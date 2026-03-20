package com.phonemoneyai.client.automation

import android.content.Context
import org.json.JSONArray

class TaskSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(taskId: String, goal: String, appPackage: String?) {
        preferences.edit()
            .putString(KEY_TASK_ID, taskId)
            .putString(KEY_GOAL, goal)
            .putString(KEY_APP_PACKAGE, appPackage)
            .putBoolean(KEY_AUTOMATION_ENABLED, false)
            .apply()
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
    }
}
