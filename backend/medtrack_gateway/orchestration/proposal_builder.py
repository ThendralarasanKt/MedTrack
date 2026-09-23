from __future__ import annotations

from typing import Any

from .. import COMMAND_SCHEMA_VERSION, ROUTE_VERSION
from ..proposals import digest_obj


def heuristic_proposal(job_id: str, request: dict[str, Any], text: str, *, decision_model: str | None = None) -> dict[str, Any]:
    identity_state = "RESOLVED" if request.get("patientId") and request.get("admissionId") else "UNRESOLVED"
    operations: list[dict[str, Any]] = []
    lower = text.lower()
    if "transfer" in lower and "loc-" in lower:
        operations.append(
            {
                "operationId": f"op-{digest_obj(text)[-8:]}-t",
                "type": "TRANSFER",
                "atomicGroupId": "g-location",
                "dependsOn": [],
                "target": {
                    "kind": "LOCATION",
                    "id": _extract_id(text, "loc-"),
                    "expectedVersion": 1,
                    "displayHint": None,
                },
                "fields": {"admissionId": request.get("admissionId")},
                "unresolvedFields": [],
            }
        )
    bundle = {
        "proposalId": f"prop-{job_id}",
        "jobId": job_id,
        "requestId": request.get("requestId"),
        "commandSchemaVersion": COMMAND_SCHEMA_VERSION,
        "contextDigest": request.get("contextDigest"),
        "identity": {
            "state": identity_state,
            "patientId": request.get("patientId"),
            "admissionId": request.get("admissionId"),
        },
        "summary": "No mutating operations." if not operations else "Proposed local clinical commands for review.",
        "operations": operations,
        "provenance": {
            "routeVersion": ROUTE_VERSION,
            "decisionModel": decision_model or "mock-jev",
            "generationModel": None,
        },
    }
    if identity_state != "RESOLVED":
        bundle["clarification"] = "Which patient should this apply to?"
    return bundle


def _extract_id(text: str, prefix: str) -> str | None:
    for token in text.replace("(", " ").replace(")", " ").split():
        if token.startswith(prefix):
            return token.strip(".,")
    return None
