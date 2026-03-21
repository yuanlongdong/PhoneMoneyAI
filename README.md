# PhoneMoneyAI

PhoneMoneyAI 是一个面向 Android 自动化的 AI Mobile Agent 后端骨架，采用 **FastAPI + ADB + SQLite + 可选 OpenAI API**，覆盖：任务规划、任务编排、感知融合、决策评分、动作校验、执行与反馈闭环。

## 已实现模块

- `/plan`：预览 Task DSL（intent / entities / steps）。
- `/task`：创建/列出/更新任务，保存 Task DSL 到 SQLite。
- `/task/{task_id}/next`：取当前待执行步骤与决策状态。
- `/task/{task_id}/result`：回写单步结果，自动进入 `running` / `retry` / `success` / `fail`。
- `/screen`：融合 UI Tree 与 OCR，产出统一元素视图。
- `/decide`：对融合元素做评分，优先 UI Tree，再校验动作合法性，最后走 fallback 自愈策略。
- `/validate`：对动作做坐标范围与重复动作校验。
- `/feedback`：记录反馈日志，便于回放与调试。
- `/memory/search`：支持字段权重、短语命中、关键词召回解释的记忆检索排序。
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

## Android 客户端

仓库内置了一个 `android-client/` Kotlin Android 工程，当前既保留后端联调代码，也新增了一个**本地 JSON 任务驱动的 Android 自动化测试助手**，用于合规的 UI 测试、演示与内部 RPA。

### 已完成能力

- `MainActivity`：内置 JSON 任务编辑器、模板载入、保存配置、一键启动/停止、日志清理、任务历史筛选，以及当前步骤/循环进度展示。
- `AutomationForegroundService`：以前台通知维持任务执行状态，适合长时间内容流浏览测试。
- `PhoneMoneyAccessibilityService`：负责本地自动化主循环，按配置执行 `open_app / wait / swipe / back` 步骤，并记录每轮执行日志。
- `ActionExecutor`：支持打开目标应用、随机化滑动轨迹、随机停留时间，以及基础无障碍动作执行。
- `TaskSessionStore`：持久化 JSON 配置、执行状态、循环次数、历史事件与本地日志。
- `PhoneMoneyApiClient` 与既有后端链路仍保留，便于后续继续做端到端联调。

### 默认任务模板

默认 JSON 模板支持：

1. 打开目标应用；
2. 首屏等待；
3. 向上滑动浏览内容流；
4. 随机停留；
5. 按 `loop_count` 循环执行；当 `loop_count <= 0` 时持续运行直到手动停止。

### Android 构建

```bash
cd android-client
export JAVA_HOME=/root/.local/share/mise/installs/java/17.0.2
gradle assembleRelease
```

> 说明：Release 构建已配置为可安装的 release 变体，并默认复用 debug signing，方便测试环境直接安装。

### 当前仍建议继续补的内容

1. 为 Android 端补充导入/导出任务模板文件。
2. 为自动化循环增加更细粒度的暂停/恢复控制。
3. 在具备 Android SDK 的 CI 环境中产出并归档正式的 Release APK 工件。
