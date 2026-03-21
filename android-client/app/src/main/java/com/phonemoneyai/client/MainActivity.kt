package com.phonemoneyai.client

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.phonemoneyai.client.automation.AutomationForegroundService
import com.phonemoneyai.client.automation.TaskSessionStore

class MainActivity : AppCompatActivity() {
    private lateinit var taskSessionStore: TaskSessionStore
    private lateinit var packageInput: EditText
    private lateinit var configEditor: EditText
    private lateinit var historyFilterInput: EditText
    private lateinit var taskIdLabel: TextView
    private lateinit var currentStepLabel: TextView
    private lateinit var executionMetaLabel: TextView
    private lateinit var loopSummaryLabel: TextView
    private lateinit var statusView: TextView
    private lateinit var taskHistoryList: ListView
    private lateinit var logList: ListView
    private lateinit var historyAdapter: ArrayAdapter<String>
    private lateinit var logAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        taskSessionStore = TaskSessionStore(applicationContext)

        packageInput = findViewById(R.id.packageInput)
        configEditor = findViewById(R.id.configEditor)
        historyFilterInput = findViewById(R.id.historyFilterInput)
        taskIdLabel = findViewById(R.id.taskIdLabel)
        currentStepLabel = findViewById(R.id.currentStepLabel)
        executionMetaLabel = findViewById(R.id.executionMetaLabel)
        loopSummaryLabel = findViewById(R.id.loopSummaryLabel)
        statusView = findViewById(R.id.statusView)
        taskHistoryList = findViewById(R.id.taskHistoryList)
        logList = findViewById(R.id.logList)
        historyAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        logAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        taskHistoryList.adapter = historyAdapter
        logList.adapter = logAdapter

        if (taskSessionStore.localConfigText().isBlank()) {
            configEditor.setText(TaskSessionStore.DEFAULT_CONFIG_JSON)
        }
        hydrateUiFromSession()
        handleDeepLink(intent?.data)
        bindHistoryFilter()

        findViewById<Button>(R.id.loadTemplateButton).setOnClickListener {
            configEditor.setText(TaskSessionStore.DEFAULT_CONFIG_JSON)
            statusView.text = getString(R.string.status_template_loaded)
        }

        findViewById<Button>(R.id.saveConfigButton).setOnClickListener {
            saveConfig(showStatus = true)
        }

        findViewById<Button>(R.id.startAutomationButton).setOnClickListener {
            startAutomation()
        }

        findViewById<Button>(R.id.stopAutomationButton).setOnClickListener {
            taskSessionStore.updateAutomationEnabled(false)
            taskSessionStore.updateRuntimeState("已停止", "manual-stop", "用户手动停止")
            taskSessionStore.appendLog("INFO", "手动停止自动化")
            AutomationForegroundService.stop(applicationContext)
            hydrateUiFromSession()
            statusView.text = getString(R.string.status_stopped)
        }

        findViewById<Button>(R.id.clearLogsButton).setOnClickListener {
            taskSessionStore.clearLogs()
            renderLogs()
            statusView.text = getString(R.string.status_logs_cleared)
        }

