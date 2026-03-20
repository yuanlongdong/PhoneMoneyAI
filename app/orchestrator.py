from __future__ import annotations

from .models import CreateTaskRequest, TaskRecord, TaskStatus
from .planner import Planner
from .storage import TaskRepository


class Orchestrator:
    def __init__(self, repository: TaskRepository | None = None, planner: Planner | None = None) -> None:
        self.repository = repository or TaskRepository()
        self.planner = planner or Planner()

    def create_task(self, request: CreateTaskRequest) -> TaskRecord:
        dsl = self.planner.create_task(request)
        record = TaskRecord(task_id=dsl.task_id, goal=dsl.goal, status=TaskStatus.PENDING, steps=dsl.steps)
        return self.repository.save(record)

    def update_status(self, task_id: str, status: TaskStatus | None = None, current_step_index: int | None = None) -> TaskRecord | None:
        record = self.repository.get(task_id)
        if record is None:
            return None
        if status is not None:
            record.status = status
        if current_step_index is not None:
            record.current_step_index = current_step_index
        return self.repository.save(record)
