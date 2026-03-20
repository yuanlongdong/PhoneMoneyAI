package com.phonemoneyai.client.automation

import android.content.Context

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

    fun currentTaskId(): String? = preferences.getString(KEY_TASK_ID, null)
    fun currentGoal(): String = preferences.getString(KEY_GOAL, "") ?: ""
    fun currentAppPackage(): String? = preferences.getString(KEY_APP_PACKAGE, null)
    fun automationEnabled(): Boolean = preferences.getBoolean(KEY_AUTOMATION_ENABLED, false)

    companion object {
        private const val PREFS_NAME = "phone_money_ai_session"
        private const val KEY_TASK_ID = "task_id"
        private const val KEY_GOAL = "goal"
        private const val KEY_APP_PACKAGE = "app_package"
        private const val KEY_AUTOMATION_ENABLED = "automation_enabled"
    }
}
