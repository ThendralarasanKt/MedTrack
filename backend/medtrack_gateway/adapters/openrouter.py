from __future__ import annotations

import json
from typing import Any

import httpx

from ..config import SETTINGS
from . import DecisionError, DecisionEnvelope, DecisionRequest, parse_decision_response


DECISIONS_URL = "https://openrouter.ai/api/alpha/decisions"
CHAT_URL = "https://openrouter.ai/api/v1/chat/completions"


class OpenRouterDecisionAdapter:
    def __init__(self, client: httpx.Client | None = None) -> None:
        self._client = client

    def decide(self, request: DecisionRequest) -> DecisionEnvelope:
        if not SETTINGS.openrouter_api_key:
            raise DecisionError("UNAVAILABLE", "OpenRouter credential is not configured on the server.")
        body = {
            "model": SETTINGS.jev_model,
            "state": request.state,
            "questions": request.questions,
        }
        response = self._post(DECISIONS_URL, body)
        answers = parse_decision_response(response)
        return DecisionEnvelope(
            job_id=request.job_id,
            stage=request.stage,
            input_id=request.input_id,
            question_set_version=request.question_set_version,
            model=str(response.get("model") or SETTINGS.jev_model),
            answers=answers,
            usage=_usage(response),
        )


class OpenRouterGenerationAdapter:
    def generate_json(self, prompt: str, schema_name: str, *, model: str | None = None) -> dict[str, Any]:
        return self.generate_with_meta(prompt, schema_name, model=model)["json"]

    def generate_with_meta(
        self, prompt: str, schema_name: str, *, model: str | None = None
    ) -> dict[str, Any]:
        """Return parsed JSON plus provider model/usage/cost metadata for evaluation logging."""
        if not SETTINGS.openrouter_api_key:
            raise DecisionError("UNAVAILABLE", "OpenRouter credential is not configured on the server.")
        requested_model = model or SETTINGS.generation_model
        body = {
            "model": requested_model,
            "messages": [
                {
                    "role": "system",
                    "content": (
                        "Return only JSON matching the MedTrack proposal bundle schema. "
                        "Never claim a clinical write occurred. Use stable target IDs, not labels."
                    ),
                },
                {"role": "user", "content": prompt},
            ],
            "response_format": {"type": "json_object"},
            # Ask OpenRouter to include usage/cost when available.
            "usage": {"include": True},
        }
        response = self._post(CHAT_URL, body)
        content = (
            (((response.get("choices") or [{}])[0]).get("message") or {}).get("content") or "{}"
        )
        try:
            parsed = json.loads(content)
        except json.JSONDecodeError as exc:
            raise DecisionError("MALFORMED_RESPONSE", "Generative adapter did not return JSON.") from exc
        return {
            "json": parsed,
            "requestedModel": requested_model,
            "model": str(response.get("model") or requested_model),
            "provider": response.get("provider"),
            "id": response.get("id"),
            "usage": _usage(response),
            "schemaName": schema_name,
        }

    def _post(self, url: str, body: dict) -> dict:
        headers = {
            "Authorization": f"Bearer {SETTINGS.openrouter_api_key}",
            "Content-Type": "application/json",
            "HTTP-Referer": SETTINGS.openrouter_referer,
            "X-OpenRouter-Title": "MedTrack",
        }
        with httpx.Client(timeout=45.0) as client:
            result = client.post(url, headers=headers, json=body)
        if result.status_code == 429:
            raise DecisionError("RATE_LIMITED", "Provider rate limited the request.")
        if result.status_code >= 500:
            raise DecisionError("UNAVAILABLE", f"Provider failed ({result.status_code}): {_trim_body(result.text)}")
        if result.status_code >= 400:
            raise DecisionError(
                "INVALID_INPUT",
                f"Provider rejected the request ({result.status_code}): {_trim_body(result.text)}",
            )
        return result.json()


# Bind _post for the decision adapter.
OpenRouterDecisionAdapter._post = OpenRouterGenerationAdapter._post  # type: ignore[method-assign]


def _trim_body(text: str, limit: int = 500) -> str:
    cleaned = " ".join((text or "").split())
    if len(cleaned) <= limit:
        return cleaned or "(empty body)"
    return cleaned[: limit - 1] + "…"


def _usage(payload: dict) -> dict[str, Any]:
    """Normalize OpenRouter usage; preserve cost when the provider returns it."""
    usage = payload.get("usage") or {}
    prompt_tokens = usage.get("prompt_tokens") or usage.get("input_tokens")
    completion_tokens = usage.get("completion_tokens") or usage.get("output_tokens")
    total_tokens = usage.get("total_tokens")
    if total_tokens is None and prompt_tokens is not None and completion_tokens is not None:
        try:
            total_tokens = int(prompt_tokens) + int(completion_tokens)
        except (TypeError, ValueError):
            total_tokens = None
    # OpenRouter may return cost as usage.cost (USD) or nested cost_details.
    cost = usage.get("cost")
    if cost is None and isinstance(usage.get("cost_details"), dict):
        cost = usage["cost_details"].get("upstream_inference_cost")
    if cost is None:
        cost = payload.get("cost")
    out: dict[str, Any] = {
        "prompt_tokens": prompt_tokens,
        "completion_tokens": completion_tokens,
        "total_tokens": total_tokens,
        "costUsd": float(cost) if cost is not None else None,
        "native": {
            k: usage.get(k)
            for k in ("cost", "cost_details", "prompt_tokens_details", "completion_tokens_details")
            if k in usage
        },
    }
    return out
