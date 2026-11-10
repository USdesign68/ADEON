package com.splashcleaner.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.splashcleaner.domain.NodeSnapshot
import com.splashcleaner.domain.WindowSnapshot

class WindowTreeCollector(
    private val maxDepth: Int = 12,
    private val maxNodes: Int = 400,
    private val budgetMs: Long = 800
) {
    fun collect(root: AccessibilityNodeInfo, packageName: String, activity: String?, startedAt: Long, screenWidth: Int, screenHeight: Int): WindowSnapshot {
        val start = System.currentTimeMillis()
        val nodes = mutableListOf<NodeSnapshot>()
        fun walk(node: AccessibilityNodeInfo, parent: Int?, depth: Int) {
            if (depth > maxDepth || nodes.size >= maxNodes || System.currentTimeMillis() - start >= budgetMs) return
            if (!node.isVisibleToUser) return
            val bounds = Rect().also(node::getBoundsInScreen)
            val index = nodes.size
            nodes += NodeSnapshot(index, parent, node.className?.toString(), node.viewIdResourceName,
                node.text?.toString(), node.contentDescription?.toString(), node.isClickable, node.isEnabled,
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
        return WindowSnapshot(packageName, activity, System.currentTimeMillis(), nodes, opaque, System.currentTimeMillis() - startedAt)
    }
}
