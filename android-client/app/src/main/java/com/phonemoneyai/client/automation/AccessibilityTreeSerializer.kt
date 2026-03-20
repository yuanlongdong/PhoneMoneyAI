package com.phonemoneyai.client.automation

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.phonemoneyai.client.model.UiNode

object AccessibilityTreeSerializer {
    fun flatten(root: AccessibilityNodeInfo?): List<UiNode> {
        if (root == null) return emptyList()
        val results = mutableListOf<UiNode>()
        traverse(root, results)
        return results
    }

    private fun traverse(node: AccessibilityNodeInfo, results: MutableList<UiNode>) {
        results += UiNode(
            text = node.text?.toString(),
            resourceId = node.viewIdResourceName,
            bounds = node.boundsAsList(),
            clickable = node.isClickable,
            className = node.className?.toString(),
        )

        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                traverse(child, results)
                child.recycle()
            }
        }
    }

    private fun AccessibilityNodeInfo.boundsAsList(): List<Int> {
        val rect = Rect()
        getBoundsInScreen(rect)
        return listOf(rect.left, rect.top, rect.right, rect.bottom)
    }
}
