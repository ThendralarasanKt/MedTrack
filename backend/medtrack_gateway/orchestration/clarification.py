from __future__ import annotations

from typing import Any

from .. import COMMAND_SCHEMA_VERSION, ROUTE_VERSION


def clarification_proposal(job_id: str, request: dict[str, Any], message: str, *, decision_model: str | None = None) -> dict[str, Any]:
    """A clarification result has no operations and cannot commit."""
    return {
        "proposalId": f"prop-{job_id}",
        "jobId": job_id,
        "requestId": request.get("requestId"),
        "commandSchemaVersion": COMMAND_SCHEMA_VERSION,
        "contextDigest": request.get("contextDigest"),
        "identity": {
            "state": "UNRESOLVED",
            "patientId": request.get("patientId"),
            "admissionId": request.get("admissionId"),
        },
        "summary": "No mutating operations.",
        "clarification": message,
        "operations": [],
        "provenance": {"routeVersion": ROUTE_VERSION, "decisionModel": decision_model},
    }


def needs_clarification(answers: dict[str, Any], *, purpose: str, identity_resolved: bool) -> str | None:
    # Unresolved identity is a routing-policy question, not a second guess here.
    del identity_resolved
    multiple = (answers.get("contains_multiple_patient_contexts") or {}).get("noul", 0) or 0
    if multiple > 0.8 and purpose != "CROSS_PATIENT_EXPLICIT":
        return "Cross-patient context requires an explicit purpose."
    ambiguity = (answers.get("unresolved_ambiguity") or {}).get("noul", 0) or 0
    if ambiguity >= 0.8:
        return "Please clarify the clinical intent."
    return None
