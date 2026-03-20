from __future__ import annotations

from .config import settings
from .models import ActionType, DecisionCandidate, DecisionResponse, DecisionState, DeviceAction, Element, ScreenPayload
from .perception import PerceptionFusion


class ActionValidator:
    def validate(self, state: DecisionState, action: DeviceAction) -> list[str]:
        reasons: list[str] = []
        if action.coordinates:
            if len(action.coordinates) not in {2, 4}:
                reasons.append("Coordinates must contain 2 or 4 integers.")
            for index, value in enumerate(action.coordinates[:2]):
                limit = state.screen_width if index == 0 else state.screen_height
                if value < 0 or value > limit:
                    axis = "x" if index == 0 else "y"
                    reasons.append(f"{axis} coordinate {value} out of range.")
        if state.last_action == action and action.action != ActionType.WAIT:
            reasons.append("Repeated identical action blocked.")
        return reasons


class DecisionEngine:
    def __init__(self) -> None:
        self.perception = PerceptionFusion()
        self.validator = ActionValidator()

    def decide(self, state: DecisionState) -> DecisionResponse:
        if state.current_step is None:
            return DecisionResponse(
                action=DeviceAction(action=ActionType.WAIT, duration_ms=1000),
                reason="No remaining steps; waiting for orchestrator update.",
                confidence=0.2,
            )

        candidates = self._score_candidates(state)
        if candidates:
            best = candidates[0]
            action = DeviceAction(
                action=state.current_step.action,
                target=best.element.text or best.element.resource_id,
                coordinates=best.element.center(),
                text=state.current_step.params.get("text"),
            )
            validation_errors = self.validator.validate(state, action)
            if not validation_errors:
                return DecisionResponse(
                    action=action,
                    reason="Best scored candidate selected from fused UI/OCR elements.",
                    confidence=min(best.score, 1.0),
                    candidates=candidates[:5],
                )

        fallback_action = self._fallback(state)
        fallback_errors = self.validator.validate(state, fallback_action)
        if fallback_errors:
            fallback_action = DeviceAction(action=ActionType.WAIT, duration_ms=1500)
        return DecisionResponse(
            action=fallback_action,
            reason="No valid candidate survived validation; executing self-healing fallback.",
            confidence=0.35,
            used_fallback=True,
            candidates=candidates[:5] if candidates else [],
        )

    def validate(self, state: DecisionState, action: DeviceAction) -> list[str]:
        return self.validator.validate(state, action)

    def _score_candidates(self, state: DecisionState) -> list[DecisionCandidate]:
        target = (state.current_step.target or state.goal).lower()
        elements = self.perception.fuse(ScreenPayload(ui_tree=state.ui_tree, ocr=state.ocr))
        candidates: list[DecisionCandidate] = []
        for element in elements:
            score, reasons = self._score_element(target, element)
            if score > 0:
                candidates.append(DecisionCandidate(element=element, score=score, reasons=reasons))
        candidates.sort(key=lambda item: item.score, reverse=True)
        return candidates

    @staticmethod
    def _score_element(target: str, element: Element) -> tuple[float, list[str]]:
        score = 0.0
        reasons: list[str] = []
        haystacks = [value.lower() for value in [element.text, element.resource_id] if value]
        if any(target == hay for hay in haystacks):
            score += 0.55
            reasons.append("exact-text-match")
        elif any(target in hay or hay in target for hay in haystacks):
            score += 0.35
            reasons.append("fuzzy-text-match")
        if element.clickable:
            score += 0.2
            reasons.append("clickable")
        if element.source == "ui":
            score += settings.ui_priority * 0.2
            reasons.append("ui-priority")
        score += min(element.confidence, 1.0) * 0.05
        return score, reasons

    @staticmethod
    def _fallback(state: DecisionState) -> DeviceAction:
        last_result = (state.last_result or "").lower()
        if "timeout" in last_result:
            return DeviceAction(action=ActionType.WAIT, duration_ms=2000)
        if state.history and state.history[-1] == ActionType.SWIPE.value:
            return DeviceAction(action=ActionType.BACK)
        return DeviceAction(action=ActionType.SWIPE, coordinates=[500, 1600, 500, 400])