        findViewById<Button>(R.id.openAccessibilitySettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        hydrateUiFromSession()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent.data)
    }

    private fun startAutomation() {
        if (!saveConfig(showStatus = false)) return
        taskSessionStore.updateAutomationEnabled(true)
        taskSessionStore.updateRuntimeState("等待无障碍服务", "run-requested", "前台服务已启动")
        taskSessionStore.appendLog("INFO", "启动自动化任务")
        AutomationForegroundService.start(applicationContext, getString(R.string.status_running_local))
        hydrateUiFromSession()
        statusView.text = getString(R.string.status_running_local)
    }

    private fun saveConfig(showStatus: Boolean): Boolean {
        val configText = configEditor.text.toString().trim()
        val packageName = packageInput.text.toString().trim()
        if (configText.isBlank()) {
            statusView.text = getString(R.string.status_config_required)
            return false
        }
        val normalized = if (packageName.isBlank()) configText else configText.replace("com.example.targetapp", packageName)
        return taskSessionStore.run {
            kotlin.runCatching {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; prettyPrint = true }
                val parsedConfig = json
                    .decodeFromString(com.phonemoneyai.client.model.AutomationTaskConfig.serializer(), normalized)
                val effectiveConfig = if (packageName.isBlank()) parsedConfig else parsedConfig.copy(appPackage = packageName)
                val persistedConfig = json.encodeToString(com.phonemoneyai.client.model.AutomationTaskConfig.serializer(), effectiveConfig)
                configEditor.setText(persistedConfig)
                saveLocalConfig(persistedConfig, effectiveConfig)
                val loopSummary = if (effectiveConfig.loopCount <= 0) "持续运行，直到手动停止" else "${effectiveConfig.loopCount} 轮"
                updateRuntimeState("配置已保存", "config-saved", "${effectiveConfig.steps.size} 个步骤 / ${loopSummary}")
                appendLog("INFO", "保存配置成功: ${effectiveConfig.name} -> ${effectiveConfig.appPackage}")
                if (showStatus) {
                    statusView.text = getString(R.string.status_config_saved)
                }
                hydrateUiFromSession()
            }.onFailure {
                statusView.text = getString(R.string.status_config_invalid, it.message ?: "unknown")
            }.isSuccess
        }
    }

    private fun bindHistoryFilter() {
        historyFilterInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                renderHistory(s?.toString().orEmpty())
            }
        })
    }

    private fun hydrateUiFromSession(statusOverride: String? = null) {
        packageInput.setText(taskSessionStore.currentAppPackage().orEmpty())
        if (configEditor.text.isNullOrBlank()) {
            configEditor.setText(taskSessionStore.localConfigText())
        }
        taskIdLabel.text = "当前任务：${taskSessionStore.currentGoal().ifBlank { "未配置" }}"
        currentStepLabel.text = "当前步骤：${taskSessionStore.currentStep()}"
        executionMetaLabel.text = "执行结果：${taskSessionStore.executionMeta()}"
        val configuredLoops = taskSessionStore.configuredLoopCount()
        val loopTargetLabel = if (configuredLoops <= 0) "∞" else configuredLoops.toString()
        loopSummaryLabel.text = "循环进度：${taskSessionStore.completedLoops()}/${loopTargetLabel}"
        renderHistory(historyFilterInput.text?.toString().orEmpty())
        renderLogs()
        statusView.text = when {
            taskSessionStore.automationEnabled() -> getString(R.string.status_running_local)
            statusOverride != null -> statusOverride
            else -> getString(R.string.status_idle_local)
        }
    }

    private fun renderHistory(filter: String) {
        val items = taskSessionStore.historyEntries()
            .filter { filter.isBlank() || it.contains(filter, ignoreCase = true) }
            .ifEmpty { listOf("暂无匹配历史") }
        historyAdapter.clear()
        historyAdapter.addAll(items)
        historyAdapter.notifyDataSetChanged()
    }

    private fun renderLogs() {
        val items = taskSessionStore.logEntries().ifEmpty { listOf("暂无执行日志") }
        logAdapter.clear()
        logAdapter.addAll(items)
        logAdapter.notifyDataSetChanged()
    }

    private fun handleDeepLink(data: Uri?) {
        if (data == null || data.scheme != "phonemoneyai") return
        val packageName = data.getQueryParameter("app_package") ?: return
        packageInput.setText(packageName)
        configEditor.setText(TaskSessionStore.DEFAULT_CONFIG_JSON.replace("com.example.targetapp", packageName))
        taskSessionStore.appendLog("INFO", "通过 Deep Link 导入包名: $packageName")
        hydrateUiFromSession(getString(R.string.status_template_loaded))
    }
}
