package com.phonemoneyai.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateTaskRequest(
    val goal: String,
    @SerialName("app_name") val appName: String? = null,
)

@Serializable
data class TaskRecord(
    @SerialName("task_id") val taskId: String,
    val goal: String,
    @SerialName("app_name") val appName: String? = null,
    val status: String,
)

@Serializable
data class StepResultRequest(
    val success: Boolean,
    @SerialName("error_type") val errorType: String? = null,
    val message: String? = null,
    @SerialName("screenshot_path") val screenshotPath: String? = null,
)

@Serializable
data class FeedbackLog(
    @SerialName("task_id") val taskId: String,
    @SerialName("step_id") val stepId: String? = null,
    val action: String? = null,
    val result: String,
    @SerialName("screenshot_path") val screenshotPath: String? = null,
    @SerialName("error_category") val errorCategory: String? = null,
    @SerialName("ocr_summary") val ocrSummary: String? = null,
    @SerialName("ui_snapshot") val uiSnapshot: Map<String, String>? = null,
    @SerialName("ocr_snapshot") val ocrSnapshot: Map<String, String>? = null,
)

@Serializable
data class ScreenRequest(
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("ui_tree") val uiTree: List<UiNode>,
    val ocr: List<OcrNode> = emptyList(),
)

@Serializable
data class UiNode(
    val text: String? = null,
    val resourceId: String? = null,
    val bounds: List<Int>? = null,
    val clickable: Boolean = false,
    val className: String? = null,
)

@Serializable
data class OcrNode(
    val text: String,
    val x: Int,
    val y: Int,
    val confidence: Double,
)

@Serializable
data class TaskStep(
    val id: String,
    val description: String,
    val action: String,
    val target: String? = null,
    val params: Map<String, String> = emptyMap(),
)

@Serializable
data class DeviceAction(
    val action: String,
    val target: String? = null,
    val coordinates: List<Int>? = null,
    val text: String? = null,
    @SerialName("package_name") val packageName: String? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
)

@Serializable
data class DecisionState(
    val goal: String,
    @SerialName("current_step") val currentStep: TaskStep? = null,
    @SerialName("ui_tree") val uiTree: List<UiNode> = emptyList(),
    val ocr: List<OcrNode> = emptyList(),
    val history: List<String> = emptyList(),
    @SerialName("last_action") val lastAction: DeviceAction? = null,
    @SerialName("last_result") val lastResult: String? = null,
    @SerialName("screen_width") val screenWidth: Int = 1080,
    @SerialName("screen_height") val screenHeight: Int = 1920,
)

@Serializable
data class DecisionResponse(
    val action: DeviceAction,
    val reason: String,
    val confidence: Double,
    @SerialName("used_fallback") val usedFallback: Boolean,
)

@Serializable
data class NextStepResponse(
    val task: TaskPayload,
    val state: DecisionState,
)

@Serializable
data class TaskPayload(
    @SerialName("task_id") val taskId: String,
    val goal: String,
)
