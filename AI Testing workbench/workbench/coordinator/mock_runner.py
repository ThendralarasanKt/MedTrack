from __future__ import annotations

import sys
import uuid
from pathlib import Path
from typing import Any

_REPO = Path(__file__).resolve().parents[3]
_BACKEND = _REPO / "backend"
if str(_BACKEND) not in sys.path:
    sys.path.insert(0, str(_BACKEND))

from medtrack_gateway import COMMAND_SCHEMA_VERSION, ROUTE_VERSION
from medtrack_gateway.adapters import QUESTION_SET_V1, DecisionError, DecisionRequest, MockDecisionService
from medtrack_gateway.adapters.orchestration import BoundedOrchestrator
from medtrack_gateway.proposals import ProposalValidationError, validate_proposal_bundle

from workbench.config.loader import WorkbenchConfig
from workbench.routing import select_routes


FIXTURE_OVERRIDE = {
    "success": "ADMINISTRATIVE_EXTRACTION",
    "clarification": "CLARIFICATION",
    "partial-input": "CLINICAL_EXTRACTION",
    "stale-context": "DETERMINISTIC",
    "timeout": "DETERMINISTIC",
}


class MockCoordinator:
    """Runs the mock evaluation pipeline. Never writes clinical records."""

    def __init__(self, config: WorkbenchConfig | None = None) -> None:
        self.config = config
        self.orchestrator = BoundedOrchestrator(decision=MockDecisionService())
        self.mock_decision = MockDecisionService()

    def run_case(self, case: dict[str, Any], *, mode: str = "MOCK") -> dict[str, Any]:
        if mode != "MOCK":
            raise ValueError("Mock coordinator only supports MOCK mode.")
        if (case.get("dataClassification") or "").upper() != "SYNTHETIC":
            raise ValueError("Live/non-synthetic classification is rejected.")

        fixture = case.get("fixtureTag") or None
        context = case.get("context") or {}
        text = case.get("inputText") or ""
        purpose = context.get("purpose") or "SINGLE_PATIENT_CAPTURE"
        request = {
            "requestId": f"req-{uuid.uuid4().hex[:10]}",
            "commandSchemaVersion": COMMAND_SCHEMA_VERSION,
            "purpose": purpose,
            "contextDigest": context.get("contextDigest") or "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "patientId": context.get("patientId"),
            "admissionId": context.get("admissionId"),
            "text": text,
            "dataClassification": "SYNTHETIC",
        }
        job_id = f"job-{uuid.uuid4().hex[:10]}"
        stages: list[dict[str, Any]] = []
        qs = self.config.question_set() if self.config else QUESTION_SET_V1
        policy = self.config.routing_policy() if self.config else None

        preflight = self._preflight(case, request)
        stages.append({"stage": "preflight", "ok": preflight["ok"], "detail": preflight})
        if not preflight["ok"]:
            return self._failed(case, request, stages, fixture, preflight["error"])

        stages.append(
            {
                "stage": "jev_state",
                "ok": True,
                "detail": {
                    "questionSetVersion": qs.get("version", QUESTION_SET_V1["version"]),
                    "routeVersion": ROUTE_VERSION,
                    "minimizedState": {
                        "purpose": purpose,
                        "textPreview": text[:240],
                        "patientId": request.get("patientId"),
                        "admissionId": request.get("admissionId"),
                    },
                },
            }
        )

        decision_env = self.mock_decision.decide(
            DecisionRequest(
                job_id=job_id,
                stage="intent",
                input_id=request["requestId"],
                state={"text": text, "purpose": purpose},
                question_set_version=qs.get("version", QUESTION_SET_V1["version"]),
                questions=qs.get("questions", QUESTION_SET_V1["questions"]),
            )
        )
        decision = {
            "model": decision_env.model,
            "questionSetVersion": decision_env.question_set_version,
            "answers": decision_env.answers,
            "policyVersion": decision_env.policy_version,
            "source": "mock-jev",
        }
        stages.append({"stage": "jev_decisions", "ok": True, "detail": decision})

        identity_resolved = bool(request.get("patientId") and request.get("admissionId"))
        if policy:
            routing = select_routes(
                policy,
                answers=decision_env.answers,
                purpose=purpose,
                identity_resolved=identity_resolved,
                text=text,
            )
        else:
            routing = {
                "policyVersion": "none",
                "primary": {"capability": "ADMINISTRATIVE_EXTRACTION", "reason": "no policy"},
                "matched": [],
                "modelRouteId": None,
                "reasons": [],
            }
        if fixture in FIXTURE_OVERRIDE:
            routing = {
                **routing,
                "fixtureOverride": FIXTURE_OVERRIDE[fixture],
                "reasons": routing.get("reasons", [])
                + [f"Fixture tag '{fixture}' forces capability {FIXTURE_OVERRIDE[fixture]} for proposal stage."],
            }
        stages.append({"stage": "routing", "ok": True, "detail": routing})

        model_route_id = routing.get("modelRouteId")
        model_cfg = None
        if self.config and model_route_id:
            try:
                model_cfg = self.config.model_for_route(model_route_id)
            except Exception:
                model_cfg = None
        stages.append(
            {
                "stage": "model_configuration",
                "ok": True,
                "detail": {
                    "label": "mock-fixture",
                    "modelId": "mock/no-provider",
                    "configuredRouteId": model_route_id,
                    "configuredModelId": (model_cfg or {}).get("model_id"),
                    "promptVersion": (model_cfg or {}).get("prompt_version"),
                    "schemaVersion": COMMAND_SCHEMA_VERSION,
                    "providerCalls": 0,
                },
            }
        )

        try:
            result = self.orchestrator.run(job_id, request, fixture=fixture)
        except DecisionError as exc:
            stages.append({"stage": "orchestrator", "ok": False, "detail": {"code": exc.code, "message": exc.message}})
            return self._failed(case, request, stages, fixture, {"code": exc.code, "message": exc.message})
        except ProposalValidationError as exc:
            stages.append({"stage": "orchestrator", "ok": False, "detail": {"code": exc.code, "message": str(exc)}})
            return self._failed(case, request, stages, fixture, {"code": exc.code, "message": str(exc)})

        if result.get("decision"):
            stages[2]["detail"]["orchestratorDecision"] = result["decision"]

        proposal = result.get("proposal")
        stages.append(
            {
                "stage": "proposal",
                "ok": proposal is not None,
                "detail": {
                    "kind": result.get("kind"),
                    "summary": (proposal or {}).get("summary") if isinstance(proposal, dict) else None,
                    "operationCount": len((proposal or {}).get("operations") or [])
                    if isinstance(proposal, dict)
                    else 0,
                },
            }
        )

        validation = self._validate(proposal)
        stages.append({"stage": "validation", "ok": validation["ok"], "detail": validation})

        usage = {
            "invocations": [],
            "totals": {
                "providerCalls": 0,
                "promptTokens": 0,
                "completionTokens": 0,
                "totalTokens": 0,
                "costUsd": 0.0,
                "latencyMs": 0,
                "models": ["mock-jev", "mock/no-provider"],
            },
            "note": "MOCK mode — no provider cost.",
        }
        stages.append({"stage": "usage_summary", "ok": True, "detail": usage})

        status = "SUCCEEDED"
        if result.get("kind") == "error" or not validation["ok"]:
            status = "BLOCKED" if proposal else "FAILED"
        elif result.get("kind") == "clarification":
            status = "CLARIFICATION"

        return {
            "caseId": case["id"],
            "mode": "MOCK",
            "status": status,
            "fixtureTag": fixture,
            "request": request,
            "trace": stages,
            "proposal": proposal,
            "validation": validation,
            "error": result.get("error"),
            "clinicalWriteClaimed": False,
            "banner": "MOCK — NO PROVIDER CALLS",
            "androidWrite": False,
            "note": "Simulate approval records evaluation only; CareWritePath is not invoked.",
            "usage": usage,
        }

    def _preflight(self, case: dict[str, Any], request: dict[str, Any]) -> dict[str, Any]:
        text = request.get("text") or ""
        if len(text) > 20_000:
            return {"ok": False, "error": {"code": "VALIDATION", "message": "Input text exceeds mock limit."}}
        if (case.get("dataClassification") or "").upper() != "SYNTHETIC":
            return {"ok": False, "error": {"code": "FORBIDDEN", "message": "Only SYNTHETIC cases may run."}}
        ctx = case.get("context") or {}
        patient = ctx.get("patientId")
        admission = ctx.get("admissionId")
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
            "attachmentCount": 0,
            "contextConsistent": True,
        }

    def _validate(self, proposal: dict[str, Any] | None) -> dict[str, Any]:
        if not proposal:
            return {
                "ok": False,
                "findings": [
                    {
                        "code": "NO_PROPOSAL",
                        "severity": "ERROR",
                        "blocking": True,
                        "explanation": "No proposal object to validate.",
                    }
                ],
            }
        try:
            warnings = validate_proposal_bundle(proposal)
            return {
                "ok": True,
                "findings": [
                    {"code": "SCHEMA_WARNING", "severity": "WARNING", "blocking": False, "explanation": w}
                    for w in warnings
                ],
            }
        except ProposalValidationError as exc:
            return {
                "ok": False,
                "findings": [
                    {
                        "code": getattr(exc, "code", "VALIDATION"),
                        "severity": "ERROR",
                        "blocking": True,
                        "explanation": str(exc),
                    }
                ],
            }

    def _failed(self, case, request, stages, fixture, error):
        return {
            "caseId": case["id"],
            "mode": "MOCK",
            "status": "FAILED",
            "fixtureTag": fixture,
            "request": request,
            "trace": stages,
            "proposal": None,
            "validation": {"ok": False, "findings": []},
            "error": error,
            "clinicalWriteClaimed": False,
            "banner": "MOCK — NO PROVIDER CALLS",
            "androidWrite": False,
            "note": "Simulate approval records evaluation only; CareWritePath is not invoked.",
        }
