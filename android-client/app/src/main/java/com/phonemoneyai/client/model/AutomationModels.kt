package com.phonemoneyai.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class AutomationTaskConfig(
    val name: String = "内容流浏览测试",
    @SerialName("app_package") val appPackage: String,
    @SerialName("loop_count") val loopCount: Int = 1,
    @SerialName("random_seed") val randomSeed: Long? = null,
    val steps: List<AutomationStep> = emptyList(),
)

@Serializable
data class AutomationStep(
    val action: String,
    val label: String? = null,
    @SerialName("duration_min_ms") val durationMinMs: Long? = null,
    @SerialName("duration_max_ms") val durationMaxMs: Long? = null,
    val direction: String? = null,
    @SerialName("start_ratio") val startRatio: SwipePoint? = null,
    @SerialName("end_ratio") val endRatio: SwipePoint? = null,
)

@Serializable
data class SwipePoint(
    val x: Float,
    val y: Float,
) {
    fun toPixels(width: Int, height: Int): List<Int> = listOf(
        (x.coerceIn(0f, 1f) * width).roundToInt(),
        (y.coerceIn(0f, 1f) * height).roundToInt(),
    )
}

@Serializable
data class AutomationLogEntry(
    val timestamp: Long,
    val level: String,
    val message: String,
)
