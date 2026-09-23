from __future__ import annotations

from typing import Any

from ..adapters import QUESTION_SET_V1, DecisionRequest


def build_decision_request(job_id: str, request: dict[str, Any], questions: dict[str, Any] | None = None) -> DecisionRequest:
    """Minimized Jev state. The census is never included."""
    question_set = questions or QUESTION_SET_V1["questions"]
    version = QUESTION_SET_V1["version"] if questions is None else request.get("questionSetVersion") or QUESTION_SET_V1["version"]
    return DecisionRequest(
        job_id=job_id,
        stage="intent",
        input_id=request.get("requestId") or job_id,
        state={
            "text": request.get("text") or "",
            "purpose": request.get("purpose") or "SINGLE_PATIENT_CAPTURE",
        },
        question_set_version=version,
        questions=question_set,
    )
