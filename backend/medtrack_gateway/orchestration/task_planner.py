from __future__ import annotations

from typing import Any

from .routing_policy import select_routes


def plan(
    policy: dict[str, Any] | None,
    *,
    answers: dict[str, Any],
    purpose: str,
    identity_resolved: bool,
    text: str,
) -> dict[str, Any]:
    """Turn Jev answers into an ordered capability plan. Clarification blocks later steps."""
    if not policy:
        return {
            "policyVersion": "none",
            "primary": {"capability": "ADMINISTRATIVE_EXTRACTION", "reason": "no policy"},
            "matched": [],
            "modelRouteId": None,
            "reasons": [],
            "blocked": False,
        }
    routing = select_routes(
        policy,
        answers=answers,
        purpose=purpose,
        identity_resolved=identity_resolved,
        text=text,
    )
    routing["blocked"] = routing["primary"]["capability"] == "CLARIFICATION"
    return routing
