package com.phonemoneyai.client.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import com.phonemoneyai.client.model.AutomationStep
import com.phonemoneyai.client.model.DeviceAction
import kotlinx.coroutines.delay
import kotlin.random.Random

class ActionExecutor(private val service: AccessibilityService) {
    suspend fun execute(action: DeviceAction, root: AccessibilityNodeInfo?): Boolean {
        return when (action.action) {
            "tap" -> tap(action.coordinates)
            "back" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "wait" -> waitAction(action.durationMs)
            "swipe" -> swipe(action.coordinates, 250)
            "input" -> input(root, action.text ?: action.target.orEmpty())
            "open_app" -> openApp(action.packageName ?: action.target.orEmpty())
            else -> false
        }
    }

    suspend fun executeLocalStep(step: AutomationStep, packageName: String, random: Random): Boolean {
        return when (step.action) {
            "open_app" -> openApp(packageName)
            "wait" -> waitAction(randomDuration(step, random, 800L, 1600L).toInt())
            "swipe" -> {
                val coords = randomizedSwipe(step, random)
                swipe(coords, randomDuration(step, random, 260L, 420L))
            }
            "back" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
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

    private fun swipe(coordinates: List<Int>?, durationMs: Long): Boolean {
        if (coordinates == null || coordinates.size < 4) return false
        val path = Path().apply {
            moveTo(coordinates[0].toFloat(), coordinates[1].toFloat())
            lineTo(coordinates[2].toFloat(), coordinates[3].toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
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

    private fun randomDuration(step: AutomationStep, random: Random, defaultMin: Long, defaultMax: Long): Long {
        val min = step.durationMinMs ?: defaultMin
        val max = step.durationMaxMs ?: defaultMax
        return if (max <= min) min else random.nextLong(min, max + 1)
    }

    private fun randomizedSwipe(step: AutomationStep, random: Random): List<Int> {
        val metrics = displayMetrics()
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val defaultStart = when (step.direction) {
            "down" -> listOf((width * 0.5f).toInt(), (height * 0.25f).toInt())
            else -> listOf((width * 0.5f).toInt(), (height * 0.82f).toInt())
        }
        val defaultEnd = when (step.direction) {
            "down" -> listOf((width * 0.52f).toInt(), (height * 0.78f).toInt())
            else -> listOf((width * 0.48f).toInt(), (height * 0.24f).toInt())
        }
        val start = step.startRatio?.toPixels(width, height) ?: defaultStart
        val end = step.endRatio?.toPixels(width, height) ?: defaultEnd
        return listOf(
            jitter(start[0], width, random),
            jitter(start[1], height, random),
            jitter(end[0], width, random),
            jitter(end[1], height, random),
        )
    }

    private fun jitter(value: Int, max: Int, random: Random): Int {
        val delta = (max * 0.03).toInt().coerceAtLeast(6)
        return (value + random.nextInt(-delta, delta + 1)).coerceIn(1, max - 1)
    }

    private fun displayMetrics(): DisplayMetrics {
        val windowManager = service.getSystemService(WindowManager::class.java)
        return DisplayMetrics().also { metrics ->
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getRealMetrics(metrics)
        }
    }
}
