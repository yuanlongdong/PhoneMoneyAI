package com.phonemoneyai.client.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.phonemoneyai.client.model.DeviceAction

class ActionExecutor(private val service: AccessibilityService) {
    fun execute(action: DeviceAction) {
        when (action.action) {
            "tap" -> tap(action.coordinates)
            "back" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "open_app", "wait", "input", "swipe" -> {
                // These are intentionally left as extension points for the next iteration.
            }
        }
    }

    private fun tap(coordinates: List<Int>?) {
        if (coordinates == null || coordinates.size < 2) return
        val path = Path().apply {
            moveTo(coordinates[0].toFloat(), coordinates[1].toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        service.dispatchGesture(gesture, null, null)
    }

    fun trySetText(node: AccessibilityNodeInfo?, text: String) {
        if (node == null) return
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }
}
