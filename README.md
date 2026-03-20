# PhoneMoneyAI

PhoneMoneyAI 当前先聚焦为一个 **“自动刷视频 + 模板下载 + 一键运行” APK + 配套后端**。Android 端负责模板下载、目标 App 拉起、无障碍自动上滑；后端代码继续保留，便于后续扩展。

## 已实现模块

- `/plan`：预览 Task DSL（intent / entities / steps）。
- `/task`：创建/列出/更新任务，保存 Task DSL 到 SQLite。
- `/task/{task_id}/next`：取当前待执行步骤与决策状态。
- `/task/{task_id}/result`：回写单步结果，自动进入 `running` / `retry` / `success` / `fail`。
- `/screen`：融合 UI Tree 与 OCR，产出统一元素视图。
- `/decide`：对融合元素做评分，优先 UI Tree，再校验动作合法性，最后走 fallback 自愈策略。
- `/validate`：对动作做坐标范围与重复动作校验。
- `/feedback`：记录反馈日志，便于回放与调试。
- `/execute`：将动作翻译为 ADB 命令，支持 dry-run 直连调试。
- `/health`：健康检查。

## 架构映射

- `app/planner.py`：Goal Normalize / Intent Parser / Entity Extractor / Step Generator。
- `app/orchestrator.py`：TaskQueue / Scheduler 简化版 / 状态机 / RetryManager / Step Tracker。
- `app/perception.py`：UI Tree 与 OCR 融合。
- `app/decision.py`：Rule Engine / Scorer / Validator / Fallback Manager。
- `app/executor.py`：ADB Action Dispatcher。
- `app/storage.py`：SQLite 持久化任务与反馈。
- `app/main.py`：FastAPI API。

## 启动

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

## API 直连

### 1. 预览规划

```bash
curl -X POST http://127.0.0.1:8000/plan \
  -H 'Content-Type: application/json' \
  -d '{
    "goal": "打开微信并点击收款码",
    "app_name": "com.tencent.mm"
  }'
```

### 2. 创建任务

```bash
curl -X POST http://127.0.0.1:8000/task \
  -H 'Content-Type: application/json' \
  -d '{
    "goal": "打开微信并点击收款码",
    "app_name": "com.tencent.mm"
  }'
```

### 3. 获取下一步

```bash
curl http://127.0.0.1:8000/task/<task_id>/next
```

### 4. 上传感知数据

```bash
curl -X POST http://127.0.0.1:8000/screen \
  -H 'Content-Type: application/json' \
  -d '{
    "task_id": "task-demo",
    "ui_tree": [
      {
        "text": "收款码",
        "resourceId": "btn_payee_code",
        "bounds": [0, 0, 100, 100],
        "clickable": true,
        "className": "android.widget.Button"
      }
    ],
    "ocr": []
  }'
```

### 5. 请求决策

```bash
curl -X POST http://127.0.0.1:8000/decide \
  -H 'Content-Type: application/json' \
  -d '{
    "goal": "收款码",
    "current_step": {
      "id": "step-2",
      "description": "Find target UI element",
      "action": "tap",
      "target": "收款码",
      "params": {}
    },
    "ui_tree": [
      {
        "text": "收款码",
        "resourceId": "btn_payee_code",
        "bounds": [0, 0, 100, 100],
        "clickable": true,
        "className": "android.widget.Button"
      }
    ],
    "ocr": [],
    "history": [],
    "last_action": null,
    "last_result": null,
    "screen_width": 1080,
    "screen_height": 1920
  }'
```

### 6. 校验动作

```bash
curl -X POST http://127.0.0.1:8000/validate \
  -H 'Content-Type: application/json' \
  -d '{
    "state": {
      "goal": "收款码",
      "current_step": null,
      "ui_tree": [],
      "ocr": [],
      "history": [],
      "last_action": null,
      "last_result": null,
      "screen_width": 1080,
      "screen_height": 1920
    },
    "action": {
      "action": "tap",
      "coordinates": [500, 800]
    }
  }'
```

### 7. 回写步骤结果

```bash
curl -X POST http://127.0.0.1:8000/task/<task_id>/result \
  -H 'Content-Type: application/json' \
  -d '{
    "success": false,
    "error_type": "not_found",
    "message": "button missing"
  }'
```

