from __future__ import annotations

from typing import Any


class RegistryError(ValueError):
    pass


def model_for_route(models: dict[str, dict[str, Any]], route_id: str | None) -> dict[str, Any] | None:
    if not route_id:
        return None
    model = models.get(route_id)
    if model is None:
        raise RegistryError(f"Model configuration {route_id} missing")
    if not model.get("enabled", True):
        raise RegistryError(f"Model configuration {route_id} is disabled")
    return model


def generation_candidates(primary: str | None, fallbacks: list[str] | None) -> list[str]:
    ordered: list[str] = []
    for model in [primary, *(fallbacks or [])]:
        if model and model not in ordered:
            ordered.append(model)
    return ordered
