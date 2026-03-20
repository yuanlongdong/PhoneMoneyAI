from __future__ import annotations

import json
import sqlite3
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator

from .models import TaskRecord, TaskStatus, TaskStep

DB_PATH = Path("phonemoneyai.db")


def init_db() -> None:
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS tasks (
                task_id TEXT PRIMARY KEY,
                goal TEXT NOT NULL,
                status TEXT NOT NULL,
                current_step_index INTEGER NOT NULL,
                steps_json TEXT NOT NULL
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
                "REPLACE INTO tasks(task_id, goal, status, current_step_index, steps_json) VALUES (?, ?, ?, ?, ?)",
                (
                    record.task_id,
                    record.goal,
                    record.status.value,
                    record.current_step_index,
                    json.dumps([step.model_dump() for step in record.steps]),
                ),
            )
            conn.commit()
        return record

    def get(self, task_id: str) -> TaskRecord | None:
        with get_conn() as conn:
            row = conn.execute(
                "SELECT task_id, goal, status, current_step_index, steps_json FROM tasks WHERE task_id = ?",
                (task_id,),
            ).fetchone()
        if row is None:
            return None
        return TaskRecord(
            task_id=row[0],
            goal=row[1],
            status=TaskStatus(row[2]),
            current_step_index=row[3],
            steps=[TaskStep.model_validate(step) for step in json.loads(row[4])],
        )

    def list_all(self) -> list[TaskRecord]:
        with get_conn() as conn:
            rows = conn.execute(
                "SELECT task_id, goal, status, current_step_index, steps_json FROM tasks ORDER BY rowid DESC"
            ).fetchall()
        return [
            TaskRecord(
                task_id=row[0],
                goal=row[1],
                status=TaskStatus(row[2]),
                current_step_index=row[3],
                steps=[TaskStep.model_validate(step) for step in json.loads(row[4])],
            )
            for row in rows
        ]
