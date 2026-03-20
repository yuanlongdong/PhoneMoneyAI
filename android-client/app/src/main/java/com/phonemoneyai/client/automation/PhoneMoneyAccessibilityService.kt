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

    @Volatile
    private var lastInsightSignature: String? = null

    @Volatile
    private var lastInsightAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "PhoneMoneyAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val template = sessionStore.currentTemplate() ?: return
        if (!sessionStore.automationEnabled()) return
        val root = rootInActiveWindow ?: return
        val packageName = event?.packageName?.toString() ?: root.packageName?.toString() ?: return
        if (packageName != template.appPackage) return
        if (!inFlight.compareAndSet(false, true)) return

        try {
            val now = System.currentTimeMillis()
            val insight = ScreenContentAnalyzer.analyze(root)
            if (insight.signature == lastInsightSignature && now - lastInsightAt < DUPLICATE_EVENT_COOLDOWN_MS) {
                return
            }
            lastInsightSignature = insight.signature
            lastInsightAt = now

            when {
                insight.isLive -> skipSpecialContent(template, insight, "直播间", now)
                insight.isAd -> skipSpecialContent(template, insight, "广告", now)
                else -> handleRegularVideo(template, insight, now)
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Auto swipe failed", exception)
            sessionStore.updateRuntimeState(
                currentStep = "异常中断",
                historyEntry = "exception:${template.id}",
                executionMeta = "错误: ${exception.message}",
                runtimeLogEntry = "异常中断：${exception.message ?: "未知错误"}",
            )
        } finally {
            inFlight.set(false)
        }
    }

    private fun handleRegularVideo(template: com.phonemoneyai.client.template.VideoAutomationTemplate, insight: ScreenInsight, now: Long) {
        sessionStore.updateRuntimeState(
            currentStep = "正在观看：${insight.title}",
            executionMeta = "模板=${template.name}; 间隔=${template.swipeIntervalMs}ms; 最近视频=${insight.title}",
            currentVideoTitle = insight.title,
            runtimeLogEntry = "识别视频：${insight.title}",
        )

        if (now - lastSwipeAt < template.swipeIntervalMs) return

        val success = actionExecutor.executeSwipe(template.swipeStart, template.swipeEnd)
        lastSwipeAt = now
        sessionStore.updateRuntimeState(
            currentStep = if (success) "已滑到下一条视频" else "滑动失败，等待重试",
            historyEntry = if (success) "swipe:${template.id}" else "swipe-failed:${template.id}",
            executionMeta = "模板=${template.name}; 视频=${insight.title}; 间隔=${template.swipeIntervalMs}ms",
            currentVideoTitle = insight.title,
            runtimeLogEntry = if (success) "观看后滑走：${insight.title}" else "滑动失败：${insight.title}",
        )
    }

    private fun skipSpecialContent(
        template: com.phonemoneyai.client.template.VideoAutomationTemplate,
        insight: ScreenInsight,
        reason: String,
        now: Long,
    ) {
        if (now - lastSwipeAt < SPECIAL_SKIP_COOLDOWN_MS) return
        val success = actionExecutor.executeSwipe(template.swipeStart, template.swipeEnd)
        lastSwipeAt = now
        val keywords = insight.matchedKeywords.joinToString("/").ifBlank { reason }
        sessionStore.updateRuntimeState(
            currentStep = if (success) "识别到$reason，已自动跳过" else "识别到$reason，但滑动失败",
            historyEntry = if (success) "skip-$reason:${template.id}" else "skip-$reason-failed:${template.id}",
            executionMeta = "模板=${template.name}; 内容=${insight.title}; 命中=$keywords",
            currentVideoTitle = insight.title,
            runtimeLogEntry = if (success) "跳过$reason：${insight.title}" else "跳过$reason失败：${insight.title}",
        )
    }

    override fun onInterrupt() {
        Log.w(TAG, "PhoneMoneyAccessibilityService interrupted")
    }

    companion object {
        private const val TAG = "PhoneMoneyAIService"
        private const val DUPLICATE_EVENT_COOLDOWN_MS = 1200L
        private const val SPECIAL_SKIP_COOLDOWN_MS = 900L
    }
}