### 8. ADB 执行 dry-run

```bash
curl -X POST http://127.0.0.1:8000/execute \
  -H 'Content-Type: application/json' \
  -d '{
    "device_id": "emulator-5554",
    "dry_run": true,
    "action": {
      "action": "tap",
      "coordinates": [50, 50]
    }
  }'
```

返回的 `command` 字段就是设备控制层可以直接执行的 ADB 命令。

## OpenAI API 接入

如果你希望 Planner / Decision 直接接 OpenAI：

```bash
export PHONEMONEYAI_OPENAI_API_KEY="your_api_key"
export PHONEMONEYAI_OPENAI_MODEL="gpt-4.1-mini"
```

当环境变量存在时，`Planner` 会优先尝试用 OpenAI 返回结构化 JSON Task DSL；否则自动退回本地规则规划。

## Android 客户端（当前主目标：自动刷视频 APK）

仓库内置了一个 `android-client/` Kotlin Android 工程，当前优先交付的是：

1. **模板下载**：从远端 JSON 地址下载刷视频模板。
2. **模板选择**：内置抖音 / 快手 / 视频号默认模板，也支持替换为远端模板。
3. **一键运行**：点击按钮后自动保存模板、拉起目标 App，并把无障碍服务切换到自动刷视频模式。
4. **自动上滑**：`PhoneMoneyAccessibilityService` 在目标 App 前台时，按模板配置的坐标和时间间隔持续上滑。
5. **运行历史**：APK 首页展示当前模板、运行任务 ID、当前步骤和最近刷视频历史。

### 模板 JSON 格式

```json
{
  "templates": [
    {
      "id": "douyin-default",
      "name": "抖音默认刷视频",
      "goal": "自动上滑切换抖音短视频",
      "app_package": "com.ss.android.ugc.aweme",
      "swipe_start": [540, 1600],
      "swipe_end": [540, 500],
      "swipe_interval_ms": 2500,
      "launch_on_start": true
    }
  ]
}
```

### APK 使用方式

```bash
export JAVA_HOME=$HOME/.local/share/mise/installs/java/17.0.2
export ANDROID_HOME=/root/android-sdk
export ANDROID_SDK_ROOT=/root/android-sdk
cd android-client
gradle assembleDebug
```

如果你的环境还没准备好 Android SDK，需要先安装命令行工具并接受 license，然后再执行上面的打包命令。仓库当前已补充 `android-client/gradle.properties` 以启用 AndroidX，本地打出的调试 APK 默认位于：

```text
artifacts/phonemoneyai-video-runner-debug.apk
```

为了避免把大体积二进制直接塞进 Git 历史，仓库分发方式改为 **GitHub Releases 直传 APK asset**。构建完成后，请把 `artifacts/phonemoneyai-video-runner-debug.apk` 上传到对应 Release 的 Assets 区域，这样 Release 页面会直接出现一个可下载的 `.apk` 按钮。

仓库已经内置 `.github/workflows/release-apk.yml`：向 GitHub 推送形如 `apk-release-2026-03-20` 的 tag 后，Actions 会自动构建 `android-client`，并把 `phonemoneyai-video-runner-debug.apk` 作为独立 Release asset 挂到对应 Release 页面。

安装后：

1. 打开 APK。
2. 输入模板 URL 并点击“下载模板”（也可以直接使用内置模板）。
3. 选择一个模板。
4. 打开无障碍设置并启用 `PhoneMoneyAI Accessibility`。
5. 回到 APK，点击“一键运行”。

### 当前实现说明

- `MainActivity`：聚焦模板下载、模板选择、一键运行和运行历史展示。
- `TaskSessionStore`：持久化模板列表、当前选中模板、运行状态与历史。
- `VideoTemplateRepository`：负责模板 JSON 的下载、解析和内置默认模板。
- `PhoneMoneyAccessibilityService`：在模板目标包名命中时执行自动上滑。
- `ActionExecutor`：保留通用动作能力，并额外提供直接执行模板滑动的方法。

### 后续可继续补的内容

1. 模板分享页 / 本地导入文件。
2. 更细的随机停留、点赞/收藏概率控制。
3. 前台悬浮窗和运行统计。
