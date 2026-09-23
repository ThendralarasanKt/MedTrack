from __future__ import annotations

from typing import Any


def check_request(request: dict[str, Any], *, max_chars: int = 20_000) -> dict[str, Any]:
    """Deterministic checks before Jev. No model call and no clinical write."""
    classification = (request.get("dataClassification") or "SYNTHETIC").upper()
    if classification != "SYNTHETIC":
        return {"ok": False, "error": {"code": "FORBIDDEN", "message": "Only SYNTHETIC cases may run."}}
    text = request.get("text") or ""
    if len(text) > max_chars:
        return {"ok": False, "error": {"code": "VALIDATION", "message": "Input text exceeds the limit."}}
    patient = request.get("patientId")
    admission = request.get("admissionId")
    if (patient and not admission) or (admission and not patient):
        return {
            "ok": False,
            "error": {
                "code": "VALIDATION",
                "message": "Context must include both patientId and admissionId, or neither.",
            },
        }
    return {
        "ok": True,
        "dataClassification": "SYNTHETIC",
        "identityResolved": bool(patient and admission),
        "purpose": request.get("purpose") or "SINGLE_PATIENT_CAPTURE",
    }
