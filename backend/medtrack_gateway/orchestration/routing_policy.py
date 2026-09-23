from __future__ import annotations

from typing import Any


def noul_value(answers: dict[str, Any], qid: str) -> float | None:
    ans = answers.get(qid) or {}
    if "noul" not in ans or ans.get("unavailable"):
        return None
    return float(ans["noul"]) if ans["noul"] is not None else None


def select_routes(
    policy: dict[str, Any],
    *,
    answers: dict[str, Any],
    purpose: str,
    identity_resolved: bool,
    text: str,
) -> dict[str, Any]:
    """Deterministic route selection. Models do not choose the route."""
    matched: list[dict[str, Any]] = []
    lower = (text or "").lower()
    for rule in policy.get("rules") or []:
        when = rule.get("when") or {}
        if not _matches(when, answers=answers, purpose=purpose, identity_resolved=identity_resolved, text=lower):
            continue
        matched.append({"ruleId": rule.get("id"), "capability": rule["route"], "reason": rule.get("reason")})
    if not matched:
        matched.append(
            {
                "ruleId": "default",
                "capability": policy.get("defaultRoute", "ADMINISTRATIVE_EXTRACTION"),
                "reason": policy.get("defaultReason", "Default route"),
            }
        )
    primary = next((item for item in matched if item["capability"] == "CLARIFICATION"), matched[0])
    route_map = policy.get("modelRouteMap") or {}
    return {
        "policyVersion": policy.get("version"),
        "primary": primary,
        "matched": matched,
        "modelRouteId": route_map.get(primary["capability"]),
        "reasons": [f"{item['capability']}: {item['reason']}" for item in matched],
    }


def _matches(when: dict[str, Any], *, answers: dict[str, Any], purpose: str, identity_resolved: bool, text: str) -> bool:
    if "identity_unresolved" in when and bool(when["identity_unresolved"]) == identity_resolved:
        return False
    if "purpose_ne" in when and purpose == when["purpose_ne"]:
        return False
    if "purpose_eq" in when and purpose != when["purpose_eq"]:
        return False
    if "noul_gte" in when:
        qid, threshold = when["noul_gte"]
        value = noul_value(answers, qid)
        if value is None or value < float(threshold):
            return False
    if "score_eq" in when:
        qid, expected = when["score_eq"]
        if (answers.get(qid) or {}).get("score") != expected:
            return False
    if "text_any" in when and not any(needle.lower() in text for needle in when["text_any"]):
        return False
    return True
