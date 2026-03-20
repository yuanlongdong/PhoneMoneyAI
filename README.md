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

## 下一步建议

- 增加 Android Kotlin Accessibility 服务，把 UI 树、截图、OCR 结果直接上报。
- 接入 ML Kit OCR 真机结果，而不是手工模拟 payload。
- 把成功路径 / 失败案例沉淀成长期记忆表。
- 为 `/execute` 增加真机回执采集与截图闭环。

## Android 客户端（新增）

仓库现在新增了一个 `android-client/` Kotlin 工程骨架，包含：

- `MainActivity`：引导用户打开无障碍设置。
- `PhoneMoneyAccessibilityService`：监听界面变化、抓取 UI 树、上传 `/screen`、请求 `/task/{task_id}/next` 和 `/decide`。
- `AccessibilityTreeSerializer`：把 `AccessibilityNodeInfo` 展平成后端可接收的 `ui_tree`。
- `ActionExecutor`：先支持基础 `tap` / `back`，为后续 `input` / `swipe` / `open_app` 留了扩展点。
- `PhoneMoneyApiClient`：直连当前 FastAPI 后端。

> 默认后端地址使用 Android 模拟器访问宿主机：`http://10.0.2.2:8000`

### Android 启动

```bash
cd android-client
gradle assembleDebug
```

如果你是第一次继续开发，建议下一步补：

1. 在 `PhoneMoneyAccessibilityService` 中把 `activeTaskId` / `activeGoal` 接到 UI 或 deep link。
2. 接入截图 + ML Kit OCR 上报。
3. 在 `ActionExecutor` 中补完 `input` / `swipe` / `open_app`。
4. 增加执行结果回写 `/task/{task_id}/result`。
