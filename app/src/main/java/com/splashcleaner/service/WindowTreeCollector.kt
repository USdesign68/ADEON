package com.splashcleaner.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.splashcleaner.domain.NodeSnapshot
import com.splashcleaner.domain.WindowSnapshot
import com.splashcleaner.domain.AccessibilityFeatureDiagnostic

class WindowTreeCollector(
    private val maxDepth: Int = 12,
    private val maxNodes: Int = 400,
    private val budgetMs: Long = 800
) {
    data class Capture(val snapshot: WindowSnapshot, val featureDiagnostic: AccessibilityFeatureDiagnostic)

    fun collect(root: AccessibilityNodeInfo, packageName: String, activity: String?, startedAt: Long, screenWidth: Int, screenHeight: Int, probeAdFeatures: Boolean = false): Capture {
        val start = System.currentTimeMillis()
        val nodes = mutableListOf<NodeSnapshot>()
        var successfulTextReads = 0
        var failedTextReads = 0
        var adLabelFound = false
        var skipLabelFound = false
        fun walk(node: AccessibilityNodeInfo, parent: Int?, depth: Int) {
            if (depth > maxDepth || nodes.size >= maxNodes || System.currentTimeMillis() - start >= budgetMs) return
            if (!node.isVisibleToUser) return
            val bounds = Rect().also(node::getBoundsInScreen)
            val index = nodes.size
            // Read text/description only into locals; summarize immediately and never put their
            // values in the persistent diagnostic record.
            val textRead = if (probeAdFeatures) runCatching { node.text?.toString() } else Result.success(null)
            val descriptionRead = if (probeAdFeatures) runCatching { node.contentDescription?.toString() } else Result.success(null)
            if (probeAdFeatures) {
                if (textRead.isSuccess && descriptionRead.isSuccess) successfulTextReads++ else failedTextReads++
            }
            val text = textRead.getOrNull()
            val description = descriptionRead.getOrNull()
            adLabelFound = adLabelFound || text.orEmpty().contains("广告", ignoreCase = true) ||
                description.orEmpty().contains("广告", ignoreCase = true)
            skipLabelFound = skipLabelFound || text.orEmpty().contains("跳过", ignoreCase = true) ||
                description.orEmpty().contains("跳过", ignoreCase = true)
            nodes += NodeSnapshot(index, parent, node.className?.toString(), node.viewIdResourceName,
                text, description, node.isClickable, node.isEnabled,
                node.isVisibleToUser, bounds.left, bounds.top, bounds.right, bounds.bottom, depth)
            for (i in 0 until node.childCount) {
                if (nodes.size >= maxNodes || System.currentTimeMillis() - start >= budgetMs) break
                val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                try { walk(child, index, depth + 1) } finally { runCatching { child.recycle() } }
            }
        }
        walk(root, null, 0)
        val screenArea = (screenWidth.toLong() * screenHeight.toLong()).coerceAtLeast(1)
        val visibleArea = nodes.sumOf { it.areaPx }
        val opaque = nodes.size <= 3 && visibleArea < screenArea * .30
        return Capture(
            WindowSnapshot(packageName, activity, System.currentTimeMillis(), nodes, opaque, System.currentTimeMillis() - startedAt),
            AccessibilityFeatureDiagnostic(
                attemptedNodes = if (probeAdFeatures) nodes.size else 0,
                successfulReads = successfulTextReads,
                failedReads = failedTextReads,
                adLabelFound = adLabelFound,
                skipLabelFound = skipLabelFound
            )
        )
    }
}

