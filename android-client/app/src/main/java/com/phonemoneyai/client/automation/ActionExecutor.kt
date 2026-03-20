package com.phonemoneyai.client.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.phonemoneyai.client.model.DeviceAction
import kotlinx.coroutines.delay

class ActionExecutor(private val service: AccessibilityService) {
    suspend fun execute(action: DeviceAction, root: AccessibilityNodeInfo?): Boolean {
        return when (action.action) {
            "tap" -> tap(action.coordinates)
            "back" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "wait" -> waitAction(action.durationMs)
            "swipe" -> swipe(action.coordinates)
            "input" -> input(root, action.text ?: action.target.orEmpty())
            "open_app" -> openApp(action.packageName ?: action.target.orEmpty())
            else -> false
        }
    }

    private fun tap(coordinates: List<Int>?): Boolean {
        if (coordinates == null || coordinates.size < 2) return false
        val path = Path().apply {
            moveTo(coordinates[0].toFloat(), coordinates[1].toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return service.dispatchGesture(gesture, null, null)
    }

    private fun swipe(coordinates: List<Int>?): Boolean {
        if (coordinates == null || coordinates.size < 4) return false
        val path = Path().apply {
            moveTo(coordinates[0].toFloat(), coordinates[1].toFloat())
            lineTo(coordinates[2].toFloat(), coordinates[3].toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 250))
            .build()
        return service.dispatchGesture(gesture, null, null)
    }

    private suspend fun waitAction(durationMs: Int?): Boolean {
        delay((durationMs ?: 1000).toLong())
        return true
    }

    private fun input(root: AccessibilityNodeInfo?, text: String): Boolean {
        if (text.isBlank()) return false
        val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (trySetText(focused, text)) return true

        val editableNode = findEditableNode(root)
        return trySetText(editableNode, text)
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (index in 0 until node.childCount) {
            val match = findEditableNode(node.getChild(index))
            if (match != null) return match
        }
        return null
    }

    private fun openApp(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val launchIntent = service.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(launchIntent)
        return true
    }

    private fun trySetText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }
}
