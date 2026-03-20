from __future__ import annotations

from .config import settings
from .models import ActionType, DecisionResponse, DecisionState, DeviceAction


class DecisionEngine:
    def decide(self, state: DecisionState) -> DecisionResponse:
        if state.current_step is None:
            return DecisionResponse(
                action=DeviceAction(action=ActionType.WAIT, duration_ms=1000),
                reason="No remaining steps; waiting for orchestrator update.",
                confidence=0.2,
            )

        target = (state.current_step.target or state.goal).lower()

        for node in state.ui_tree:
            if self._matches(target, node.text, node.resourceId):
                return DecisionResponse(
                    action=DeviceAction(
                        action=state.current_step.action,
                        target=node.text or node.resourceId,
                        coordinates=self._center(node.bounds),
                    ),
                    reason="Matched target in UI tree with highest priority.",
                    confidence=0.92,
                )

        for node in state.ocr:
            if target in node.text.lower() and node.confidence >= settings.ocr_threshold:
                return DecisionResponse(
                    action=DeviceAction(
                        action=ActionType.TAP,
                        target=node.text,
                        coordinates=[node.x, node.y],
                    ),
                    reason="UI tree missed target, using OCR fallback.",
                    confidence=0.74,
                    used_fallback=True,
                )

        fallback_action = self._fallback(state)
        return DecisionResponse(
            action=fallback_action,
            reason="No reliable match; executing self-healing fallback.",
            confidence=0.4,
            used_fallback=True,
        )

    @staticmethod
    def _matches(target: str, text: str | None, resource_id: str | None) -> bool:
        haystacks = [value.lower() for value in [text, resource_id] if value]
        return any(target in haystack or haystack in target for haystack in haystacks)

    @staticmethod
    def _center(bounds: list[int] | None) -> list[int] | None:
        if not bounds or len(bounds) != 4:
            return None
        x1, y1, x2, y2 = bounds
        return [(x1 + x2) // 2, (y1 + y2) // 2]

    @staticmethod
    def _fallback(state: DecisionState) -> DeviceAction:
        last_result = (state.last_result or "").lower()
        if "timeout" in last_result:
            return DeviceAction(action=ActionType.WAIT, duration_ms=2000)
        if state.history and state.history[-1] == ActionType.SWIPE.value:
            return DeviceAction(action=ActionType.BACK)
        return DeviceAction(action=ActionType.SWIPE, coordinates=[500, 1600, 500, 400])
