from __future__ import annotations

from enum import Enum
from typing import Any, Literal

from pydantic import BaseModel, Field


class TaskStatus(str, Enum):
    PENDING = "pending"
    RUNNING = "running"
    RETRY = "retry"
    SUCCESS = "success"
    FAIL = "fail"


class ActionType(str, Enum):
    TAP = "tap"
    INPUT = "input"
    SWIPE = "swipe"
    BACK = "back"
    OPEN_APP = "open_app"
    WAIT = "wait"


class TaskStep(BaseModel):
    id: str
    description: str
    action: ActionType
    target: str | None = None
    params: dict[str, Any] = Field(default_factory=dict)


class TaskDSL(BaseModel):
    task_id: str
    goal: str
    steps: list[TaskStep]


class UITextNode(BaseModel):
    text: str | None = None
    resourceId: str | None = None
    bounds: list[int] | None = None
    clickable: bool = False
    className: str | None = None


class OCRNode(BaseModel):
    text: str
    x: int
    y: int
    confidence: float


class ScreenPayload(BaseModel):
    task_id: str | None = None
    ui_tree: list[UITextNode] = Field(default_factory=list)
    ocr: list[OCRNode] = Field(default_factory=list)


class DeviceAction(BaseModel):
    action: ActionType
    target: str | None = None
    coordinates: list[int] | None = None
    text: str | None = None
    package_name: str | None = None
    duration_ms: int | None = None


class DecisionState(BaseModel):
    goal: str
    current_step: TaskStep | None = None
    ui_tree: list[UITextNode] = Field(default_factory=list)
    ocr: list[OCRNode] = Field(default_factory=list)
    history: list[str] = Field(default_factory=list)
    last_action: DeviceAction | None = None
    last_result: str | None = None


class DecisionResponse(BaseModel):
    action: DeviceAction
    reason: str
    confidence: float = Field(ge=0.0, le=1.0)
    used_fallback: bool = False


class ExecutionRequest(BaseModel):
    device_id: str = "emulator-5554"
    action: DeviceAction
    dry_run: bool = True


class ExecutionResult(BaseModel):
    success: bool
    command: str
    stdout: str = ""
    stderr: str = ""


class CreateTaskRequest(BaseModel):
    goal: str
    app_name: str | None = None
    context: dict[str, Any] = Field(default_factory=dict)


class TaskRecord(BaseModel):
    task_id: str
    goal: str
    status: TaskStatus
    current_step_index: int = 0
    steps: list[TaskStep]


class TaskUpdateRequest(BaseModel):
    status: TaskStatus | None = None
    current_step_index: int | None = None


class HealthResponse(BaseModel):
    status: Literal["ok"] = "ok"
