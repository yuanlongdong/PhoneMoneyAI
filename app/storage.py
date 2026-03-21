from __future__ import annotations

import json
import re
import sqlite3
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator

from .models import FeedbackLog, MemoryKind, MemoryRecord, MemorySearchHit, TaskRecord, TaskStatus, TaskStep

DB_PATH = Path("phonemoneyai.db")
TOKEN_PATTERN = re.compile(r"[\w\u4e00-\u9fff]+")
CJK_TOKEN_PATTERN = re.compile(r"[\u4e00-\u9fff]{2,}")


def init_db() -> None:
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS tasks (
                task_id TEXT PRIMARY KEY,
                goal TEXT NOT NULL,
                app_name TEXT,
                intent TEXT NOT NULL,
                entities_json TEXT NOT NULL,
                status TEXT NOT NULL,
                current_step_index INTEGER NOT NULL,
                steps_json TEXT NOT NULL,
                retry_count INTEGER NOT NULL,
                max_retries INTEGER NOT NULL,
                last_error TEXT,
                history_json TEXT NOT NULL
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS feedback_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                task_id TEXT NOT NULL,
                step_id TEXT,
                action TEXT,
                result TEXT NOT NULL,
                screenshot_path TEXT,
                error_category TEXT,
                ocr_summary TEXT,
                ui_snapshot_json TEXT,
                ocr_snapshot_json TEXT
            )
            """
        )
        _ensure_column(conn, "feedback_logs", "error_category", "error_category TEXT")
        _ensure_column(conn, "feedback_logs", "ocr_summary", "ocr_summary TEXT")
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS memory_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                task_id TEXT NOT NULL,
                kind TEXT NOT NULL,
                goal TEXT NOT NULL,
                current_step_id TEXT,
                payload_json TEXT NOT NULL
            )
            """
        )
        conn.commit()


def _ensure_column(conn: sqlite3.Connection, table: str, column: str, ddl: str) -> None:
    columns = {row[1] for row in conn.execute(f"PRAGMA table_info({table})").fetchall()}
    if column not in columns:
        conn.execute(f"ALTER TABLE {table} ADD COLUMN {ddl}")


@contextmanager
def get_conn() -> Iterator[sqlite3.Connection]:
    conn = sqlite3.connect(DB_PATH)
    try:
        yield conn
    finally:
        conn.close()


class TaskRepository:
    def __init__(self) -> None:
        init_db()

    def save(self, record: TaskRecord) -> TaskRecord:
        with get_conn() as conn:
            conn.execute(
                """
                REPLACE INTO tasks(
                    task_id, goal, app_name, intent, entities_json, status, current_step_index,
                    steps_json, retry_count, max_retries, last_error, history_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    record.task_id,
                    record.goal,
                    record.app_name,
                    record.intent,
                    json.dumps(record.entities, ensure_ascii=False),
                    record.status.value,
                    record.current_step_index,
                    json.dumps([step.model_dump() for step in record.steps], ensure_ascii=False),
                    record.retry_count,
                    record.max_retries,
                    record.last_error,
                    json.dumps(record.history, ensure_ascii=False),
                ),
            )
            conn.commit()
        return record

    def get(self, task_id: str) -> TaskRecord | None:
        with get_conn() as conn:
            row = conn.execute(
                """
                SELECT task_id, goal, app_name, intent, entities_json, status, current_step_index,
                       steps_json, retry_count, max_retries, last_error, history_json
                FROM tasks WHERE task_id = ?
                """,
                (task_id,),
            ).fetchone()
        if row is None:
            return None
        return self._row_to_task(row)

    def list_all(self) -> list[TaskRecord]:
        with get_conn() as conn:
            rows = conn.execute(
                """
                SELECT task_id, goal, app_name, intent, entities_json, status, current_step_index,
                       steps_json, retry_count, max_retries, last_error, history_json
                FROM tasks ORDER BY rowid DESC
                """
            ).fetchall()
        return [self._row_to_task(row) for row in rows]

    def append_feedback(self, log: FeedbackLog) -> FeedbackLog:
        with get_conn() as conn:
            conn.execute(
                """
                INSERT INTO feedback_logs(task_id, step_id, action, result, screenshot_path, error_category, ocr_summary, ui_snapshot_json, ocr_snapshot_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    log.task_id,
                    log.step_id,
                    log.action,
                    log.result,
                    log.screenshot_path,
                    log.error_category,
                    log.ocr_summary,
                    json.dumps(log.ui_snapshot, ensure_ascii=False),
                    json.dumps(log.ocr_snapshot, ensure_ascii=False),
                ),
            )
            conn.commit()
        return log

    def append_memory(self, record: MemoryRecord) -> MemoryRecord:
        with get_conn() as conn:
            cursor = conn.execute(
                """
                INSERT INTO memory_records(task_id, kind, goal, current_step_id, payload_json)
                VALUES (?, ?, ?, ?, ?)
                """,
                (
                    record.task_id,
                    record.kind.value,
                    record.goal,
                    record.current_step_id,
                    json.dumps(record.payload, ensure_ascii=False),
                ),
            )
            conn.commit()
        record.id = cursor.lastrowid
        return record

    def list_memories(self) -> list[MemoryRecord]:
        with get_conn() as conn:
            rows = conn.execute(
                "SELECT id, task_id, kind, goal, current_step_id, payload_json FROM memory_records ORDER BY id DESC"
            ).fetchall()
        return [self._row_to_memory(row) for row in rows]

    def search_memories(
        self, query: str | None = None, kind: MemoryKind | None = None, limit: int = 10
    ) -> list[MemorySearchHit]:
        records = self.list_memories()
        if kind is not None:
            records = [record for record in records if record.kind == kind]
        if not query:
            return [MemorySearchHit(record=record, score=0.0) for record in records[:limit]]

        query_text = query.strip().lower()
        query_terms = _expand_terms(query_text)
        ranked: list[MemorySearchHit] = []
        for record in records:
            hit = _score_record(record, query_text, query_terms)
            if hit is not None:
                ranked.append(hit)
        ranked.sort(
            key=lambda item: (
                item.score,
                1 if item.record.kind == MemoryKind.FAILURE_CASE else 0,
                item.record.id or 0,
            ),
            reverse=True,
        )
        return ranked[:limit]

    @staticmethod
    def _row_to_memory(row: sqlite3.Row | tuple) -> MemoryRecord:
        return MemoryRecord(
            id=row[0],
            task_id=row[1],
            kind=MemoryKind(row[2]),
            goal=row[3],
            current_step_id=row[4],
            payload=json.loads(row[5]),
        )

    @staticmethod
    def _row_to_task(row: sqlite3.Row | tuple) -> TaskRecord:
        return TaskRecord(
            task_id=row[0],
            goal=row[1],
            app_name=row[2],
            intent=row[3],
            entities=json.loads(row[4]),
            status=TaskStatus(row[5]),
            current_step_index=row[6],
            steps=[TaskStep.model_validate(step) for step in json.loads(row[7])],
            retry_count=row[8],
            max_retries=row[9],
            last_error=row[10],
            history=json.loads(row[11]),
        )


