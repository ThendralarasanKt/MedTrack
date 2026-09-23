from __future__ import annotations

from typing import Any

from ..proposals import ProposalValidationError, is_commit_ready, validate_proposal_bundle


def validate(proposal: dict[str, Any] | None) -> dict[str, Any]:
    if not proposal:
        return {
            "ok": False,
            "commitReady": False,
            "findings": [{"code": "NO_PROPOSAL", "severity": "ERROR", "blocking": True, "explanation": "No proposal."}],
        }
    try:
        warnings = validate_proposal_bundle(proposal)
    except ProposalValidationError as exc:
        return {
            "ok": False,
            "commitReady": False,
            "findings": [{"code": exc.code, "severity": "ERROR", "blocking": True, "explanation": exc.message}],
        }
    return {
        "ok": True,
        "commitReady": is_commit_ready(proposal),
        "findings": [
            {"code": "SCHEMA_WARNING", "severity": "WARNING", "blocking": False, "explanation": warning}
            for warning in warnings
        ],
    }
