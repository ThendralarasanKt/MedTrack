from __future__ import annotations

import json
from typing import Any

from ..config import CONTRACTS_DIR
from ..orchestration.coordinator import Coordinator
from ..orchestration.proposal_builder import heuristic_proposal
from ..proposals import ProposalValidationError, is_commit_ready
from . import DecisionError, MockDecisionService


FIXTURES = CONTRACTS_DIR / "fixtures"


class BoundedOrchestrator:
    """Jev routes; generative extraction produces proposals. No clinical DB credential."""

    def __init__(self, decision=None, generation=None) -> None:
        self.decision = decision or MockDecisionService()
        self.generation = generation

    def run(self, job_id: str, request_body: dict, fixture: str | None = None) -> dict[str, Any]:
        if fixture:
            return self._fixture_result(job_id, request_body, fixture)
        return Coordinator(decision=self.decision, generation=self.generation).run(job_id, request_body)

    def _fixture_result(self, job_id: str, request_body: dict, fixture: str) -> dict[str, Any]:
        if fixture == "timeout":
            raise DecisionError("TRANSIENT_PROVIDER", "Provider timed out before a proposal was produced.")
        if fixture == "unsupported-version":
            raise ProposalValidationError("UNSUPPORTED_SCHEMA", "commandSchemaVersion is not supported.")
        if fixture == "stale-context":
            raise ProposalValidationError("VALIDATION", "Context digest does not match the supplied manifest.")
        if fixture == "clarification":
            return {"kind": "clarification", "proposal": _load_json("clarification.proposal.json")}
        if fixture == "partial-input":
            bundle = _load_json("partial-input.proposal.json")
            return {"kind": "proposal", "proposal": bundle, "commitReady": is_commit_ready(bundle)}
        if fixture == "success":
            bundle = _load_json("success.proposal.json")
            bundle["jobId"] = job_id
            bundle["requestId"] = request_body.get("requestId")
            return {"kind": "proposal", "proposal": bundle, "commitReady": is_commit_ready(bundle)}
        return {"kind": "proposal", "proposal": _heuristic_proposal(job_id, request_body, request_body.get("text") or "")}


def _heuristic_proposal(job_id: str, request_body: dict, text: str) -> dict[str, Any]:
    return heuristic_proposal(job_id, request_body, text)


def _load_json(name: str) -> dict[str, Any]:
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))
