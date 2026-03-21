package com.phonemoneyai.client.automation

import android.content.Context
import com.phonemoneyai.client.model.AutomationTaskConfig
import kotlinx.serialization.json.Json
import org.json.JSONArray

class TaskSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

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
            .putString(KEY_LOG_LIST, "[]")
            .apply()
    }

    fun saveLocalConfig(configText: String, config: AutomationTaskConfig) {
        preferences.edit()
            .putString(KEY_LOCAL_CONFIG_TEXT, configText)
            .putString(KEY_APP_PACKAGE, config.appPackage)
            .putString(KEY_GOAL, config.name)
            .putInt(KEY_LOOP_COUNT, config.loopCount)
            .apply()
    }

    fun localConfigText(): String = preferences.getString(KEY_LOCAL_CONFIG_TEXT, DEFAULT_CONFIG_JSON) ?: DEFAULT_CONFIG_JSON

    fun parseLocalConfig(): Result<AutomationTaskConfig> = runCatching {
        json.decodeFromString(AutomationTaskConfig.serializer(), localConfigText())
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

    fun appendLog(level: String, message: String) {
        val logs = JSONArray(preferences.getString(KEY_LOG_LIST, "[]") ?: "[]")
        val timestamp = System.currentTimeMillis()
        logs.put(0, "[$level] $timestamp $message")
        while (logs.length() > 100) {
            logs.remove(logs.length() - 1)
        }
        preferences.edit().putString(KEY_LOG_LIST, logs.toString()).apply()
    }

    fun clearLogs() {
        preferences.edit().putString(KEY_LOG_LIST, "[]").apply()
    }

    fun startRun(totalLoops: Int) {
        preferences.edit()
            .putInt(KEY_LOOP_COUNT, totalLoops)
            .putInt(KEY_COMPLETED_LOOPS, 0)
            .apply()
    }

    fun markLoopCompleted(loopIndex: Int) {
        preferences.edit().putInt(KEY_COMPLETED_LOOPS, loopIndex).apply()
    }

    private fun appendHistory(entry: String) {
        val history = JSONArray(preferences.getString(KEY_HISTORY_LIST, "[]") ?: "[]")
        history.put(0, entry)
        while (history.length() > 30) {
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
    fun configuredLoopCount(): Int = preferences.getInt(KEY_LOOP_COUNT, 0)
    fun completedLoops(): Int = preferences.getInt(KEY_COMPLETED_LOOPS, 0)
    fun historyEntries(): List<String> {
        val history = JSONArray(preferences.getString(KEY_HISTORY_LIST, "[]") ?: "[]")
        return buildList {
            for (index in 0 until history.length()) add(history.optString(index))
        }
    }

    fun logEntries(): List<String> {
        val logs = JSONArray(preferences.getString(KEY_LOG_LIST, "[]") ?: "[]")
        return buildList {
            for (index in 0 until logs.length()) add(logs.optString(index))
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
        private const val KEY_LOG_LIST = "log_list"
        private const val KEY_LOCAL_CONFIG_TEXT = "local_config_text"
        private const val KEY_LOOP_COUNT = "loop_count"
        private const val KEY_COMPLETED_LOOPS = "completed_loops"

        val DEFAULT_CONFIG_JSON = """
            {
              "name": "内容流浏览测试",
              "app_package": "com.example.targetapp",
              "loop_count": 0,
              "steps": [
                {
                  "action": "open_app",
                  "label": "打开目标应用"
                },
                {
                  "action": "wait",
                  "label": "首屏稳定等待",
                  "duration_min_ms": 1200,
                  "duration_max_ms": 2200
                },
                {
                  "action": "swipe",
                  "label": "向上浏览内容",
                  "direction": "up",
                  "duration_min_ms": 900,
                  "duration_max_ms": 1600,
                  "start_ratio": { "x": 0.5, "y": 0.82 },
                  "end_ratio": { "x": 0.48, "y": 0.26 }
                },
                {
                  "action": "wait",
                  "label": "随机停留",
                  "duration_min_ms": 2500,
                  "duration_max_ms": 5200
                }
              ]
            }
        """.trimIndent()
    }
}
