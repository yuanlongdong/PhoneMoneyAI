from __future__ import annotations

from enum import Enum
from typing import Any, Literal

from pydantic import BaseModel, Field, model_validator


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


class MemoryKind(str, Enum):
    SUCCESS_PATH = "success_path"
    FAILURE_CASE = "failure_case"


class TaskStep(BaseModel):
    id: str
    description: str
    action: ActionType
    target: str | None = None
    params: dict[str, Any] = Field(default_factory=dict)


class TaskDSL(BaseModel):
    task_id: str
    goal: str
    app_name: str | None = None
    intent: str
    entities: dict[str, Any] = Field(default_factory=dict)
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


class Element(BaseModel):
    source: Literal["ui", "ocr"]
    text: str | None = None
    resource_id: str | None = None
    bounds: list[int] | None = None
    position: list[int] | None = None
    clickable: bool = False
    class_name: str | None = None
    confidence: float = 0.0

    def center(self) -> list[int] | None:
        if self.bounds and len(self.bounds) == 4:
            x1, y1, x2, y2 = self.bounds
            return [(x1 + x2) // 2, (y1 + y2) // 2]
        return self.position


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
    screen_width: int = 1080
    screen_height: int = 1920


class DecisionCandidate(BaseModel):
    element: Element
    score: float
    reasons: list[str] = Field(default_factory=list)


class DecisionResponse(BaseModel):
    action: DeviceAction
    reason: str
    confidence: float = Field(ge=0.0, le=1.0)
    used_fallback: bool = False
    candidates: list[DecisionCandidate] = Field(default_factory=list)


class ExecutionRequest(BaseModel):
    device_id: str = "emulator-5554"
    action: DeviceAction
    dry_run: bool = True
    capture_screenshot: bool = False
    screenshot_dir: str = "artifacts/executor"
    verify_receipt: bool = False
    cleanup_screenshots: bool = False
    keep_latest: int = 20


class ExecutionResult(BaseModel):
    success: bool
    command: str
    stdout: str = ""
    stderr: str = ""
    screenshot_path: str | None = None
    receipt: dict[str, Any] = Field(default_factory=dict)


class CreateTaskRequest(BaseModel):
    goal: str
    app_name: str | None = None
    context: dict[str, Any] = Field(default_factory=dict)


class TaskRecord(BaseModel):
    task_id: str
    goal: str
    app_name: str | None = None
    intent: str
    entities: dict[str, Any] = Field(default_factory=dict)
    status: TaskStatus
    current_step_index: int = 0
    steps: list[TaskStep]
    retry_count: int = 0
    max_retries: int = 3
    last_error: str | None = None
    history: list[str] = Field(default_factory=list)

    @property
    def current_step(self) -> TaskStep | None:
        if 0 <= self.current_step_index < len(self.steps):
            return self.steps[self.current_step_index]
        return None


class TaskUpdateRequest(BaseModel):
    status: TaskStatus | None = None
    current_step_index: int | None = None
    retry_count: int | None = None
    last_error: str | None = None
    history_entry: str | None = None


class StepResultRequest(BaseModel):
    success: bool
    error_type: str | None = None
    message: str | None = None
    screenshot_path: str | None = None


class FeedbackLog(BaseModel):
    task_id: str
    step_id: str | None = None
    action: str | None = None
    result: str
    screenshot_path: str | None = None
    error_category: str | None = None
    ocr_summary: str | None = None
    ui_snapshot: dict[str, Any] | None = None
    ocr_snapshot: dict[str, Any] | None = None


class MemoryRecord(BaseModel):
    id: int | None = None
    task_id: str
    kind: MemoryKind
    goal: str
    current_step_id: str | None = None
    payload: dict[str, Any] = Field(default_factory=dict)


class MemorySearchHit(BaseModel):
    record: MemoryRecord
    score: float = Field(ge=0.0)
    matched_terms: list[str] = Field(default_factory=list)
    reasons: list[str] = Field(default_factory=list)


class MemorySearchResponse(BaseModel):
    items: list[MemorySearchHit]
    query: str | None = None
    kind: MemoryKind | None = None


class HealthResponse(BaseModel):
    status: Literal["ok"] = "ok"


class NextStepResponse(BaseModel):
    task: TaskRecord
    state: DecisionState


class DirectPlanResponse(BaseModel):
    dsl: TaskDSL


class ValidateActionRequest(BaseModel):
    state: DecisionState
    action: DeviceAction


class ValidateActionResponse(BaseModel):
    valid: bool
    reasons: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def ensure_reason_for_invalid(self) -> "ValidateActionResponse":
        if not self.valid and not self.reasons:
            self.reasons.append("Unknown validation error")
        return self
