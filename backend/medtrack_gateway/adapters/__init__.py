from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Protocol


class DecisionError(Exception):
    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


@dataclass
class DecisionRequest:
    job_id: str
    stage: str
    input_id: str
    state: dict[str, Any]
    question_set_version: str
    questions: dict[str, Any]


@dataclass
class DecisionEnvelope:
    job_id: str
    stage: str
    input_id: str
    question_set_version: str
    model: str
    answers: dict[str, Any]
    usage: dict[str, Any] = field(default_factory=dict)
    latency_ms: int = 0
    error: str | None = None
    state_digest: str = ""
    policy_version: str = "policy-v1"


class DecisionService(Protocol):
    def decide(self, request: DecisionRequest) -> DecisionEnvelope: ...


class GenerationService(Protocol):
    def generate_json(self, prompt: str, schema_name: str) -> dict[str, Any]: ...


QUESTION_SET_V1 = {
    "version": "intent-v1",
    "questions": {
        "requests_record_lookup": {
            "type": "noul",
            "instructions": "Is the user asking to retrieve stored information?",
            "criteria": {
                "true": "The text asks to look up, show, list, or retrieve existing record data.",
                "false": "The text does not ask to retrieve stored information.",
            },
        },
        "requests_patient_creation": {
            "type": "noul",
            "instructions": "Is a new patient/admission being described for registration?",
            "criteria": {
                "true": "The text describes admitting or registering a new patient/admission.",
                "false": "No new patient registration is being requested.",
            },
        },
        "requests_location_change": {
            "type": "noul",
            "instructions": "Is a transfer/location update requested?",
            "criteria": {
                "true": "The text requests a bed/ward/location transfer or update.",
                "false": "No location or transfer change is requested.",
            },
        },
        "requests_task_or_reminder": {
            "type": "noul",
            "instructions": "Is work to be scheduled, completed, cancelled or rescheduled?",
            "criteria": {
                "true": "The text schedules, completes, cancels, or reschedules a task/reminder.",
                "false": "No task or reminder change is requested.",
            },
        },
        "contains_medication_change": {
            "type": "noul",
            "instructions": "Does this include a proposed or reported medication change?",
            "criteria": {
                "true": "The text starts, stops, changes, or reports a medication/dose change.",
                "false": "No medication change is described.",
            },
        },
        "contains_multiple_patient_contexts": {
            "type": "noul",
            "instructions": "Does the source appear to refer to multiple patients?",
            "criteria": {
                "true": "The text clearly refers to more than one patient.",
                "false": "The text refers to at most one patient context.",
            },
        },
        "statement_kind": {
            "type": "choice",
            "instructions": "Select the statement kind.",
            "criteria": {
                "observation": "Reported finding",
                "completed_care": "Care already performed",
                "recommendation": "Requested change",
                "question": "A question",
                "mixed": "More than one kind",
                "unclear": "Cannot tell",
            },
        },
        "processing_demand": {
            "type": "score",
            "instructions": "How much interpretation does this input need?",
            "criteria": ["Straightforward", "Contextual", "Complex"],
        },
        "source_supports_action": {
            "type": "noul",
            "instructions": "Does the supplied source support this proposed action?",
            "criteria": {
                "true": "The supplied source text supports the proposed action.",
                "false": "The source does not support the proposed action, or support is unclear.",
            },
        },
        "unresolved_ambiguity": {
            "type": "noul",
            "instructions": "Does this proposal still depend on ambiguous intent or attribution?",
            "criteria": {
                "true": "Important intent or attribution remains ambiguous.",
                "false": "Intent and attribution are sufficiently clear.",
            },
        },
    },
}


class MockDecisionService:
    """Deterministic Jev stand-in. Never authorizes a write."""

    def decide(self, request: DecisionRequest) -> DecisionEnvelope:
        text = json_state_text(request.state)
        answers: dict[str, Any] = {}
        for qid, spec in QUESTION_SET_V1["questions"].items():
            if spec["type"] == "noul":
                answers[qid] = {"noul": _noul_for(qid, text)}
            elif spec["type"] == "choice":
                choice = "recommendation" if "transfer" in text or "stop" in text else "unclear"
                criteria = spec.get("criteria")
                if isinstance(criteria, dict):
                    options = list(criteria.keys())
                else:
                    options = [k for k in spec if k not in {"type", "instructions", "criteria"}]
                dist = {opt: (0.7 if opt == choice else 0.3 / max(len(options) - 1, 1)) for opt in options}
                answers[qid] = {"choice": choice, "probabilities": dist, "confidence": 0.7}
            elif spec["type"] == "score":
                answers[qid] = {
                    "score": 0,
                    "probabilities": {"Straightforward": 0.6, "Contextual": 0.3, "Complex": 0.1},
                    "confidence": 0.6,
                }
        return DecisionEnvelope(
            job_id=request.job_id,
            stage=request.stage,
            input_id=request.input_id,
            question_set_version=QUESTION_SET_V1["version"],
            model="mock-jev",
            answers=answers,
        )


class MockGenerationService:
    def generate_json(self, prompt: str, schema_name: str) -> dict[str, Any]:
        raise DecisionError("NOT_IMPLEMENTED", "Use orchestration fixtures or the live adapter.")


def json_state_text(state: dict[str, Any]) -> str:
    text = str(state.get("text") or state.get("message") or "").lower()
    return text


def _noul_for(qid: str, text: str) -> float:
    mapping = {
        "requests_location_change": any(w in text for w in ("transfer", "move", "shift to", "bed")),
        "contains_medication_change": any(w in text for w in ("stop", "start", "prescribe", "antibiotic")),
        "requests_task_or_reminder": any(w in text for w in ("remind", "check", "hours", "follow")),
        "requests_patient_creation": any(w in text for w in ("admit", "new patient")),
        "requests_record_lookup": any(w in text for w in ("what", "show", "list")),
        "contains_multiple_patient_contexts": " and " in text and "patient" in text,
        "source_supports_action": True,
        "unresolved_ambiguity": "maybe" in text or "not sure" in text,
    }
    return 0.91 if mapping.get(qid) else 0.08


def parse_decision_response(payload: dict) -> dict[str, Any]:
    """Reject chat-completions payloads. Noul has no confidence field."""
    if "choices" in payload and "answers" not in payload:
        raise DecisionError("MALFORMED_RESPONSE", "Chat completion payload is not a decision envelope.")
    answers = payload.get("answers") or payload.get("result", {}).get("answers")
    if not isinstance(answers, dict):
        raise DecisionError("MALFORMED_RESPONSE", "Decision response missing answers.")
    normalized: dict[str, Any] = {}
    for qid, answer in answers.items():
        if not isinstance(answer, dict):
            raise DecisionError("MALFORMED_RESPONSE", f"Answer {qid} is not an object.")
        if "noul" in answer:
            if "confidence" in answer:
                raise DecisionError("MALFORMED_RESPONSE", "Noul answers must not carry a confidence field.")
            noul = answer["noul"]
            if noul is None:
                normalized[qid] = {"noul": None, "unavailable": True}
            else:
                normalized[qid] = {"noul": float(noul)}
        elif "choice" in answer:
            normalized[qid] = {
                "choice": answer.get("choice"),
                "probabilities": answer.get("probabilities") or {},
                "confidence": answer.get("confidence"),
            }
        elif "score" in answer:
            normalized[qid] = {
                "score": answer.get("score"),
                "probabilities": answer.get("probabilities") or {},
                "confidence": answer.get("confidence"),
            }
        else:
            raise DecisionError("MALFORMED_RESPONSE", f"Answer {qid} has no noul, choice, or score.")
    return normalized
