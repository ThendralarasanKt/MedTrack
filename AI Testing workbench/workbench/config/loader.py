from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


class ConfigError(RuntimeError):
    pass


@dataclass
class WorkbenchConfig:
    root: Path
    question_sets: dict[str, dict[str, Any]] = field(default_factory=dict)
    routing_policies: dict[str, dict[str, Any]] = field(default_factory=dict)
    models: dict[str, dict[str, Any]] = field(default_factory=dict)
    experiments: dict[str, dict[str, Any]] = field(default_factory=dict)
    prompts: dict[str, str] = field(default_factory=dict)
    active_experiment_id: str = "mock-default"

    def active_experiment(self) -> dict[str, Any]:
        exp = self.experiments.get(self.active_experiment_id)
        if not exp:
            raise ConfigError(f"Active experiment {self.active_experiment_id} missing")
        return exp

    def question_set(self, version: str | None = None) -> dict[str, Any]:
        ver = version or self.active_experiment()["questionSetVersion"]
        qs = self.question_sets.get(ver)
        if not qs:
            raise ConfigError(f"Question set {ver} missing")
        return qs

    def routing_policy(self, version: str | None = None) -> dict[str, Any]:
        ver = version or self.active_experiment()["routingPolicyVersion"]
        policy = self.routing_policies.get(ver)
        if not policy:
            raise ConfigError(f"Routing policy {ver} missing")
        return policy

    def model_for_route(self, route_id: str | None) -> dict[str, Any] | None:
        if not route_id:
            return None
        model = self.models.get(route_id)
        if model is None:
            raise ConfigError(f"Model configuration {route_id} missing")
        return model

    def prompt_text(self, prompt_version: str) -> str:
        text = self.prompts.get(prompt_version)
        if text is None:
            raise ConfigError(f"Prompt {prompt_version} missing")
        return text

    def public_status(self) -> dict[str, Any]:
        exp = self.active_experiment()
        return {
            "activeExperimentId": exp["id"],
            "questionSetVersion": exp["questionSetVersion"],
            "routingPolicyVersion": exp["routingPolicyVersion"],
            "modelRoutes": sorted(self.models.keys()),
            "promptVersions": sorted(self.prompts.keys()),
            "experiments": sorted(self.experiments.keys()),
        }


def load_config(root: Path, active_experiment_id: str = "mock-default") -> WorkbenchConfig:
    cfg = WorkbenchConfig(root=root, active_experiment_id=active_experiment_id)
    qs_dir = root / "config" / "question-sets"
    for path in qs_dir.glob("*.json"):
        data = json.loads(path.read_text(encoding="utf-8"))
        cfg.question_sets[data["version"]] = data
    for path in (root / "config" / "routing-policies").glob("*.json"):
        data = json.loads(path.read_text(encoding="utf-8"))
        cfg.routing_policies[data["version"]] = data
    for path in (root / "config" / "model-registry").glob("*.json"):
        data = json.loads(path.read_text(encoding="utf-8"))
        cfg.models[data["route_id"]] = data
    for path in (root / "config" / "experiment-configs").glob("*.json"):
        data = json.loads(path.read_text(encoding="utf-8"))
        cfg.experiments[data["id"]] = data
    for path in (root / "prompts").glob("*.md"):
        cfg.prompts[path.stem] = path.read_text(encoding="utf-8")
    validate_config(cfg)
    return cfg


def validate_config(cfg: WorkbenchConfig) -> None:
    if not cfg.question_sets:
        raise ConfigError("No question sets loaded")
    if not cfg.routing_policies:
        raise ConfigError("No routing policies loaded")
    if not cfg.experiments:
        raise ConfigError("No experiment configs loaded")
    if cfg.active_experiment_id not in cfg.experiments:
        raise ConfigError(f"Unknown active experiment {cfg.active_experiment_id}")
    for qs in cfg.question_sets.values():
        for qid, q in (qs.get("questions") or {}).items():
            qtype = q.get("type")
            criteria = q.get("criteria")
            if qtype == "noul":
                if not isinstance(criteria, dict) or "true" not in criteria or "false" not in criteria:
                    raise ConfigError(
                        f"Question {qid} (noul) requires criteria.true and criteria.false for OpenRouter Decisions"
                    )
            elif qtype == "choice":
                if not isinstance(criteria, dict) or len(criteria) < 2:
                    raise ConfigError(f"Question {qid} (choice) requires criteria option map")
            elif qtype == "score":
                if not isinstance(criteria, list) or len(criteria) < 2:
                    raise ConfigError(f"Question {qid} (score) requires criteria level array")
    for exp in cfg.experiments.values():
        if exp["questionSetVersion"] not in cfg.question_sets:
            raise ConfigError(f"Experiment {exp['id']} references missing question set")
        if exp["routingPolicyVersion"] not in cfg.routing_policies:
            raise ConfigError(f"Experiment {exp['id']} references missing routing policy")
        policy = cfg.routing_policies[exp["routingPolicyVersion"]]
        if policy.get("questionSetVersion") != exp["questionSetVersion"]:
            raise ConfigError(
                f"Routing policy {policy['version']} questionSetVersion mismatch for experiment {exp['id']}"
            )
        for capability, route_id in (policy.get("modelRouteMap") or {}).items():
            if route_id is None:
                continue
            if route_id not in cfg.models:
                raise ConfigError(f"Route map {capability} -> {route_id} missing from model registry")
            model = cfg.models[route_id]
            prompt_version = model.get("prompt_version")
            if prompt_version and prompt_version not in cfg.prompts:
                raise ConfigError(f"Model {route_id} prompt {prompt_version} missing")
            if model.get("capability") != capability:
                raise ConfigError(f"Model {route_id} capability mismatch for {capability}")
