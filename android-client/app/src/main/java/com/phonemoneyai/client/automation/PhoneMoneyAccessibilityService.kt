package com.phonemoneyai.client.automation

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.phonemoneyai.client.api.PhoneMoneyApiClient
import com.phonemoneyai.client.model.DecisionState
import com.phonemoneyai.client.model.ScreenRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PhoneMoneyAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val apiClient = PhoneMoneyApiClient()
    private val actionExecutor by lazy { ActionExecutor(this) }

    var activeTaskId: String? = null
    var activeGoal: String = "收款码"

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "PhoneMoneyAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val uiNodes = AccessibilityTreeSerializer.flatten(root)
        val taskId = activeTaskId ?: return

        scope.launch {
            runCatching {
                apiClient.uploadScreen(ScreenRequest(taskId = taskId, uiTree = uiNodes))
                val nextStep = apiClient.getNextStep(taskId)
                val decision = apiClient.decide(
                    DecisionState(
                        goal = activeGoal,
                        currentStep = nextStep.state.currentStep,
                        uiTree = uiNodes,
                    )
                )
                actionExecutor.execute(decision.action)
            }.onFailure {
                Log.e(TAG, "Automation loop failed", it)
            }
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
