from __future__ import annotations

import json
import sqlite3
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator

from .models import FeedbackLog, TaskRecord, TaskStatus, TaskStep

DB_PATH = Path("phonemoneyai.db")


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
                ui_snapshot_json TEXT,
                ocr_snapshot_json TEXT
            )
            """
        )
        conn.commit()


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
                INSERT INTO feedback_logs(task_id, step_id, action, result, screenshot_path, ui_snapshot_json, ocr_snapshot_json)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    log.task_id,
                    log.step_id,
                    log.action,
                    log.result,
                    log.screenshot_path,
                    json.dumps(log.ui_snapshot, ensure_ascii=False),
                    json.dumps(log.ocr_snapshot, ensure_ascii=False),
                ),
            )
            conn.commit()
        return log

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
