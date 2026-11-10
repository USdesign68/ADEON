package com.splashcleaner.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.splashcleaner.data.HitEntity
import com.splashcleaner.data.LocalDatabase
import com.splashcleaner.domain.AdRule
import com.splashcleaner.domain.RuleEngine
import com.splashcleaner.domain.SafetyGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Collection-only baseline. Diagnostics contain metadata only; window text and descriptions are
 * transient in memory. Automatic actions and ad-rule recognition remain disconnected.
 */
class AdAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val collector = WindowTreeCollector()
    private val diagnosticThrottle = mutableMapOf<String, Long>()
/** Local matching diagnostics only. Rule matches never trigger actions in this build. */
class AdAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val collector = WindowTreeCollector()
    private val ruleEngine = RuleEngine()
    private var activeRules: List<AdRule> = emptyList()
    private var rulePackageAvailable = false
    private var foregroundPackage: String? = null
    private var appLaunchStartedAt = 0L
    private val lastDiagnosticWriteAt = mutableMapOf<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        loadLocalRules()
        connected = true
    }

    private fun loadLocalRules() {
        val parsed = runCatching {
            assets.open(LOCAL_RULE_ASSET).bufferedReader().use { ruleEngine.parseRulePackage(it.readText()) }
        }
        activeRules = parsed.getOrElse { emptyList() }
        rulePackageAvailable = parsed.isSuccess
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

        // Keep the launch clock tied to the current foreground package, not service uptime.
        if (pkg != foregroundPackage) {
            foregroundPackage = pkg
            appLaunchStartedAt = System.currentTimeMillis()
        }
        val launchStartedAt = appLaunchStartedAt.takeIf { it > 0L } ?: return

        // Per-app opt-in and protected-package checks happen before reading the window tree.
        if (!getSharedPreferences("app_cfg", MODE_PRIVATE).getBoolean(pkg, false)) return
        if (SafetyGate.isProtectedPackage(pkg)) return

        val root = rootInActiveWindow
        if (root == null) {
            recordDiagnostic(pkg, "no_active_window")
            return
        }

        try {
            if (root.packageName?.toString() != pkg) {
                recordDiagnostic(pkg, "package_mismatch")
                return
            }
            val metrics = resources.displayMetrics
            val startedAt = System.currentTimeMillis()
            val snapshot = collector.collect(
                root, pkg, event.className?.toString(), launchStartedAt,
                metrics.widthPixels, metrics.heightPixels
            )
            val collectionCostMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)

            // Persist metadata only. The result explicitly says collection-only, not ad recognition.
            val result = if (snapshot.opaque) {
                "opaque_skipped"
            } else {
                "collected_only;nodes=${snapshot.nodes.size}"
            }
            recordDiagnostic(pkg, result, collectionCostMs)
            val captureStartedAt = System.currentTimeMillis()
            // AccessibilityEvent.className is the event source view class, not a reliable Activity name.
            // Keep it unknown: activity-specific rules must not match on guessed metadata.
            val snapshot = collector.collect(root, pkg, null, launchStartedAt,
                metrics.widthPixels, metrics.heightPixels)

            // Match only in memory. Neither node text nor selector values are persisted.
            val matchedRule = if (snapshot.opaque || activeRules.isEmpty()) null else
                activeRules.asSequence()
                    .filter { it.packageName == pkg || it.packageName == "*" }
                    .firstOrNull { ruleEngine.candidates(it, snapshot, metrics.density).isNotEmpty() }

            val result = when {
                snapshot.opaque -> "opaque_skipped"
                !rulePackageAvailable -> "rules_unavailable"
                activeRules.isEmpty() -> "no_rules"
                matchedRule != null -> "rule_matched"
                else -> "no_match"
            }
            val diagnosticRuleId = matchedRule?.id ?: when (result) {
                "opaque_skipped" -> "_opaque"
                "rules_unavailable" -> "_rules_unavailable"
                "no_rules" -> "_no_rules"
                else -> "_no_match"
            }

            // Persist metadata only: package hash, rule ID, outcome, cost and timestamp.
            // Throttle diagnostics per package so content-change events cannot flood local storage.
            val now = System.currentTimeMillis()
            if (now - (lastDiagnosticWriteAt[pkg] ?: 0L) >= DIAGNOSTIC_INTERVAL_MS) {
                lastDiagnosticWriteAt[pkg] = now
                val diagnosticCostMs = (System.currentTimeMillis() - captureStartedAt).coerceAtLeast(0L)
                serviceScope.launch {
                    runCatching {
                        val dao = LocalDatabase.get(this@AdAccessibilityService).hitDao()
                        dao.insert(com.splashcleaner.data.HitEntity(
                            packageHash = sha256(pkg), ruleId = diagnosticRuleId, result = result,
                            costMs = diagnosticCostMs, occurredAt = now
                        ))
                        dao.deleteBefore(now - DIAGNOSTIC_RETENTION_MS)
                    }
                }
            }
            // A match is diagnostic evidence only. No click, back, wait or other action is executed.
        } catch (_: Throwable) {
            // Keep the accessibility callback resilient and avoid logging exception details.
            recordDiagnostic(pkg, "collection_error")
        } finally {
            runCatching { root.recycle() }
        }
    }

    private fun recordDiagnostic(packageName: String, result: String, costMs: Long = 0L) {
        val now = System.currentTimeMillis()
        val throttleKey = "$packageName|$result"
        val shouldRecord = synchronized(diagnosticThrottle) {
            val last = diagnosticThrottle[throttleKey] ?: 0L
            if (now - last < DIAGNOSTIC_THROTTLE_MS) {
                false
            } else {
                diagnosticThrottle[throttleKey] = now
                true
            }
        }
        if (!shouldRecord) return

        serviceScope.launch {
            runCatching {
                val dao = LocalDatabase.get(this@AdAccessibilityService).hitDao()
                dao.insert(
                    HitEntity(
                        packageHash = sha256(packageName),
                        ruleId = DIAGNOSTIC_RULE_ID,
                        result = result,
                        costMs = costMs.coerceAtLeast(0L),
                        occurredAt = now
                    )
                )
                dao.trimDiagnostics()
            }
        }
    }

    override fun onDestroy() {
        connected = false
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }

    companion object {
        private const val DIAGNOSTIC_RULE_ID = "_diagnostic"
        private const val DIAGNOSTIC_THROTTLE_MS = 1_500L
    companion object {
        private const val LOCAL_RULE_ASSET = "rules/stable.json5"
        private const val DIAGNOSTIC_INTERVAL_MS = 3_000L
        private const val DIAGNOSTIC_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
        @Volatile var connected: Boolean = false
            private set
    }
}
