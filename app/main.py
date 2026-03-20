from __future__ import annotations

from fastapi import FastAPI, HTTPException

from .decision import DecisionEngine
from .executor import ADBExecutor
from .models import (
    CreateTaskRequest,
    DecisionResponse,
    DecisionState,
    DirectPlanResponse,
    ExecutionRequest,
    ExecutionResult,
    FeedbackLog,
    HealthResponse,
    NextStepResponse,
    ScreenPayload,
    StepResultRequest,
    TaskRecord,
    TaskUpdateRequest,
    ValidateActionRequest,
    ValidateActionResponse,
)
from .orchestrator import Orchestrator
from .perception import PerceptionFusion

app = FastAPI(title="PhoneMoneyAI", version="0.2.0")

orchestrator = Orchestrator()
decision_engine = DecisionEngine()
executor = ADBExecutor()
perception = PerceptionFusion()


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse()


@app.post("/plan", response_model=DirectPlanResponse)
def plan(request: CreateTaskRequest) -> DirectPlanResponse:
    return DirectPlanResponse(dsl=orchestrator.planner.preview_plan(request))


@app.post("/task", response_model=TaskRecord)
def create_task(request: CreateTaskRequest) -> TaskRecord:
    return orchestrator.create_task(request)


@app.get("/task", response_model=list[TaskRecord])
def list_tasks() -> list[TaskRecord]:
    return orchestrator.repository.list_all()


@app.get("/task/{task_id}/next", response_model=NextStepResponse)
def next_task_step(task_id: str) -> NextStepResponse:
    record = orchestrator.repository.get(task_id)
    state = orchestrator.build_decision_state(task_id)
    if record is None or state is None:
        raise HTTPException(status_code=404, detail="Task not found")
    return NextStepResponse(task=record, state=state)


@app.patch("/task/{task_id}", response_model=TaskRecord)
def update_task(task_id: str, request: TaskUpdateRequest) -> TaskRecord:
    record = orchestrator.update_status(task_id, request)
    if record is None:
        raise HTTPException(status_code=404, detail="Task not found")
    return record


@app.post("/task/{task_id}/result", response_model=TaskRecord)
def apply_task_result(task_id: str, request: StepResultRequest) -> TaskRecord:
    record = orchestrator.apply_step_result(task_id, request)
    if record is None:
        raise HTTPException(status_code=404, detail="Task not found")
    return record


@app.post("/feedback", response_model=FeedbackLog)
def feedback(log: FeedbackLog) -> FeedbackLog:
    return orchestrator.log_feedback(log)


@app.post("/screen")
def screen(payload: ScreenPayload) -> dict:
    return {"elements": [element.model_dump() for element in perception.fuse(payload)]}


@app.post("/decide", response_model=DecisionResponse)
def decide(state: DecisionState) -> DecisionResponse:
    return decision_engine.decide(state)


@app.post("/validate", response_model=ValidateActionResponse)
def validate_action(request: ValidateActionRequest) -> ValidateActionResponse:
    reasons = decision_engine.validate(request.state, request.action)
    return ValidateActionResponse(valid=not reasons, reasons=reasons)


@app.post("/execute", response_model=ExecutionResult)
def execute(request: ExecutionRequest) -> ExecutionResult:
    try:
        return executor.execute(request)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