def _score_record(record: MemoryRecord, query_text: str, query_terms: set[str]) -> MemorySearchHit | None:
    payload_text = json.dumps(record.payload, ensure_ascii=False).lower()
    field_texts = {
        "goal": record.goal.lower(),
        "step": (record.current_step_id or "").lower(),
        "payload": payload_text,
        "kind": record.kind.value.lower(),
        "task": record.task_id.lower(),
    }
    field_weights = {
        "goal": 5.0,
        "step": 4.0,
        "payload": 3.0,
        "kind": 2.0,
        "task": 1.0,
    }

    score = 0.0
    matched_terms: set[str] = set()
    reasons: list[str] = []

    for field_name, field_text in field_texts.items():
        if not field_text:
            continue
        if query_text in field_text:
            bonus = field_weights[field_name] * 2.5
            score += bonus
            reasons.append(f"完整短语命中 {field_name} (+{bonus:.1f})")
        field_terms = _expand_terms(field_text)
        overlap = sorted(query_terms & field_terms)
        if overlap:
            matched_terms.update(overlap)
            token_score = len(overlap) * field_weights[field_name]
            score += token_score
            reasons.append(f"关键词命中 {field_name}: {', '.join(overlap[:5])} (+{token_score:.1f})")
        for term in query_terms:
            if len(term) < 2 or term in overlap:
                continue
            if term in field_text:
                partial_bonus = field_weights[field_name] * 0.5
                score += partial_bonus
                matched_terms.add(term)
                reasons.append(f"子串命中 {field_name}: {term} (+{partial_bonus:.1f})")

    history = record.payload.get("history")
    if isinstance(history, list):
        history_hits = sum(1 for entry in history if query_text in str(entry).lower())
        if history_hits:
            history_bonus = history_hits * 1.5
            score += history_bonus
            reasons.append(f"历史轨迹命中 {history_hits} 次 (+{history_bonus:.1f})")

    if score <= 0:
        return None

    return MemorySearchHit(
        record=record,
        score=round(score, 2),
        matched_terms=sorted(matched_terms),
        reasons=reasons[:6],
    )


def _expand_terms(text: str) -> set[str]:
    normalized = text.lower().strip()
    if not normalized:
        return set()
    terms = {match.group(0) for match in TOKEN_PATTERN.finditer(normalized)}
    for token in list(terms):
        if len(token) > 4:
            terms.update(part for part in re.split(r"[_\-:/\\.]+", token) if len(part) >= 2)
    for match in CJK_TOKEN_PATTERN.finditer(normalized):
        token = match.group(0)
        if len(token) <= 4:
            terms.add(token)
            continue
        terms.update(token[index : index + 2] for index in range(len(token) - 1))
    return {term for term in terms if term}
