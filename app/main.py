from __future__ import annotations

from fastapi import FastAPI, HTTPException

from .decision import DecisionEngine
from .executor import ADBExecutor
from .models import (
    CreateTaskRequest,
    DecisionResponse,
    DecisionState,
    ExecutionRequest,
    ExecutionResult,
    HealthResponse,
    ScreenPayload,
    TaskRecord,
    TaskUpdateRequest,
)
from .orchestrator import Orchestrator
from .perception import PerceptionFusion

app = FastAPI(title="PhoneMoneyAI", version="0.1.0")

orchestrator = Orchestrator()
decision_engine = DecisionEngine()
executor = ADBExecutor()
perception = PerceptionFusion()


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse()


@app.post("/task", response_model=TaskRecord)
def create_task(request: CreateTaskRequest) -> TaskRecord:
    return orchestrator.create_task(request)


@app.get("/task", response_model=list[TaskRecord])
def list_tasks() -> list[TaskRecord]:
    return orchestrator.repository.list_all()


@app.patch("/task/{task_id}", response_model=TaskRecord)
def update_task(task_id: str, request: TaskUpdateRequest) -> TaskRecord:
    record = orchestrator.update_status(task_id, request.status, request.current_step_index)
    if record is None:
        raise HTTPException(status_code=404, detail="Task not found")
    return record


@app.post("/screen")
def screen(payload: ScreenPayload) -> dict:
    return {"elements": perception.fuse(payload)}


@app.post("/decide", response_model=DecisionResponse)
def decide(state: DecisionState) -> DecisionResponse:
    return decision_engine.decide(state)


@app.post("/execute", response_model=ExecutionResult)
def execute(request: ExecutionRequest) -> ExecutionResult:
    try:
        return executor.execute(request)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
