package com.phonemoneyai.client

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.phonemoneyai.client.api.PhoneMoneyApiClient
import com.phonemoneyai.client.automation.TaskSessionStore
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val apiClient = PhoneMoneyApiClient()
    private lateinit var taskSessionStore: TaskSessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        taskSessionStore = TaskSessionStore(applicationContext)

        val goalInput = findViewById<EditText>(R.id.goalInput)
        val packageInput = findViewById<EditText>(R.id.packageInput)
        val taskIdLabel = findViewById<TextView>(R.id.taskIdLabel)
        val statusView = findViewById<TextView>(R.id.statusView)

        goalInput.setText(taskSessionStore.currentGoal())
        packageInput.setText(taskSessionStore.currentAppPackage().orEmpty())
        taskIdLabel.text = "当前 Task ID：${taskSessionStore.currentTaskId() ?: "未创建"}"
        statusView.text = if (taskSessionStore.automationEnabled()) "状态：自动化运行中" else getString(R.string.status_idle)

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
                    taskIdLabel.text = "当前 Task ID：${task.taskId}"
                    statusView.text = "状态：任务已创建 (${task.status})"
                }.onFailure {
                    statusView.text = "状态：任务创建失败 ${it.message}"
                }
            }
        }

        findViewById<Button>(R.id.startAutomationButton).setOnClickListener {
            taskSessionStore.updateAutomationEnabled(true)
            statusView.text = "状态：自动化运行中"
        }

        findViewById<Button>(R.id.stopAutomationButton).setOnClickListener {
            taskSessionStore.updateAutomationEnabled(false)
            statusView.text = "状态：自动化已停止"
        }

        findViewById<Button>(R.id.openAccessibilitySettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}
