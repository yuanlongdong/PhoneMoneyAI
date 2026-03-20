package com.phonemoneyai.client.template

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class VideoAutomationTemplate(
    val id: String,
    val name: String,
    val goal: String,
    @SerialName("app_package") val appPackage: String,
    @SerialName("swipe_start") val swipeStart: List<Int> = listOf(540, 1600),
    @SerialName("swipe_end") val swipeEnd: List<Int> = listOf(540, 500),
    @SerialName("swipe_interval_ms") val swipeIntervalMs: Long = 2500,
    @SerialName("launch_on_start") val launchOnStart: Boolean = true,
)

@Serializable
private data class VideoTemplatePayload(
    val templates: List<VideoAutomationTemplate> = emptyList(),
)

class VideoTemplateRepository(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
) {
    fun builtInTemplates(): List<VideoAutomationTemplate> = listOf(
        VideoAutomationTemplate(
            id = "douyin-default",
            name = "抖音默认刷视频",
            goal = "自动上滑切换抖音短视频",
            appPackage = "com.ss.android.ugc.aweme",
        ),
        VideoAutomationTemplate(
            id = "kuaishou-default",
            name = "快手默认刷视频",
            goal = "自动上滑切换快手短视频",
            appPackage = "com.smile.gifmaker",
        ),
        VideoAutomationTemplate(
            id = "wechat-channels",
            name = "视频号默认刷视频",
            goal = "自动上滑切换视频号内容",
            appPackage = "com.tencent.mm",
        ),
    )

    fun parse(raw: String): List<VideoAutomationTemplate> {
        val payload = json.decodeFromString(VideoTemplatePayload.serializer(), raw)
        require(payload.templates.isNotEmpty()) { "模板为空" }
        return payload.templates
    }

    fun serialize(templates: List<VideoAutomationTemplate>): String =
        json.encodeToString(VideoTemplatePayload.serializer(), VideoTemplatePayload(templates))

    fun download(url: String): List<VideoAutomationTemplate> {
        require(url.isNotBlank()) { "请输入模板地址" }
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "模板下载失败: ${response.code}" }
            return parse(response.body?.string().orEmpty())
        }
    }
}
