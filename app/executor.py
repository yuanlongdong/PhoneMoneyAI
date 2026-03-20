from __future__ import annotations

import shlex
import subprocess
from datetime import datetime, timezone
from pathlib import Path

from .config import settings
from .models import ActionType, ExecutionRequest, ExecutionResult


class ADBExecutor:
    def execute(self, request: ExecutionRequest) -> ExecutionResult:
        command = self._build_command(request)
        receipt = {
            "device_id": request.device_id,
            "action": request.action.action.value,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "dry_run": request.dry_run,
        }
        screenshot_path: str | None = None

        if request.dry_run:
            if request.capture_screenshot:
                screenshot_path = str(Path(request.screenshot_dir) / f"{request.device_id}-dry-run.png")
            if request.cleanup_screenshots:
                receipt["cleanup_removed"] = self._cleanup_screenshots(request)
            receipt["verified"] = not request.verify_receipt
            return ExecutionResult(success=True, command=command, stdout="dry-run", screenshot_path=screenshot_path, receipt=receipt)

        completed = subprocess.run(command, shell=True, capture_output=True, text=True, timeout=settings.action_timeout_seconds)
        if completed.returncode == 0 and request.capture_screenshot:
            screenshot_path = self._capture_screenshot(request)
        if request.cleanup_screenshots:
            receipt["cleanup_removed"] = self._cleanup_screenshots(request, keep_path=screenshot_path)
        if request.verify_receipt:
            receipt["verified"] = self._verify_receipt(request, screenshot_path)
        return ExecutionResult(
            success=completed.returncode == 0,
            command=command,
            stdout=completed.stdout,
            stderr=completed.stderr,
            screenshot_path=screenshot_path,
            receipt=receipt | {"returncode": completed.returncode},
        )

    def _verify_receipt(self, request: ExecutionRequest, screenshot_path: str | None) -> bool:
        adb = shlex.quote(settings.adb_path)
        device = shlex.quote(request.device_id)
        state = subprocess.run(
            f"{adb} -s {device} get-state",
            shell=True,
            capture_output=True,
            text=True,
            timeout=settings.action_timeout_seconds,
        )
        device_ready = state.returncode == 0 and state.stdout.strip() == "device"
        screenshot_ok = screenshot_path is None or Path(screenshot_path).exists()
        return device_ready and screenshot_ok

    def _cleanup_screenshots(self, request: ExecutionRequest, keep_path: str | None = None) -> int:
        screenshot_dir = Path(request.screenshot_dir)
        screenshot_dir.mkdir(parents=True, exist_ok=True)
        screenshots = sorted(screenshot_dir.glob("*.png"), key=lambda path: path.stat().st_mtime, reverse=True)
        removed = 0
        keep_latest = max(request.keep_latest, 0)
        for path in screenshots[keep_latest:]:
            if keep_path and Path(keep_path) == path:
                continue
            path.unlink(missing_ok=True)
            removed += 1
        return removed

    def _capture_screenshot(self, request: ExecutionRequest) -> str:
        screenshot_dir = Path(request.screenshot_dir)
        screenshot_dir.mkdir(parents=True, exist_ok=True)
        screenshot_path = screenshot_dir / f"{request.device_id}-{datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S')}.png"
        adb = shlex.quote(settings.adb_path)
        device = shlex.quote(request.device_id)
        command = f"{adb} -s {device} exec-out screencap -p > {shlex.quote(str(screenshot_path))}"
        subprocess.run(command, shell=True, capture_output=True, text=True, timeout=settings.action_timeout_seconds)
        return str(screenshot_path)

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
