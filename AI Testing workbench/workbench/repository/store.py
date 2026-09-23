from __future__ import annotations

import json
import sqlite3
import threading
import time
import uuid
from pathlib import Path
from typing import Any


def _now() -> float:
    return time.time()


def _id(prefix: str) -> str:
    return f"{prefix}-{uuid.uuid4().hex[:12]}"


class WorkbenchStore:
    """Append-friendly SQLite store under .workbench/ — evaluation only."""

    def __init__(self, root: Path) -> None:
        root.mkdir(parents=True, exist_ok=True)
        self.path = root / "state.sqlite"
        self._lock = threading.RLock()
        self._conn = sqlite3.connect(str(self.path), check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        self._conn.execute("PRAGMA journal_mode=WAL")
        self._init()

    def _init(self) -> None:
        with self._lock:
            self._conn.executescript(
                """
                CREATE TABLE IF NOT EXISTS cases (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    data_classification TEXT NOT NULL,
                    source_type TEXT NOT NULL,
                    input_text TEXT NOT NULL,
                    fixture_tag TEXT,
                    context_json TEXT NOT NULL,
                    labels_json TEXT NOT NULL,
                    version INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    created_at REAL NOT NULL,
                    updated_at REAL NOT NULL
                );
                CREATE TABLE IF NOT EXISTS case_runs (
                    id TEXT PRIMARY KEY,
                    case_id TEXT NOT NULL,
                    mode TEXT NOT NULL,
                    status TEXT NOT NULL,
                    fixture_tag TEXT,
                    request_json TEXT NOT NULL,
                    trace_json TEXT NOT NULL,
                    proposal_json TEXT,
                    validation_json TEXT NOT NULL,
                    error_json TEXT,
                    usage_json TEXT,
                    clinical_write_claimed INTEGER NOT NULL DEFAULT 0,
                    created_at REAL NOT NULL,
                    finished_at REAL
                );
                CREATE TABLE IF NOT EXISTS human_reviews (
                    id TEXT PRIMARY KEY,
                    case_run_id TEXT NOT NULL,
                    operation_id TEXT,
                    decision TEXT NOT NULL,
                    corrected_expected TEXT,
                    comment TEXT,
                    reviewer TEXT NOT NULL,
                    created_at REAL NOT NULL
                );
                """
            )
            cols = {row[1] for row in self._conn.execute("PRAGMA table_info(case_runs)").fetchall()}
            if "usage_json" not in cols:
                self._conn.execute("ALTER TABLE case_runs ADD COLUMN usage_json TEXT")
            self._conn.commit()

    def reset(self) -> None:
        with self._lock:
            for table in ("human_reviews", "case_runs", "cases"):
                self._conn.execute(f"DELETE FROM {table}")
            self._conn.commit()

    def seed_if_empty(self, cases: list[dict[str, Any]]) -> None:
        if self.list_cases():
            return
        for case in cases:
            self.create_case(case)

    def list_cases(self) -> list[dict[str, Any]]:
        rows = self._conn.execute(
            "SELECT * FROM cases ORDER BY title ASC"
        ).fetchall()
        return [self._case_payload(row) for row in rows]

    def get_case(self, case_id: str) -> dict[str, Any] | None:
        row = self._conn.execute("SELECT * FROM cases WHERE id = ?", (case_id,)).fetchone()
        return self._case_payload(row) if row else None

    def create_case(self, body: dict[str, Any]) -> dict[str, Any]:
        classification = (body.get("dataClassification") or "SYNTHETIC").upper()
        if classification != "SYNTHETIC":
            raise ValueError("Only SYNTHETIC cases are allowed in the workbench.")
        case_id = body.get("id") or _id("case")
        now = _now()
        with self._lock:
            self._conn.execute(
                """
                INSERT INTO cases(
                    id, title, data_classification, source_type, input_text, fixture_tag,
                    context_json, labels_json, version, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 'ACTIVE', ?, ?)
                """,
                (
                    case_id,
                    body.get("title") or "Untitled case",
                    classification,
                    body.get("sourceType") or "DOCTOR_NOTE",
                    body.get("inputText") or "",
                    body.get("fixtureTag"),
                    json.dumps(body.get("context") or {}),
                    json.dumps(body.get("labels") or []),
                    now,
                    now,
                ),
            )
            self._conn.commit()
        return self.get_case(case_id)  # type: ignore[return-value]

    def save_run(self, run: dict[str, Any]) -> dict[str, Any]:
        run_id = run.get("id") or _id("run")
        now = _now()
        with self._lock:
            self._conn.execute(
                """
                INSERT INTO case_runs(
                    id, case_id, mode, status, fixture_tag, request_json, trace_json,
                    proposal_json, validation_json, error_json, usage_json,
                    clinical_write_claimed, created_at, finished_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """,
                (
                    run_id,
                    run["caseId"],
                    run.get("mode", "MOCK"),
                    run.get("status", "SUCCEEDED"),
                    run.get("fixtureTag"),
                    json.dumps(run.get("request") or {}),
                    json.dumps(run.get("trace") or []),
                    json.dumps(run.get("proposal")) if run.get("proposal") is not None else None,
                    json.dumps(run.get("validation") or {}),
                    json.dumps(run.get("error")) if run.get("error") is not None else None,
                    json.dumps(run.get("usage")) if run.get("usage") is not None else None,
                    now,
                    now,
                ),
            )
            self._conn.commit()
        return self.get_run(run_id)  # type: ignore[return-value]

    def get_run(self, run_id: str) -> dict[str, Any] | None:
        row = self._conn.execute("SELECT * FROM case_runs WHERE id = ?", (run_id,)).fetchone()
        if row is None:
            return None
        return self._run_payload(row)

    def add_review(self, case_run_id: str, body: dict[str, Any]) -> dict[str, Any]:
        if self.get_run(case_run_id) is None:
            raise KeyError("case run not found")
        review_id = _id("rev")
        now = _now()
        with self._lock:
            self._conn.execute(
                """
                INSERT INTO human_reviews(
                    id, case_run_id, operation_id, decision, corrected_expected,
                    comment, reviewer, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    review_id,
                    case_run_id,
                    body.get("operationId"),
                    body["decision"],
                    body.get("correctedExpected"),
                    body.get("comment"),
                    body.get("reviewer") or "evaluator",
                    now,
                ),
            )
            self._conn.commit()
        return {
            "id": review_id,
            "caseRunId": case_run_id,
            "operationId": body.get("operationId"),
            "decision": body["decision"],
            "correctedExpected": body.get("correctedExpected"),
            "comment": body.get("comment"),
            "reviewer": body.get("reviewer") or "evaluator",
            "createdAt": int(now),
            "clinicalWrite": False,
            "note": "Evaluation ground truth only. Android CareWritePath was not invoked.",
        }

    def reviews_for_run(self, case_run_id: str) -> list[dict[str, Any]]:
        rows = self._conn.execute(
            "SELECT * FROM human_reviews WHERE case_run_id = ? ORDER BY created_at ASC",
            (case_run_id,),
        ).fetchall()
        return [
            {
                "id": row["id"],
                "caseRunId": row["case_run_id"],
                "operationId": row["operation_id"],
                "decision": row["decision"],
                "correctedExpected": row["corrected_expected"],
                "comment": row["comment"],
                "reviewer": row["reviewer"],
                "createdAt": int(row["created_at"]),
                "clinicalWrite": False,
            }
            for row in rows
        ]

    def _case_payload(self, row: sqlite3.Row) -> dict[str, Any]:
        return {
            "id": row["id"],
            "title": row["title"],
            "dataClassification": row["data_classification"],
            "sourceType": row["source_type"],
            "inputText": row["input_text"],
            "fixtureTag": row["fixture_tag"],
            "context": json.loads(row["context_json"] or "{}"),
            "labels": json.loads(row["labels_json"] or "[]"),
            "version": row["version"],
            "status": row["status"],
            "createdAt": int(row["created_at"]),
            "updatedAt": int(row["updated_at"]),
        }

    def _run_payload(self, row: sqlite3.Row) -> dict[str, Any]:
        keys = row.keys()
        usage_raw = row["usage_json"] if "usage_json" in keys else None
        return {
            "id": row["id"],
            "caseId": row["case_id"],
            "mode": row["mode"],
            "status": row["status"],
            "fixtureTag": row["fixture_tag"],
            "request": json.loads(row["request_json"] or "{}"),
            "trace": json.loads(row["trace_json"] or "[]"),
            "proposal": json.loads(row["proposal_json"]) if row["proposal_json"] else None,
            "validation": json.loads(row["validation_json"] or "{}"),
            "error": json.loads(row["error_json"]) if row["error_json"] else None,
            "usage": json.loads(usage_raw) if usage_raw else None,
            "clinicalWriteClaimed": bool(row["clinical_write_claimed"]),
            "createdAt": int(row["created_at"]),
            "finishedAt": int(row["finished_at"]) if row["finished_at"] else None,
            "reviews": self.reviews_for_run(row["id"]),
        }
