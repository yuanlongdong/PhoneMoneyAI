from __future__ import annotations

import uuid

from .models import ActionType, CreateTaskRequest, TaskDSL, TaskStep


class Planner:
    def create_task(self, request: CreateTaskRequest) -> TaskDSL:
        goal = request.goal.strip()
        app_name = request.app_name or "target app"
        steps = [
            TaskStep(id="step-1", description=f"Open {app_name}", action=ActionType.OPEN_APP, target=app_name),
            TaskStep(id="step-2", description="Find target UI element", action=ActionType.TAP, target=goal),
            TaskStep(id="step-3", description="Wait for result", action=ActionType.WAIT, params={"duration_ms": 1500}),
        ]
        return TaskDSL(task_id=f"task-{uuid.uuid4().hex[:12]}", goal=goal, steps=steps)
