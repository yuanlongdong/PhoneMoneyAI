package com.phonemoneyai.client.automation

import android.content.Context
import com.phonemoneyai.client.template.VideoAutomationTemplate
import com.phonemoneyai.client.template.VideoTemplateRepository
import org.json.JSONArray

class TaskSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val templateRepository = VideoTemplateRepository()

    fun save(taskId: String, goal: String, appPackage: String?) {
        preferences.edit()
            .putString(KEY_TASK_ID, taskId)
            .putString(KEY_GOAL, goal)
            .putString(KEY_APP_PACKAGE, appPackage)
            .putBoolean(KEY_AUTOMATION_ENABLED, false)
            .putString(KEY_CURRENT_STEP, "暂无")
            .putString(KEY_HISTORY_ENTRY, "暂无")
            .putString(KEY_EXECUTION_META, "暂无")
            .putString(KEY_HISTORY_LIST, "[]")
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
            .apply()
        appendHistory("run:${template.name}")
    }

    fun currentTemplate(): VideoAutomationTemplate? {
        val selectedId = preferences.getString(KEY_SELECTED_TEMPLATE_ID, null) ?: return null
        return templates().firstOrNull { it.id == selectedId }
    }

    fun updateAutomationEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTOMATION_ENABLED, enabled).apply()
    }

    fun updateRuntimeState(currentStep: String?, historyEntry: String?, executionMeta: String?) {
        preferences.edit()
            .putString(KEY_CURRENT_STEP, currentStep)
            .putString(KEY_HISTORY_ENTRY, historyEntry)
            .putString(KEY_EXECUTION_META, executionMeta)
            .apply()
        historyEntry?.takeIf { it.isNotBlank() }?.let { appendHistory(it) }
    }

    private fun appendHistory(entry: String) {
        val history = JSONArray(preferences.getString(KEY_HISTORY_LIST, "[]") ?: "[]")
        history.put(0, entry)
        while (history.length() > 20) {
            history.remove(history.length() - 1)
        }
        preferences.edit().putString(KEY_HISTORY_LIST, history.toString()).apply()
    }

    fun currentTaskId(): String? = preferences.getString(KEY_TASK_ID, null)
    fun currentGoal(): String = preferences.getString(KEY_GOAL, "") ?: ""
    fun currentAppPackage(): String? = preferences.getString(KEY_APP_PACKAGE, null)
    fun selectedTemplateId(): String? = preferences.getString(KEY_SELECTED_TEMPLATE_ID, null)
    fun automationEnabled(): Boolean = preferences.getBoolean(KEY_AUTOMATION_ENABLED, false)
    fun currentStep(): String = preferences.getString(KEY_CURRENT_STEP, "暂无") ?: "暂无"
    fun historyEntry(): String = preferences.getString(KEY_HISTORY_ENTRY, "暂无") ?: "暂无"
    fun executionMeta(): String = preferences.getString(KEY_EXECUTION_META, "暂无") ?: "暂无"
    fun historyEntries(): List<String> {
        val history = JSONArray(preferences.getString(KEY_HISTORY_LIST, "[]") ?: "[]")
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
        private const val KEY_HISTORY_LIST = "history_list"
        private const val KEY_TEMPLATE_PAYLOAD = "template_payload"
        private const val KEY_SELECTED_TEMPLATE_ID = "selected_template_id"
    }
}
