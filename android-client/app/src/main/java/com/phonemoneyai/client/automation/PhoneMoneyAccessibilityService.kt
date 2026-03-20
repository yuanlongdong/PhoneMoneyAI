package com.phonemoneyai.client.automation

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicBoolean

class PhoneMoneyAccessibilityService : AccessibilityService() {
    private val actionExecutor by lazy { ActionExecutor(this) }
    private val sessionStore by lazy { TaskSessionStore(applicationContext) }
    private val inFlight = AtomicBoolean(false)
    @Volatile
    private var lastSwipeAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "PhoneMoneyAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val template = sessionStore.currentTemplate() ?: return
        if (!sessionStore.automationEnabled()) return
        val packageName = event?.packageName?.toString() ?: rootInActiveWindow?.packageName?.toString() ?: return
        if (packageName != template.appPackage) return
        val now = System.currentTimeMillis()
        if (now - lastSwipeAt < template.swipeIntervalMs) return
        if (!inFlight.compareAndSet(false, true)) return

        try {
            val success = actionExecutor.executeSwipe(template.swipeStart, template.swipeEnd)
            lastSwipeAt = now
            sessionStore.updateRuntimeState(
                currentStep = if (success) "正在自动上滑刷视频" else "滑动失败，等待重试",
                historyEntry = if (success) "swipe:${template.id}" else "swipe-failed:${template.id}",
                executionMeta = "模板=${template.name}; 间隔=${template.swipeIntervalMs}ms; 包名=${template.appPackage}",
            )
        } catch (exception: Exception) {
            Log.e(TAG, "Auto swipe failed", exception)
            sessionStore.updateRuntimeState(
                currentStep = "异常中断",
                historyEntry = "exception:${template.id}",
                executionMeta = "错误: ${exception.message}",
            )
        } finally {
            inFlight.set(false)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "PhoneMoneyAccessibilityService interrupted")
    }

    companion object {
        private const val TAG = "PhoneMoneyAIService"
    }
}
