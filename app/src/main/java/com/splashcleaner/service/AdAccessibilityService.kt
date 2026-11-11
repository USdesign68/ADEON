package com.splashcleaner.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.splashcleaner.data.LocalDatabase
import com.splashcleaner.domain.AdRule
import com.splashcleaner.domain.RuleEngine
import com.splashcleaner.domain.SafetyGate
import com.splashcleaner.domain.weiboDiagnosticOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.MessageDigest

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
            val captureStartedAt = System.currentTimeMillis()
            // AccessibilityEvent.className is the event source view class, not a reliable Activity name.
            // Keep it unknown: activity-specific rules must not match on guessed metadata.
            val isWeibo = pkg == WEIBO_PACKAGE
            val capture = collector.collect(root, pkg, null, launchStartedAt,
                metrics.widthPixels, metrics.heightPixels, probeAdFeatures = isWeibo)
            val snapshot = capture.snapshot

            // Match only in memory. Neither node text nor selector values are persisted.
            val matchedRule = if (snapshot.opaque || activeRules.isEmpty()) null else
                activeRules.asSequence()
                    .filter { it.packageName == pkg || it.packageName == "*" }
                    .firstOrNull { ruleEngine.candidates(it, snapshot, metrics.density).isNotEmpty() }

            val ruleOutcome = when {
                snapshot.opaque -> "opaque_skipped"
                !rulePackageAvailable -> "rules_unavailable"
                activeRules.isEmpty() -> "no_rules"
                matchedRule != null -> "rule_matched"
                else -> "no_match"
            }
            val result = if (isWeibo) weiboDiagnosticOutcome(capture.featureDiagnostic, ruleOutcome) else ruleOutcome
            val diagnosticRuleId = matchedRule?.id ?: when (ruleOutcome) {
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

    companion object {
        private const val LOCAL_RULE_ASSET = "rules/stable.json5"
        private const val WEIBO_PACKAGE = "com.sina.weibo"
        private const val DIAGNOSTIC_INTERVAL_MS = 3_000L
        private const val DIAGNOSTIC_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
        @Volatile var connected: Boolean = false
            private set
    }
}

