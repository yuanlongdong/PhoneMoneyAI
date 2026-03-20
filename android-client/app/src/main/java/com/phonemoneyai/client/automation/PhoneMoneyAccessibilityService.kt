package com.phonemoneyai.client.automation

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.phonemoneyai.client.api.PhoneMoneyApiClient
import com.phonemoneyai.client.model.DecisionState
import com.phonemoneyai.client.model.FeedbackLog
import com.phonemoneyai.client.model.ScreenRequest
import com.phonemoneyai.client.model.StepResultRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class PhoneMoneyAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val apiClient = PhoneMoneyApiClient()
    private val actionExecutor by lazy { ActionExecutor(this) }
    private val sessionStore by lazy { TaskSessionStore(applicationContext) }
    private val screenshotOcrProcessor by lazy { ScreenshotOcrProcessor(this) }
    private val inFlight = AtomicBoolean(false)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "PhoneMoneyAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!sessionStore.automationEnabled() || !inFlight.compareAndSet(false, true)) return

        val root = rootInActiveWindow ?: run {
            inFlight.set(false)
            return
        }
        val uiNodes = AccessibilityTreeSerializer.flatten(root)
        val taskId = sessionStore.currentTaskId() ?: run {
            inFlight.set(false)
            return
        }
        val goal = sessionStore.currentGoal()

        scope.launch {
            runCatching {
                val ocrNodes = screenshotOcrProcessor.captureOcr()
                apiClient.uploadScreen(ScreenRequest(taskId = taskId, uiTree = uiNodes, ocr = ocrNodes))
                val nextStep = apiClient.getNextStep(taskId)
                val decision = apiClient.decide(
                    DecisionState(
                        goal = goal,
                        currentStep = nextStep.state.currentStep,
                        uiTree = uiNodes,
                        ocr = ocrNodes,
                    )
                )
                val success = actionExecutor.execute(decision.action, rootInActiveWindow)
                apiClient.postStepResult(
                    taskId,
                    if (success) StepResultRequest(success = true)
                    else StepResultRequest(success = false, errorType = "action_failed", message = decision.reason)
                )
                apiClient.feedback(
                    FeedbackLog(
                        taskId = taskId,
                        stepId = nextStep.state.currentStep?.id,
                        action = decision.action.action,
                        result = if (success) "success" else "fail",
                    )
                )
            }.onFailure {
                Log.e(TAG, "Automation loop failed", it)
                runCatching {
                    apiClient.postStepResult(
                        taskId,
                        StepResultRequest(success = false, errorType = "exception", message = it.message)
                    )
                }
            }
            inFlight.set(false)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "PhoneMoneyAccessibilityService interrupted")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PhoneMoneyAIService"
    }
}
