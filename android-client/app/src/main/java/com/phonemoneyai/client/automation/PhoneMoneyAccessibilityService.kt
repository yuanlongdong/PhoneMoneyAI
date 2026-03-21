package com.phonemoneyai.client.automation

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.phonemoneyai.client.model.AutomationTaskConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class PhoneMoneyAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val actionExecutor by lazy { ActionExecutor(this) }
    private val sessionStore by lazy { TaskSessionStore(applicationContext) }
    private var runnerJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "PhoneMoneyAccessibilityService connected")
        ensureRunner()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            ensureRunner()
        }
    }

    private fun ensureRunner() {
        if (!sessionStore.automationEnabled()) {
            runnerJob?.cancel()
            runnerJob = null
            return
        }
        if (runnerJob?.isActive == true) return
        runnerJob = scope.launch {
            runAutomationLoop()
        }
    }

    private suspend fun runAutomationLoop() {
        val config = sessionStore.parseLocalConfig().getOrElse {
            sessionStore.appendLog("ERROR", "任务配置解析失败: ${it.message}")
            sessionStore.updateRuntimeState(
                currentStep = "配置错误",
                historyEntry = "config-error",
                executionMeta = it.message,
            )
            sessionStore.updateAutomationEnabled(false)
            AutomationForegroundService.stop(applicationContext)
            return
        }
        val seed = config.randomSeed ?: System.currentTimeMillis()
        val random = Random(seed)
        sessionStore.startRun(config.loopCount)
        sessionStore.appendLog("INFO", "开始执行 ${config.name}, loop=${config.loopCount}, seed=$seed")
        AutomationForegroundService.update(applicationContext, "运行中：${config.name}")

        repeat(config.loopCount) { loopIndex ->
            if (!scope.isActive || !sessionStore.automationEnabled()) return
            sessionStore.markLoopCompleted(loopIndex)
            executeLoop(config, loopIndex + 1, random)
        }

        sessionStore.updateAutomationEnabled(false)
        sessionStore.updateRuntimeState(
            currentStep = "已完成",
            historyEntry = "all-loops-finished",
            executionMeta = "循环 ${config.loopCount} 次已执行完成",
        )
        sessionStore.appendLog("INFO", "任务执行完成")
        AutomationForegroundService.update(applicationContext, "已完成：${config.name}")
        delay(600)
        AutomationForegroundService.stop(applicationContext)
    }

    private suspend fun executeLoop(config: AutomationTaskConfig, loopNumber: Int, random: Random) {
        sessionStore.appendLog("INFO", "开始第 $loopNumber/${config.loopCount} 轮")
        sessionStore.updateRuntimeState(
            currentStep = "循环 $loopNumber/${config.loopCount}",
            historyEntry = "loop-$loopNumber-start",
            executionMeta = "准备执行 ${config.steps.size} 个步骤",
        )
        for ((stepIndex, step) in config.steps.withIndex()) {
            if (!sessionStore.automationEnabled()) return
            val stepName = step.label ?: step.action
            sessionStore.updateRuntimeState(
                currentStep = stepName,
                historyEntry = "loop-$loopNumber-step-${stepIndex + 1}:${step.action}",
                executionMeta = "执行中 ${stepIndex + 1}/${config.steps.size}",
            )
            AutomationForegroundService.update(applicationContext, "第 $loopNumber 轮：$stepName")
            val success = runCatching {
                actionExecutor.executeLocalStep(step, config.appPackage, random)
            }.getOrElse {
                sessionStore.appendLog("ERROR", "$stepName 执行异常: ${it.message}")
                false
            }
            if (!success) {
                sessionStore.appendLog("WARN", "$stepName 执行失败")
                sessionStore.updateRuntimeState(
                    currentStep = stepName,
                    historyEntry = "loop-$loopNumber-step-${stepIndex + 1}:failed",
                    executionMeta = "执行失败，已继续后续步骤",
                )
                delay(500)
                continue
            }
            sessionStore.appendLog("INFO", "$stepName 执行成功")
            delay(350)
        }
        sessionStore.markLoopCompleted(loopNumber)
        sessionStore.appendLog("INFO", "完成第 $loopNumber/${config.loopCount} 轮")
    }

    override fun onInterrupt() {
        Log.w(TAG, "PhoneMoneyAccessibilityService interrupted")
    }

    override fun onDestroy() {
        runnerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PhoneMoneyAIService"
    }
}
