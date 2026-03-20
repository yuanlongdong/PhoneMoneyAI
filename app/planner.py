from __future__ import annotations

import json
import uuid
from typing import Any

from .config import settings
from .models import ActionType, CreateTaskRequest, TaskDSL, TaskStep

try:
    from openai import OpenAI
except ImportError:  # optional dependency
    OpenAI = None


class Planner:
    def create_task(self, request: CreateTaskRequest) -> TaskDSL:
        goal = request.goal.strip()
        app_name = request.app_name or self._detect_app(goal)
        intent = self._detect_intent(goal)
        entities = self._extract_entities(goal, request.context)

        llm_plan = self._maybe_llm_plan(goal=goal, app_name=app_name, intent=intent, entities=entities)
        if llm_plan is not None:
            return llm_plan

        steps = self._default_steps(goal=goal, app_name=app_name, intent=intent, entities=entities)
        return TaskDSL(
            task_id=f"task-{uuid.uuid4().hex[:12]}",
            goal=goal,
            app_name=app_name,
            intent=intent,
            entities=entities,
            steps=steps,
        )

    def preview_plan(self, request: CreateTaskRequest) -> TaskDSL:
        return self.create_task(request)

    @staticmethod
    def _detect_app(goal: str) -> str:
        app_map = {
            "微信": "com.tencent.mm",
            "支付宝": "com.eg.android.AlipayGphone",
            "抖音": "com.ss.android.ugc.aweme",
            "淘宝": "com.taobao.taobao",
        }
        for keyword, package_name in app_map.items():
            if keyword in goal:
                return package_name
        return "unknown.app"

    @staticmethod
    def _detect_intent(goal: str) -> str:
        if any(keyword in goal for keyword in ["支付", "付款", "收款"]):
            return "payment"
        if any(keyword in goal for keyword in ["搜索", "查找"]):
            return "search"
        if any(keyword in goal for keyword in ["打开", "进入"]):
            return "navigation"
        return "general"

    @staticmethod
    def _extract_entities(goal: str, context: dict[str, Any]) -> dict[str, Any]:
        entities = {"goal_text": goal}
        if context:
            entities["context"] = context
        if "收款码" in goal:
            entities["target_text"] = "收款码"
        if "付款码" in goal:
            entities["target_text"] = "付款码"
        return entities

    def _default_steps(self, goal: str, app_name: str, intent: str, entities: dict[str, Any]) -> list[TaskStep]:
        target_text = entities.get("target_text") or goal
        steps = [
            TaskStep(id="step-1", description=f"Open {app_name}", action=ActionType.OPEN_APP, target=app_name),
            TaskStep(id="step-2", description=f"Navigate to goal: {goal}", action=ActionType.TAP, target=target_text),
        ]
        if intent == "search":
            steps.append(TaskStep(id="step-3", description="Input query text", action=ActionType.INPUT, params={"text": goal}))
        steps.append(TaskStep(id=f"step-{len(steps)+1}", description="Wait for result", action=ActionType.WAIT, params={"duration_ms": 1500}))
        return steps

    def _maybe_llm_plan(self, goal: str, app_name: str, intent: str, entities: dict[str, Any]) -> TaskDSL | None:
        if not settings.openai_api_key or OpenAI is None:
            return None

        client = OpenAI(api_key=settings.openai_api_key)
        prompt = {
            "goal": goal,
            "app_name": app_name,
            "intent": intent,
            "entities": entities,
            "required_actions": [action.value for action in ActionType],
        }
        response = client.responses.create(
            model=settings.openai_model,
            input=[
                {
                    "role": "system",
                    "content": "Return JSON with task_id, goal, app_name, intent, entities, steps[]. Each step must include id, description, action, target, params.",
                },
                {"role": "user", "content": json.dumps(prompt, ensure_ascii=False)},
            ],
            temperature=0.2,
        )
        text = response.output_text
        data = json.loads(text)
        return TaskDSL.model_validate(data)
