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
import androidx.lifecycle.lifecycleScope
import com.phonemoneyai.client.api.PhoneMoneyApiClient
import com.phonemoneyai.client.automation.TaskSessionStore
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val apiClient = PhoneMoneyApiClient()
    private lateinit var taskSessionStore: TaskSessionStore
    private lateinit var goalInput: EditText
    private lateinit var packageInput: EditText
    private lateinit var historyFilterInput: EditText
    private lateinit var taskIdLabel: TextView
    private lateinit var currentStepLabel: TextView
    private lateinit var executionMetaLabel: TextView
    private lateinit var statusView: TextView
    private lateinit var taskHistoryList: ListView
    private lateinit var historyAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        taskSessionStore = TaskSessionStore(applicationContext)

        goalInput = findViewById(R.id.goalInput)
        packageInput = findViewById(R.id.packageInput)
        historyFilterInput = findViewById(R.id.historyFilterInput)
        taskIdLabel = findViewById(R.id.taskIdLabel)
        currentStepLabel = findViewById(R.id.currentStepLabel)
        executionMetaLabel = findViewById(R.id.executionMetaLabel)
        statusView = findViewById(R.id.statusView)
        taskHistoryList = findViewById(R.id.taskHistoryList)
        historyAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        taskHistoryList.adapter = historyAdapter

        hydrateUiFromSession()
        handleDeepLink(intent?.data)
        bindHistoryFilter()

        findViewById<Button>(R.id.createTaskButton).setOnClickListener {
            val goal = goalInput.text.toString().trim()
            val appPackage = packageInput.text.toString().trim().ifBlank { null }
            if (goal.isBlank()) {
                statusView.text = "状态：请输入任务目标"
                return@setOnClickListener
            }
            lifecycleScope.launch {
                runCatching {
                    statusView.text = "状态：创建任务中..."
                    val task = apiClient.createTask(goal, appPackage)
                    taskSessionStore.save(task.taskId, goal, appPackage)
                    taskSessionStore.updateRuntimeState("暂无", "task-created", "暂无")
                    hydrateUiFromSession(task.status)
                }.onFailure {
                    statusView.text = "状态：任务创建失败 ${it.message}"
                }
            }
        }

        findViewById<Button>(R.id.startAutomationButton).setOnClickListener {
            taskSessionStore.updateAutomationEnabled(true)
            statusView.text = "状态：自动化运行中"
            hydrateUiFromSession()
        }

        findViewById<Button>(R.id.stopAutomationButton).setOnClickListener {
            taskSessionStore.updateAutomationEnabled(false)
            statusView.text = "状态：自动化已停止"
            hydrateUiFromSession()
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
        goalInput.setText(taskSessionStore.currentGoal())
        packageInput.setText(taskSessionStore.currentAppPackage().orEmpty())
        taskIdLabel.text = "当前 Task ID：${taskSessionStore.currentTaskId() ?: "未创建"}"
        currentStepLabel.text = "当前步骤：${taskSessionStore.currentStep()}"
        executionMetaLabel.text = "执行结果：${taskSessionStore.executionMeta()}"
        renderHistory(historyFilterInput.text?.toString().orEmpty())
        statusView.text = when {
            taskSessionStore.automationEnabled() -> "状态：自动化运行中"
            statusOverride != null -> "状态：任务已创建 ($statusOverride)"
            else -> getString(R.string.status_idle)
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

    private fun handleDeepLink(data: Uri?) {
        if (data == null || data.scheme != "phonemoneyai") return
        val taskId = data.getQueryParameter("task_id") ?: return
        val goal = data.getQueryParameter("goal").orEmpty()
        val appPackage = data.getQueryParameter("app_package")
        taskSessionStore.save(taskId, goal, appPackage)
        taskSessionStore.updateAutomationEnabled(true)
        taskSessionStore.updateRuntimeState("已导入，等待无障碍触发", "deep-link-import", "通过 deep link 导入")
        hydrateUiFromSession()
        statusView.text = "状态：已通过 deep link 导入任务"
    }
}
