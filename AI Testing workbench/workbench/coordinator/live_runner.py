from __future__ import annotations

import hashlib
import json
import os
import sys
import time
import uuid
from pathlib import Path
from typing import Any

_REPO = Path(__file__).resolve().parents[3]
_BACKEND = _REPO / "backend"
if str(_BACKEND) not in sys.path:
    sys.path.insert(0, str(_BACKEND))

from medtrack_gateway import COMMAND_SCHEMA_VERSION, ROUTE_VERSION
from medtrack_gateway.adapters import DecisionError, DecisionRequest, MockDecisionService
from medtrack_gateway.adapters.openrouter import OpenRouterDecisionAdapter, OpenRouterGenerationAdapter
from medtrack_gateway.proposals import ProposalValidationError, validate_proposal_bundle
from medtrack_gateway.config import SETTINGS

from workbench.config.loader import WorkbenchConfig
from workbench.routing import select_routes


def openrouter_key_configured() -> bool:
    return bool(os.environ.get("OPENROUTER_API_KEY", "").strip() or SETTINGS.openrouter_api_key)


class LiveSyntheticCoordinator:
    """WB-03 live path: OpenRouter Jev + generative. SYNTHETIC cases only."""

    def __init__(self, config: WorkbenchConfig) -> None:
        self.config = config
        self.decision = OpenRouterDecisionAdapter()
        self.generation = OpenRouterGenerationAdapter()

    def run_case(self, case: dict[str, Any], *, mode: str = "LIVE_SYNTHETIC") -> dict[str, Any]:
        if mode != "LIVE_SYNTHETIC":
            raise ValueError("Live coordinator only accepts LIVE_SYNTHETIC mode.")
        if not openrouter_key_configured():
            raise ValueError(
                "OPENROUTER_API_KEY is not set. Add it to the environment to enable live synthetic runs."
            )
        if (case.get("dataClassification") or "").upper() != "SYNTHETIC":
            raise ValueError("Live mode rejects non-SYNTHETIC cases.")

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
        qs = self.config.question_set()
        policy = self.config.routing_policy()

        stages.append(
            {
                "stage": "preflight",
                "ok": True,
                "detail": {
                    "dataClassification": "SYNTHETIC",
                    "mode": "LIVE_SYNTHETIC",
                    "providerCallsPlanned": 2,
                },
            }
        )
        stages.append(
            {
                "stage": "jev_state",
                "ok": True,
                "detail": {
                    "questionSetVersion": qs["version"],
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

        t0 = time.perf_counter()
        try:
            decision = self.decision.decide(
                DecisionRequest(
                    job_id=job_id,
                    stage="intent",
                    input_id=request["requestId"],
                    state={"text": text, "purpose": purpose},
                    question_set_version=qs["version"],
                    questions=qs["questions"],
                )
            )
        except DecisionError as exc:
            stages.append({"stage": "jev_decisions", "ok": False, "detail": {"code": exc.code, "message": exc.message}})
            usage = self._rollup([])
            stages.append({"stage": "usage_summary", "ok": True, "detail": usage})
            return self._failed(case, request, stages, {"code": exc.code, "message": exc.message}, usage=usage)
        jev_ms = int((time.perf_counter() - t0) * 1000)
        decision_public = {
            "model": decision.model,
            "requestedModel": SETTINGS.jev_model,
            "questionSetVersion": decision.question_set_version,
            "answers": decision.answers,
            "usage": decision.usage,
            "latencyMs": jev_ms,
            "source": "openrouter-jev",
        }
        stages.append({"stage": "jev_decisions", "ok": True, "detail": decision_public})
        invocations: list[dict[str, Any]] = [
            {
                "stage": "jev",
                "requestedModel": SETTINGS.jev_model,
                "model": decision.model,
                "provider": None,
                "latencyMs": jev_ms,
                "usage": decision.usage or {},
            }
        ]

        identity_resolved = bool(request.get("patientId") and request.get("admissionId"))
        routing = select_routes(
            policy,
            answers=decision.answers,
            purpose=purpose,
            identity_resolved=identity_resolved,
            text=text,
        )
        stages.append({"stage": "routing", "ok": True, "detail": routing})

        model_cfg = self.config.model_for_route(routing.get("modelRouteId"))
        stages.append(
            {
                "stage": "model_configuration",
                "ok": True,
                "detail": {
                    "label": model_cfg["route_id"] if model_cfg else "none",
                    "modelId": model_cfg["model_id"] if model_cfg else None,
                    "promptVersion": model_cfg.get("prompt_version") if model_cfg else None,
                    "schemaVersion": model_cfg.get("schema_version") if model_cfg else None,
                    "providerCalls": 1 if model_cfg else 0,
                },
            }
        )

        proposal = None
        if routing["primary"]["capability"] == "CLARIFICATION" or model_cfg is None:
            proposal = {
                "proposalId": f"prop-{job_id}",
                "jobId": job_id,
                "requestId": request["requestId"],
                "commandSchemaVersion": COMMAND_SCHEMA_VERSION,
                "contextDigest": request["contextDigest"],
                "identity": {
                    "state": "RESOLVED" if identity_resolved else "UNRESOLVED",
                    "patientId": request.get("patientId"),
                    "admissionId": request.get("admissionId"),
                },
                "summary": "No mutating operations.",
                "clarification": "Which patient should this apply to?"
                if not identity_resolved
                else "Please clarify the clinical intent.",
                "operations": [],
                "provenance": {"routeVersion": ROUTE_VERSION, "decisionModel": decision.model},
            }
            stages.append(
                {
                    "stage": "proposal",
                    "ok": True,
                    "detail": {
                        "kind": "clarification",
                        "operationCount": 0,
                        "providerCalls": 0,
                    },
                }
            )
        else:
            prompt = self._build_prompt(model_cfg, case, request)
            candidates = self._generation_candidates(model_cfg.get("model_id"))
            t1 = time.perf_counter()
            gen = None
            attempts: list[dict[str, Any]] = []
            last_error: DecisionError | None = None
            for candidate in candidates:
                try:
                    gen = self.generation.generate_with_meta(
                        prompt,
                        "proposal-bundle",
                        model=candidate,
                    )
                    attempts.append({"model": candidate, "ok": True})
                    break
                except DecisionError as exc:
                    attempts.append(
                        {"model": candidate, "ok": False, "code": exc.code, "message": exc.message}
                    )
                    last_error = exc
                    if not self._is_model_unavailable(exc):
                        break
            if gen is None:
                stages.append(
                    {
                        "stage": "proposal",
                        "ok": False,
                        "detail": {
                            "code": last_error.code if last_error else "UNAVAILABLE",
                            "message": last_error.message if last_error else "No generation model succeeded.",
                            "attempts": attempts,
                        },
                    }
                )
                usage = self._rollup(invocations)
                stages.append({"stage": "usage_summary", "ok": True, "detail": usage})
                return self._failed(
                    case,
                    request,
                    stages,
                    {
                        "code": last_error.code if last_error else "UNAVAILABLE",
                        "message": last_error.message if last_error else "No generation model succeeded.",
                        "attempts": attempts,
                    },
                    usage=usage,
                )
            gen_ms = int((time.perf_counter() - t1) * 1000)
            proposal = gen["json"]
            if isinstance(proposal, dict):
                proposal.setdefault("jobId", job_id)
                proposal.setdefault("requestId", request["requestId"])
                proposal.setdefault("commandSchemaVersion", COMMAND_SCHEMA_VERSION)
                proposal.setdefault("contextDigest", request["contextDigest"])
            gen_invocation = {
                "stage": "generation",
                "routeId": model_cfg["route_id"],
                "requestedModel": gen.get("requestedModel") or model_cfg["model_id"],
                "model": gen.get("model") or model_cfg["model_id"],
                "provider": gen.get("provider"),
                "latencyMs": gen_ms,
                "usage": gen.get("usage") or {},
                "responseId": gen.get("id"),
                "attempts": attempts,
            }
            invocations.append(gen_invocation)
            stages.append(
                {
                    "stage": "proposal",
                    "ok": True,
                    "detail": {
                        "kind": "proposal",
                        "promptDigest": "sha256:" + hashlib.sha256(prompt.encode()).hexdigest()[:32],
                        "operationCount": len((proposal or {}).get("operations") or []),
                        "requestedModel": gen_invocation["requestedModel"],
                        "model": gen_invocation["model"],
                        "provider": gen_invocation["provider"],
                        "latencyMs": gen_ms,
                        "usage": gen_invocation["usage"],
                        "attempts": attempts,
                    },
                }
            )

        validation = self._validate(proposal)
        stages.append({"stage": "validation", "ok": validation["ok"], "detail": validation})
        usage = self._rollup(invocations)
        stages.append({"stage": "usage_summary", "ok": True, "detail": usage})
        status = "SUCCEEDED" if validation["ok"] else "BLOCKED"
        if proposal and proposal.get("clarification") and not (proposal.get("operations") or []):
            status = "CLARIFICATION"

        return {
            "caseId": case["id"],
            "mode": "LIVE_SYNTHETIC",
            "status": status,
            "fixtureTag": None,
            "request": request,
            "trace": stages,
            "proposal": proposal,
            "validation": validation,
            "error": None,
            "clinicalWriteClaimed": False,
            "banner": "LIVE PROVIDER — SYNTHETIC DATA ONLY",
            "androidWrite": False,
            "note": "Simulate approval records evaluation only; CareWritePath is not invoked.",
            "usage": usage,
        }

    def _build_prompt(self, model_cfg: dict[str, Any], case: dict[str, Any], request: dict[str, Any]) -> str:
        prompt_body = self.config.prompt_text(model_cfg["prompt_version"])
        return (
            f"{prompt_body}\n\n"
            f"## Synthetic case\n"
            f"Title: {case.get('title')}\n"
            f"Context: {json.dumps(case.get('context') or {})}\n"
            f"Request: {json.dumps({k: request[k] for k in ('purpose', 'patientId', 'admissionId', 'contextDigest')})}\n\n"
            f"## Input text\n{request.get('text') or ''}\n"
        )

    def _generation_candidates(self, primary: str | None) -> list[str]:
        ordered: list[str] = []
        for model in [primary, *list(self.config.active_experiment().get("freeGenerationFallbacks") or [])]:
            if not model:
                continue
            if model not in ordered:
                ordered.append(model)
        return ordered

    @staticmethod
    def _is_model_unavailable(exc: DecisionError) -> bool:
        msg = (exc.message or "").lower()
        return exc.code in {"INVALID_INPUT", "UNAVAILABLE"} and (
            "404" in msg
            or "unavailable" in msg
            or "no endpoints" in msg
            or "not found" in msg
        )

    def _rollup(self, invocations: list[dict[str, Any]]) -> dict[str, Any]:
        prompt_tokens = 0
        completion_tokens = 0
        total_tokens = 0
        cost_usd = 0.0
        cost_known = False
        for inv in invocations:
            usage = inv.get("usage") or {}
            for key, bucket in (
                ("prompt_tokens", "prompt"),
                ("completion_tokens", "completion"),
                ("total_tokens", "total"),
            ):
                val = usage.get(key)
                if val is None:
                    continue
                try:
                    n = int(val)
                except (TypeError, ValueError):
                    continue
                if bucket == "prompt":
                    prompt_tokens += n
                elif bucket == "completion":
                    completion_tokens += n
                else:
                    total_tokens += n
            cost = usage.get("costUsd")
            if cost is not None:
                try:
                    cost_usd += float(cost)
                    cost_known = True
                except (TypeError, ValueError):
                    pass
        if total_tokens == 0 and (prompt_tokens or completion_tokens):
            total_tokens = prompt_tokens + completion_tokens
        return {
            "invocations": invocations,
            "totals": {
                "providerCalls": len(invocations),
                "promptTokens": prompt_tokens,
                "completionTokens": completion_tokens,
                "totalTokens": total_tokens,
                "costUsd": cost_usd if cost_known else None,
                "latencyMs": sum(int(i.get("latencyMs") or 0) for i in invocations),
                "models": [i.get("model") for i in invocations if i.get("model")],
            },
            "note": None
            if cost_known
            else "Cost is logged when OpenRouter returns usage.cost; otherwise tokens/latency only.",
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

    def _failed(self, case, request, stages, error, usage=None):
        return {
            "caseId": case["id"],
            "mode": "LIVE_SYNTHETIC",
            "status": "FAILED",
            "fixtureTag": None,
            "request": request,
            "trace": stages,
            "proposal": None,
            "validation": {"ok": False, "findings": []},
            "error": error,
            "clinicalWriteClaimed": False,
            "banner": "LIVE PROVIDER — SYNTHETIC DATA ONLY",
            "androidWrite": False,
            "note": "Simulate approval records evaluation only; CareWritePath is not invoked.",
            "usage": usage or self._rollup([]),
        }
