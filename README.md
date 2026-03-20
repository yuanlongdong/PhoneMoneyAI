# PhoneMoneyAI

PhoneMoneyAI 是一个面向 Android 自动化的 AI Mobile Agent MVP，采用 **FastAPI + ADB + SQLite** 的后端骨架，覆盖：任务规划、任务编排、界面感知融合、决策、执行与调试接口。

## 已实现模块

- `/task`：创建/列出/更新任务，保存 Task DSL 到 SQLite。
- `/screen`：融合 UI Tree 与 OCR，产出统一元素视图。
- `/decide`：优先使用 UI Tree，其次使用 OCR，再走 fallback 自愈策略。
- `/execute`：将动作翻译为 ADB 命令，支持 dry-run 直连调试。
- `/health`：健康检查。

## 目录

- `app/planner.py`：将目标标准化成 Task DSL。
- `app/orchestrator.py`：任务编排与状态更新。
- `app/perception.py`：UI/OCR 融合。
- `app/decision.py`：规则决策 + fallback。
- `app/executor.py`：ADB 命令构建与执行。
- `app/storage.py`：SQLite 持久化。
- `app/main.py`：FastAPI API。

## 启动

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

## API 直连

### 1. 创建任务

```bash
curl -X POST http://127.0.0.1:8000/task \
  -H 'Content-Type: application/json' \
  -d '{
    "goal": "打开微信并点击收款码",
    "app_name": "com.tencent.mm"
  }'
```

### 2. 发送感知数据

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

### 3. 请求决策

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
    "last_result": null
  }'
```

### 4. ADB 执行 dry-run

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

返回的 `command` 字段就是可以给设备控制层直接执行的 ADB 命令。

## OpenAI API 接入建议

当前仓库已经为 AI 决策层预留了配置位：

- 环境变量：`PHONEMONEYAI_OPENAI_API_KEY`
- 模型变量：`PHONEMONEYAI_OPENAI_MODEL`

下一步可以在 `app/planner.py` 或 `app/decision.py` 中接入 OpenAI Responses API，把规则引擎与 LLM 决策混合起来。出于安全原因，**不要把任何 GitHub token 或 OpenAI key 提交到仓库里**。
