from __future__ import annotations

from .models import OCRNode, ScreenPayload, UITextNode


class PerceptionFusion:
    def fuse(self, payload: ScreenPayload) -> list[dict]:
        elements: list[dict] = []
        for node in payload.ui_tree:
            elements.append(self._from_ui(node))
        for node in payload.ocr:
            if not any(node.text == existing.get("text") for existing in elements):
                elements.append(self._from_ocr(node))
        return elements

    @staticmethod
    def _from_ui(node: UITextNode) -> dict:
        return {
            "source": "ui",
            "text": node.text,
            "resource_id": node.resourceId,
            "bounds": node.bounds,
            "clickable": node.clickable,
            "class_name": node.className,
        }

    @staticmethod
    def _from_ocr(node: OCRNode) -> dict:
        return {
            "source": "ocr",
            "text": node.text,
            "position": [node.x, node.y],
            "confidence": node.confidence,
            "clickable": False,
        }
