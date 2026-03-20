from __future__ import annotations

from .models import Element, OCRNode, ScreenPayload, UITextNode


class PerceptionFusion:
    def fuse(self, payload: ScreenPayload) -> list[Element]:
        elements: list[Element] = []
        for node in payload.ui_tree:
            elements.append(self._from_ui(node))
        for node in payload.ocr:
            if not any(node.text == existing.text for existing in elements):
                elements.append(self._from_ocr(node))
        return elements

    @staticmethod
    def _from_ui(node: UITextNode) -> Element:
        confidence = 0.95 if node.clickable else 0.75
        return Element(
            source="ui",
            text=node.text,
            resource_id=node.resourceId,
            bounds=node.bounds,
            clickable=node.clickable,
            class_name=node.className,
            confidence=confidence,
        )

    @staticmethod
    def _from_ocr(node: OCRNode) -> Element:
        return Element(
            source="ocr",
            text=node.text,
            position=[node.x, node.y],
            confidence=node.confidence,
            clickable=False,
        )
