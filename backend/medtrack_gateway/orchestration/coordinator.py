from __future__ import annotations

from typing import Any

from ..adapters import QUESTION_SET_V1, DecisionError
from .clarification import clarification_proposal, needs_clarification
from .jev_decisions import build_decision_request
from .preflight import check_request
from .proposal_builder import heuristic_proposal
from .task_planner import plan
from .validation import validate


class Coordinator:
    """Single production orchestrator used by Cloud Run and the workbench.

    It never writes Android clinical records. Approval stays on the device.
    """

    def __init__(self, decision=None, generation=None, policy: dict[str, Any] | None = None) -> None:
        from ..adapters import MockDecisionService

        self.decision = decision or MockDecisionService()
        self.generation = generation
        self.policy = policy

    def run(self, job_id: str, request: dict[str, Any]) -> dict[str, Any]:
        preflight = check_request(request)
        if not preflight["ok"]:
            return {"kind": "error", "error": preflight["error"], "proposal": None, "routing": None, "validation": validate(None), "decision": None}
        questions = None
        try:
            decision = self.decision.decide(build_decision_request(job_id, request, questions))
        except DecisionError as exc:
            return {
                "kind": "error",
                "error": {"code": exc.code, "message": exc.message},
                "proposal": None,
                "routing": None,
                "validation": validate(None),
                "decision": None,
            }
        public_decision = {
            "model": decision.model,
            "questionSetVersion": decision.question_set_version or QUESTION_SET_V1["version"],
            "answers": decision.answers,
        }
        routing = plan(
            self.policy,
            answers=decision.answers,
            purpose=preflight["purpose"],
            identity_resolved=preflight["identityResolved"],
            text=request.get("text") or "",
        )
        message = needs_clarification(
            decision.answers,
            purpose=preflight["purpose"],
            identity_resolved=preflight["identityResolved"],
        )
        if message or routing.get("blocked"):
            proposal = clarification_proposal(
                job_id,
                request,
                message or routing["primary"].get("reason") or "Clarification required.",
                decision_model=decision.model,
            )
            return {
                "kind": "clarification",
                "proposal": proposal,
                "decision": public_decision,
                "routing": routing,
                "validation": validate(proposal),
                "error": None,
            }
        if self.generation is not None:
            try:
                generated = self.generation.generate_json(request.get("text") or "", "proposal-bundle")
                if isinstance(generated, dict):
                    generated.setdefault("jobId", job_id)
                    generated.setdefault("requestId", request.get("requestId"))
            except DecisionError as exc:
                return {
                    "kind": "error",
                    "error": {"code": exc.code, "message": exc.message},
                    "proposal": None,
                    "decision": public_decision,
                    "routing": routing,
                    "validation": validate(None),
                }
            checked = validate(generated if isinstance(generated, dict) else None)
            return {
                "kind": "proposal" if checked["ok"] else "error",
                "proposal": generated if isinstance(generated, dict) else None,
                "decision": public_decision,
                "routing": routing,
                "validation": checked,
                "error": None if checked["ok"] else {"code": "VALIDATION", "message": "Proposal failed validation."},
            }
        proposal = heuristic_proposal(job_id, request, request.get("text") or "", decision_model=decision.model)
        return {
            "kind": "proposal",
            "proposal": proposal,
            "decision": public_decision,
            "routing": routing,
            "validation": validate(proposal),
            "error": None,
        }
