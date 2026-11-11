package com.splashcleaner.domain

enum class AccessState { NOT_ENABLED, RESTRICTED, AVAILABLE, RECYCLED }
enum class Scene { splash, interstitial, dialog, jumpGuard }
enum class HitResult { positive, negative, skipped, error }
enum class RuleState { active, deprecated, removed }
enum class CheckStatus { PASS, ISSUE, NOT_VERIFIED }
enum class Severity(val penalty: Int) { SERIOUS(15), HIGH(6), MEDIUM(2) }

/** Ephemeral accessibility text-read summary. Contains no node text or identifiers. */
data class AccessibilityFeatureDiagnostic(
    val attemptedNodes: Int,
    val successfulReads: Int,
    val failedReads: Int,
    val adLabelFound: Boolean,
    val skipLabelFound: Boolean
) {
    fun result(): String = when {
        attemptedNodes == 0 || successfulReads == 0 -> "readability_indeterminate"
        adLabelFound && skipLabelFound -> "readable_ad_and_skip"
        adLabelFound -> "readable_ad_label"
        skipLabelFound -> "readable_skip_label"
        else -> "readable_features_absent"
    }
}

/** Store one coarse state for Weibo feature access and local rule evaluation. */
fun weiboDiagnosticOutcome(features: AccessibilityFeatureDiagnostic, ruleOutcome: String): String =
    "weibo_${features.result()}_$ruleOutcome"

data class NodeSnapshot(
    val index: Int,
    val parentIndex: Int?,
    val className: String?,
    val resourceId: String?,
    val text: String?, // memory only; never persist or log
    val contentDescription: String?, // memory only; never persist or log
    val clickable: Boolean,
    val enabled: Boolean,
    val visible: Boolean,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val depth: Int
) {
    val areaPx: Long get() = ((right - left).coerceAtLeast(0) * (bottom - top).coerceAtLeast(0)).toLong()
}

data class WindowSnapshot(
    val packageName: String,
    val activityName: String?,
    val capturedAtMs: Long,
    val nodes: List<NodeSnapshot>,
    val opaque: Boolean,
    /** Elapsed time since this target app entered the foreground, not since the service connected. */
    val elapsedSinceAppLaunchMs: Long
)

data class RuleSelector(
    val textRegex: Regex? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val description: String? = null,
    val minDepth: Int? = null,
    val maxDepth: Int? = null,
    val maxAreaDp2: Int? = null,
    val within: String? = null
)

data class AdRule(
    val id: String,
    val packageName: String,
    val name: String,
    val revision: Int,
    val versionConstraint: String,
    val scene: Scene,
    val activities: List<String>,
    val elapsedMaxMs: Long = 6_000,
    val stayMs: Long = 120,
    val firstLaunchOnly: Boolean = true,
    val any: List<RuleSelector>,
    val all: List<RuleSelector> = emptyList(),
    val action: String = "none",
    val target: String = "self",
    val waitMs: Long = 0,
    val verify: String = "windowChanged",
    val cooldownMs: Long = 1_500,
    val maxPerSession: Int = 1,
    val maxPerDay: Int = 24,
    val requireForeground: Boolean = true,
    val state: RuleState = RuleState.active
)

data class CheckDefinition(
    val id: String,
    val title: String,
    val severity: Severity,
    val method: String,
    val criterion: String,
    val evidence: String,
    val status: CheckStatus = CheckStatus.NOT_VERIFIED,
    val note: String = ""
)

data class InspectionScore(val score: Int, val grade: String, val serious: Int, val high: Int, val medium: Int) {
    companion object {
        fun calculate(checks: List<CheckDefinition>, coverage: Double): InspectionScore {
            val issues = checks.filter { it.status == CheckStatus.ISSUE }
            val s = issues.count { it.severity == Severity.SERIOUS }
            val h = issues.count { it.severity == Severity.HIGH }
            val m = issues.count { it.severity == Severity.MEDIUM }
            val score = (100 - 15 * s - 6 * h - 2 * m).coerceAtLeast(0)
            val grade = when {
                s >= 1 -> "D"
                h >= 2 -> "C"
                h == 1 || m >= 4 -> "B"
                else -> "A"
            } + if (coverage < .80) "（覆盖不足）" else ""
            return InspectionScore(score, grade, s, h, m)
        }
    }
}

