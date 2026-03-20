from __future__ import annotations

from .config import settings
from .models import CreateTaskRequest, DecisionState, FeedbackLog, StepResultRequest, TaskRecord, TaskStatus, TaskUpdateRequest
from .planner import Planner
from .storage import TaskRepository


class Orchestrator:
    def __init__(self, repository: TaskRepository | None = None, planner: Planner | None = None) -> None:
        self.repository = repository or TaskRepository()
        self.planner = planner or Planner()

    def create_task(self, request: CreateTaskRequest) -> TaskRecord:
        dsl = self.planner.create_task(request)
        record = TaskRecord(
            task_id=dsl.task_id,
            goal=dsl.goal,
            app_name=dsl.app_name,
            intent=dsl.intent,
            entities=dsl.entities,
            status=TaskStatus.PENDING,
            steps=dsl.steps,
            max_retries=settings.retry_limit,
        )
        return self.repository.save(record)

    def update_status(self, task_id: str, request: TaskUpdateRequest) -> TaskRecord | None:
        record = self.repository.get(task_id)
        if record is None:
            return None
        if request.status is not None:
            record.status = request.status
        if request.current_step_index is not None:
            record.current_step_index = request.current_step_index
        if request.retry_count is not None:
            record.retry_count = request.retry_count
        if request.last_error is not None:
            record.last_error = request.last_error
        if request.history_entry is not None:
            record.history.append(request.history_entry)
        return self.repository.save(record)

    def build_decision_state(self, task_id: str) -> DecisionState | None:
        record = self.repository.get(task_id)
        if record is None:
            return None
        return DecisionState(
            goal=record.goal,
            current_step=record.current_step,
            history=record.history,
            last_result=record.last_error,
        )

    def apply_step_result(self, task_id: str, result: StepResultRequest) -> TaskRecord | None:
        record = self.repository.get(task_id)
        if record is None:
            return None

        if result.success:
            record.retry_count = 0
            record.last_error = None
            record.history.append(f"step:{record.current_step_index}:success")
            if record.current_step_index + 1 >= len(record.steps):
                record.current_step_index = len(record.steps)
                record.status = TaskStatus.SUCCESS
            else:
                record.current_step_index += 1
                record.status = TaskStatus.RUNNING
        else:
            record.retry_count += 1
            record.last_error = result.message or result.error_type or "step_failed"
            record.history.append(f"step:{record.current_step_index}:fail:{record.last_error}")
            if record.retry_count >= record.max_retries:
                record.status = TaskStatus.FAIL
            else:
                record.status = TaskStatus.RETRY
        return self.repository.save(record)

    def log_feedback(self, log: FeedbackLog) -> FeedbackLog:
        return self.repository.append_feedback(log)
