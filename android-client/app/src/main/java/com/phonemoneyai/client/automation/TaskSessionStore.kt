package com.phonemoneyai.client.automation

import android.content.Context
import com.phonemoneyai.client.template.VideoAutomationTemplate
import com.phonemoneyai.client.template.VideoTemplateRepository
import org.json.JSONArray
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class TaskSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val templateRepository = VideoTemplateRepository()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun save(taskId: String, goal: String, appPackage: String?) {
        preferences.edit()
            .putString(KEY_TASK_ID, taskId)
            .putString(KEY_GOAL, goal)
            .putString(KEY_APP_PACKAGE, appPackage)
            .putBoolean(KEY_AUTOMATION_ENABLED, false)
            .putString(KEY_CURRENT_STEP, "暂无")
            .putString(KEY_HISTORY_ENTRY, "暂无")
            .putString(KEY_EXECUTION_META, "暂无")
            .putString(KEY_CURRENT_VIDEO_TITLE, "未识别视频")
            .putString(KEY_HISTORY_LIST, "[]")
            .putString(KEY_RUNTIME_LOG_LIST, "[]")
            .apply()
    }

    fun saveTemplates(templates: List<VideoAutomationTemplate>) {
        preferences.edit().putString(KEY_TEMPLATE_PAYLOAD, templateRepository.serialize(templates)).apply()
    }

    fun templates(): List<VideoAutomationTemplate> {
        val stored = preferences.getString(KEY_TEMPLATE_PAYLOAD, null)
        return if (stored.isNullOrBlank()) {
            val defaults = templateRepository.builtInTemplates()
            saveTemplates(defaults)
            defaults
        } else {
            runCatching { templateRepository.parse(stored) }.getOrElse {
                val defaults = templateRepository.builtInTemplates()
                saveTemplates(defaults)
                defaults
            }
        }
    }

    fun selectTemplate(template: VideoAutomationTemplate) {
        preferences.edit()
            .putString(KEY_SELECTED_TEMPLATE_ID, template.id)
            .putString(KEY_GOAL, template.goal)
            .putString(KEY_APP_PACKAGE, template.appPackage)
            .putString(KEY_CURRENT_STEP, "模板已选择：${template.name}")
            .putString(KEY_EXECUTION_META, "等待一键运行")
            .apply()
    }

    fun activateVideoAutomation(template: VideoAutomationTemplate) {
        save("video-${System.currentTimeMillis()}", template.goal, template.appPackage)
        saveTemplates(templates())
        preferences.edit()
            .putString(KEY_SELECTED_TEMPLATE_ID, template.id)
            .putBoolean(KEY_AUTOMATION_ENABLED, true)
            .putString(KEY_CURRENT_STEP, "准备刷视频")
            .putString(KEY_HISTORY_ENTRY, "run:${template.id}")
            .putString(KEY_EXECUTION_META, "一键运行已启动")
            .putString(KEY_CURRENT_VIDEO_TITLE, "等待识别视频")
            .apply()
        appendHistory("run:${template.name}")
        appendRuntimeLog("启动模板：${template.name}")
    }

    fun currentTemplate(): VideoAutomationTemplate? {
        val selectedId = preferences.getString(KEY_SELECTED_TEMPLATE_ID, null) ?: return null
        return templates().firstOrNull { it.id == selectedId }
    }

    fun updateAutomationEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTOMATION_ENABLED, enabled).apply()
        appendRuntimeLog(if (enabled) "自动刷视频已启用" else "自动刷视频已停止")
    }

    fun updateRuntimeState(
        currentStep: String? = null,
        historyEntry: String? = null,
        executionMeta: String? = null,
        currentVideoTitle: String? = null,
        runtimeLogEntry: String? = null,
    ) {
        preferences.edit().apply {
            currentStep?.let { putString(KEY_CURRENT_STEP, it) }
            historyEntry?.let { putString(KEY_HISTORY_ENTRY, it) }
            executionMeta?.let { putString(KEY_EXECUTION_META, it) }
            currentVideoTitle?.let { putString(KEY_CURRENT_VIDEO_TITLE, it) }
        }.apply()
        historyEntry?.takeIf { it.isNotBlank() }?.let(::appendHistory)
        runtimeLogEntry?.takeIf { it.isNotBlank() }?.let(::appendRuntimeLog)
    }

    fun currentTaskId(): String? = preferences.getString(KEY_TASK_ID, null)
    fun currentGoal(): String = preferences.getString(KEY_GOAL, "") ?: ""
    fun currentAppPackage(): String? = preferences.getString(KEY_APP_PACKAGE, null)
    fun selectedTemplateId(): String? = preferences.getString(KEY_SELECTED_TEMPLATE_ID, null)
    fun automationEnabled(): Boolean = preferences.getBoolean(KEY_AUTOMATION_ENABLED, false)
    fun currentStep(): String = preferences.getString(KEY_CURRENT_STEP, "暂无") ?: "暂无"
    fun historyEntry(): String = preferences.getString(KEY_HISTORY_ENTRY, "暂无") ?: "暂无"
    fun executionMeta(): String = preferences.getString(KEY_EXECUTION_META, "暂无") ?: "暂无"
    fun currentVideoTitle(): String = preferences.getString(KEY_CURRENT_VIDEO_TITLE, "未识别视频") ?: "未识别视频"

    fun historyEntries(): List<String> = readList(KEY_HISTORY_LIST)
    fun runtimeLogEntries(): List<String> = readList(KEY_RUNTIME_LOG_LIST)

    private fun appendHistory(entry: String) {
        appendToList(KEY_HISTORY_LIST, entry, 20)
    }

    private fun appendRuntimeLog(entry: String) {
        appendToList(KEY_RUNTIME_LOG_LIST, "${LocalTime.now().format(timeFormatter)} $entry", 40)
    }

    private fun appendToList(key: String, entry: String, maxItems: Int) {
        val history = JSONArray(preferences.getString(key, "[]") ?: "[]")
        history.put(0, entry)
        while (history.length() > maxItems) {
            history.remove(history.length() - 1)
        }
        preferences.edit().putString(key, history.toString()).apply()
    }

    private fun readList(key: String): List<String> {
        val history = JSONArray(preferences.getString(key, "[]") ?: "[]")
        return buildList {
            for (index in 0 until history.length()) add(history.optString(index))
        }
    }

    companion object {
        private const val PREFS_NAME = "phone_money_ai_session"
        private const val KEY_TASK_ID = "task_id"
        private const val KEY_GOAL = "goal"
        private const val KEY_APP_PACKAGE = "app_package"
        private const val KEY_AUTOMATION_ENABLED = "automation_enabled"
        private const val KEY_CURRENT_STEP = "current_step"
        private const val KEY_HISTORY_ENTRY = "history_entry"
        private const val KEY_EXECUTION_META = "execution_meta"
        private const val KEY_CURRENT_VIDEO_TITLE = "current_video_title"
        private const val KEY_HISTORY_LIST = "history_list"
        private const val KEY_RUNTIME_LOG_LIST = "runtime_log_list"
        private const val KEY_TEMPLATE_PAYLOAD = "template_payload"
        private const val KEY_SELECTED_TEMPLATE_ID = "selected_template_id"
    }
}
