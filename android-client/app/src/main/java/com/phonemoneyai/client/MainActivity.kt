package com.phonemoneyai.client

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.phonemoneyai.client.automation.TaskSessionStore
import com.phonemoneyai.client.template.VideoAutomationTemplate
import com.phonemoneyai.client.template.VideoTemplateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var taskSessionStore: TaskSessionStore
    private val templateRepository = VideoTemplateRepository()

    private lateinit var templateUrlInput: EditText
    private lateinit var taskIdLabel: TextView
    private lateinit var currentTemplateLabel: TextView
    private lateinit var currentStepLabel: TextView
    private lateinit var currentVideoLabel: TextView
    private lateinit var executionMetaLabel: TextView
    private lateinit var statusView: TextView
    private lateinit var templateList: ListView
    private lateinit var historyList: ListView
    private lateinit var runtimeLogList: ListView
    private lateinit var templateAdapter: ArrayAdapter<String>
    private lateinit var historyAdapter: ArrayAdapter<String>
    private lateinit var runtimeLogAdapter: ArrayAdapter<String>

    private var templates: List<VideoAutomationTemplate> = emptyList()
    private var selectedTemplate: VideoAutomationTemplate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        taskSessionStore = TaskSessionStore(applicationContext)

        templateUrlInput = findViewById(R.id.templateUrlInput)
        taskIdLabel = findViewById(R.id.taskIdLabel)
        currentTemplateLabel = findViewById(R.id.currentTemplateLabel)
        currentStepLabel = findViewById(R.id.currentStepLabel)
        currentVideoLabel = findViewById(R.id.currentVideoLabel)
        executionMetaLabel = findViewById(R.id.executionMetaLabel)
        statusView = findViewById(R.id.statusView)
        templateList = findViewById(R.id.templateList)
        historyList = findViewById(R.id.taskHistoryList)
        runtimeLogList = findViewById(R.id.runtimeLogList)

        templateAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, mutableListOf())
        historyAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        runtimeLogAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        templateList.adapter = templateAdapter
        templateList.choiceMode = ListView.CHOICE_MODE_SINGLE
        historyList.adapter = historyAdapter
        runtimeLogList.adapter = runtimeLogAdapter

        bindTemplateList()
        loadTemplates(taskSessionStore.templates())
        hydrateUiFromSession()
        handleDeepLink(intent?.data)

        findViewById<Button>(R.id.downloadTemplateButton).setOnClickListener {
            lifecycleScope.launch {
                runCatching {
                    statusView.text = "状态：正在下载模板..."
                    val downloaded = withContext(Dispatchers.IO) {
                        templateRepository.download(templateUrlInput.text.toString().trim())
                    }
                    taskSessionStore.saveTemplates(downloaded)
                    loadTemplates(downloaded)
                    statusView.text = "状态：模板下载完成，共 ${downloaded.size} 个"
                }.onFailure {
                    statusView.text = "状态：模板下载失败 ${it.message}"
                }
            }
        }

        findViewById<Button>(R.id.oneTapRunButton).setOnClickListener {
            val template = selectedTemplate ?: templates.firstOrNull()
            if (template == null) {
                statusView.text = "状态：请先下载或选择模板"
                return@setOnClickListener
            }
            taskSessionStore.activateVideoAutomation(template)
            openTargetApp(template)
            hydrateUiFromSession("一键运行已启动")
        }

        findViewById<Button>(R.id.stopAutomationButton).setOnClickListener {
            taskSessionStore.updateAutomationEnabled(false)
            statusView.text = "状态：自动刷视频已停止"
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

    private fun bindTemplateList() {
        templateList.setOnItemClickListener { _, _, position, _ ->
            selectedTemplate = templates.getOrNull(position)
            selectedTemplate?.let {
                taskSessionStore.selectTemplate(it)
                hydrateUiFromSession("已选择模板：${it.name}")
            }
        }
    }

    private fun loadTemplates(items: List<VideoAutomationTemplate>) {
        templates = items
        templateAdapter.clear()
        templateAdapter.addAll(items.map { "${it.name}｜${it.appPackage}｜${it.swipeIntervalMs}ms" })
        templateAdapter.notifyDataSetChanged()
        val selectedId = taskSessionStore.selectedTemplateId()
        val selectedIndex = items.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: 0
        if (items.isNotEmpty()) {
            templateList.setItemChecked(selectedIndex, true)
            selectedTemplate = items[selectedIndex]
        }
    }

    private fun hydrateUiFromSession(statusOverride: String? = null) {
        val template = taskSessionStore.currentTemplate() ?: selectedTemplate
        taskIdLabel.text = "运行任务：${taskSessionStore.currentTaskId() ?: "未启动"}"
        currentTemplateLabel.text = template?.let {
            "当前模板：${it.name} / ${it.appPackage} / ${it.swipeIntervalMs}ms"
        } ?: "当前模板：未选择"
        currentStepLabel.text = "当前步骤：${taskSessionStore.currentStep()}"
        currentVideoLabel.text = "当前视频：${taskSessionStore.currentVideoTitle()}"
        executionMetaLabel.text = "执行结果：${taskSessionStore.executionMeta()}"

        historyAdapter.clear()
        historyAdapter.addAll(taskSessionStore.historyEntries().ifEmpty { listOf("暂无运行历史") })
        historyAdapter.notifyDataSetChanged()

        runtimeLogAdapter.clear()
        runtimeLogAdapter.addAll(taskSessionStore.runtimeLogEntries().ifEmpty { listOf("暂无实时日志") })
        runtimeLogAdapter.notifyDataSetChanged()

        statusView.text = statusOverride?.let { "状态：$it" } ?: if (taskSessionStore.automationEnabled()) {
            "状态：自动刷视频运行中"
        } else {
            "状态：等待下载模板或一键运行"
        }
    }

    private fun handleDeepLink(data: Uri?) {
        if (data == null || data.scheme != "phonemoneyai" || data.host != "task") return
        val templateId = data.getQueryParameter("template_id") ?: return
        val matched = taskSessionStore.templates().firstOrNull { it.id == templateId } ?: return
        selectedTemplate = matched
        taskSessionStore.activateVideoAutomation(matched)
        openTargetApp(matched)
        hydrateUiFromSession("已通过 deep link 一键运行")
    }

    private fun openTargetApp(template: VideoAutomationTemplate) {
        if (!template.launchOnStart) return
        val launchIntent = packageManager.getLaunchIntentForPackage(template.appPackage) ?: run {
            statusView.text = "状态：未找到目标应用 ${template.appPackage}"
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }
}
