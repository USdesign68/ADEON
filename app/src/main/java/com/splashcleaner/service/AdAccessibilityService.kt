package com.splashcleaner.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.splashcleaner.data.LocalDatabase
import com.splashcleaner.domain.SafetyGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.MessageDigest

/** Collection-only baseline. Automatic actions remain disabled until a reviewed, signed rule set is installed. */
class AdAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val collector = WindowTreeCollector()
    private var foregroundPackage: String? = null
    private var appLaunchStartedAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        connected = false
        foregroundPackage = null
        appLaunchStartedAt = 0L
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Start the rule clock when a different app enters the foreground. Service uptime is not
        // a valid proxy: the accessibility service can remain connected for hours before launch.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg != foregroundPackage) {
                foregroundPackage = pkg
                appLaunchStartedAt = System.currentTimeMillis()
            }
        } else if (foregroundPackage == null) {
            foregroundPackage = pkg
            appLaunchStartedAt = System.currentTimeMillis()
        }
        val launchStartedAt = appLaunchStartedAt.takeIf { it > 0L } ?: return

        // Per-app opt-in is checked before reading the target window tree.
        if (!getSharedPreferences("app_cfg", MODE_PRIVATE).getBoolean(pkg, false)) return
        if (SafetyGate.isProtectedPackage(pkg)) return
        val root = rootInActiveWindow ?: return
        try {
            if (root.packageName?.toString() != pkg) return
            val metrics = resources.displayMetrics
            val snapshot = collector.collect(root, pkg, event.className?.toString(), launchStartedAt,
                metrics.widthPixels, metrics.heightPixels)
            // Snapshot text is transient. Do not log, persist or transmit it.
            if (snapshot.opaque) {
                serviceScope.launch {
                    runCatching {
                        val hash = sha256(pkg)
                        LocalDatabase.get(this@AdAccessibilityService).hitDao().insert(
                            com.splashcleaner.data.HitEntity(packageHash = hash, ruleId = "_opaque", result = "skipped", costMs = 0, occurredAt = System.currentTimeMillis())
                        )
                    }
                }
            }
            // Recognition and actions are intentionally not connected in this safety-baseline change.
        } catch (_: Throwable) {
            // Accessibility callbacks must never crash the service.
        } finally {
            runCatching { root.recycle() }
        }
    }

    override fun onDestroy() {
        connected = false
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).take(12).joinToString("") { "%02x".format(it) }

    companion object { @Volatile var connected: Boolean = false; private set }
}
