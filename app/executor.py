from __future__ import annotations

import shlex
import subprocess

from .config import settings
from .models import ActionType, ExecutionRequest, ExecutionResult


class ADBExecutor:
    def execute(self, request: ExecutionRequest) -> ExecutionResult:
        command = self._build_command(request)
        if request.dry_run:
            return ExecutionResult(success=True, command=command, stdout="dry-run")

        completed = subprocess.run(command, shell=True, capture_output=True, text=True, timeout=settings.action_timeout_seconds)
        return ExecutionResult(
            success=completed.returncode == 0,
            command=command,
            stdout=completed.stdout,
            stderr=completed.stderr,
        )

    def _build_command(self, request: ExecutionRequest) -> str:
        device = shlex.quote(request.device_id)
        adb = shlex.quote(settings.adb_path)
        action = request.action

        if action.action == ActionType.TAP and action.coordinates:
            x, y = action.coordinates[:2]
            return f"{adb} -s {device} shell input tap {x} {y}"
        if action.action == ActionType.INPUT and action.text:
            return f"{adb} -s {device} shell input text {shlex.quote(action.text)}"
        if action.action == ActionType.SWIPE and action.coordinates and len(action.coordinates) == 4:
            x1, y1, x2, y2 = action.coordinates
            return f"{adb} -s {device} shell input swipe {x1} {y1} {x2} {y2}"
        if action.action == ActionType.BACK:
            return f"{adb} -s {device} shell input keyevent 4"
        if action.action == ActionType.OPEN_APP and action.target:
            return f"{adb} -s {device} shell monkey -p {shlex.quote(action.target)} -c android.intent.category.LAUNCHER 1"
        if action.action == ActionType.WAIT:
            duration = action.duration_ms or 1000
            return f"sleep {duration / 1000:.1f}"
        raise ValueError(f"Unsupported action payload: {action.model_dump()}")
