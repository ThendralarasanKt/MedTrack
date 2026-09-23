from __future__ import annotations

import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
BACKEND = REPO / "backend"
for path in (str(ROOT), str(BACKEND)):
    if path not in sys.path:
        sys.path.insert(0, path)

from workbench.app import create_app  # noqa: E402
from workbench.config import load_config  # noqa: E402
from workbench.repository.store import WorkbenchStore  # noqa: E402
from workbench.routing import select_routes  # noqa: E402


@pytest.fixture()
def client(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    from medtrack_gateway.config import SETTINGS

    monkeypatch.setattr(SETTINGS, "openrouter_api_key", "")
    store = WorkbenchStore(tmp_path / ".workbench")
    store.reset()
    app = create_app(store=store)
    return TestClient(app)


def test_status_reports_mock(client: TestClient):
    res = client.get("/workbench/api/status")
    assert res.status_code == 200
    body = res.json()
    assert body["modeDefault"] == "MOCK"
    assert body["providerAvailable"] is False
    assert body["openRouterKeyConfigured"] is False
    assert body["openRouterKeyExposed"] is False
    assert body["careWritePath"] is False
    assert "MOCK" in body["banner"]
    assert body["config"]["questionSetVersion"] == "intent-v1"
    assert body["config"]["routingPolicyVersion"] == "routing-v1"
    assert body["nextStep"]


def test_run_success_fixture(client: TestClient):
    cases = client.get("/workbench/api/cases").json()["items"]
    assert cases, "starter cases should be seeded"
    target = next(c for c in cases if c["id"] == "case-bed-transfer")
    res = client.post(
        f"/workbench/api/cases/{target['id']}/run",
        json={"mode": "MOCK", "dataClassification": "SYNTHETIC", "fixtureTag": "success"},
    )
    assert res.status_code == 200, res.text
    run = res.json()
    assert run["mode"] == "MOCK"
    assert run["clinicalWriteClaimed"] is False
    stages = [s["stage"] for s in run["trace"]]
    assert stages == [
        "preflight",
        "jev_state",
        "jev_decisions",
        "routing",
        "model_configuration",
        "proposal",
        "validation",
        "usage_summary",
    ]
    assert run["proposal"] is not None
    assert run["proposal"]["commandSchemaVersion"] == "care-commands-1"
    assert run["validation"]["ok"] is True
    assert run["usage"]["totals"]["providerCalls"] == 0
    assert run["usage"]["totals"]["costUsd"] == 0.0
    routing = next(s for s in run["trace"] if s["stage"] == "routing")
    assert routing["detail"]["reasons"]
    assert routing["detail"]["primary"]["capability"]


def test_review_is_evaluation_only(client: TestClient):
    import workbench.repository.store as store_mod
    import workbench.app as app_mod
    import workbench.coordinator.mock_runner as coord_mod
    import workbench.coordinator.live_runner as live_mod

    for mod in (store_mod, app_mod, coord_mod, live_mod):
        assert not hasattr(mod, "CareWritePath")
        text = Path(mod.__file__).read_text(encoding="utf-8")
        assert "import CareWritePath" not in text
        assert "from medtrack_gateway" in text or "sqlite3" in text or "FastAPI" in text

    cases = client.get("/workbench/api/cases").json()["items"]
    run = client.post(
        f"/workbench/api/cases/{cases[0]['id']}/run",
        json={"mode": "MOCK", "fixtureTag": "success"},
    ).json()
    review = client.post(
        f"/workbench/api/case-runs/{run['id']}/review",
        json={"decision": "Simulate approval", "comment": "Looks reasonable for eval"},
    )
    assert review.status_code == 200
    body = review.json()
    assert body["clinicalWrite"] is False
    assert "not invoked" in body["note"].lower()
    fetched = client.get(f"/workbench/api/runs/{run['id']}").json()
    assert fetched["clinicalWriteClaimed"] is False
    assert len(fetched["reviews"]) >= 1


def test_rejects_non_synthetic_and_live_without_key(client: TestClient):
    bad_class = client.post(
        "/workbench/api/cases",
        json={
            "title": "bad",
            "dataClassification": "REAL",
            "inputText": "x",
        },
    )
    assert bad_class.status_code == 400
    assert bad_class.json()["error"]["code"] == "FORBIDDEN"

    cases = client.get("/workbench/api/cases").json()["items"]
    bad_mode = client.post(
        f"/workbench/api/cases/{cases[0]['id']}/run",
        json={"mode": "LIVE", "dataClassification": "SYNTHETIC"},
    )
    assert bad_mode.status_code == 400
    assert bad_mode.json()["error"]["code"] == "VALIDATION"

    live_run = client.post(
        f"/workbench/api/cases/{cases[0]['id']}/run",
        json={"mode": "LIVE_SYNTHETIC", "dataClassification": "SYNTHETIC"},
    )
    assert live_run.status_code == 400
    assert live_run.json()["error"]["code"] == "KEY_REQUIRED"


def test_proposal_fixture_passes_shared_validator():
    from medtrack_gateway.proposals import validate_proposal_bundle
    from medtrack_gateway.config import CONTRACTS_DIR
    import json

    bundle = json.loads((CONTRACTS_DIR / "fixtures" / "success.proposal.json").read_text(encoding="utf-8"))
    warnings = validate_proposal_bundle(bundle)
    assert isinstance(warnings, list)


def test_workbench_page_renders(client: TestClient):
    res = client.get("/workbench")
    assert res.status_code == 200
    assert "MOCK — NO PROVIDER CALLS" in res.text
    assert "Simulate approval" in res.text or "reviewDecision" in res.text
    assert "Routing inspector" in res.text
    assert "LIVE_SYNTHETIC" in res.text


def test_config_api_and_load(client: TestClient):
    res = client.get("/workbench/api/config")
    assert res.status_code == 200
    body = res.json()
    assert body["active"]["questionSetVersion"] == "intent-v1"
    assert body["routingPolicy"]["version"] == "routing-v1"
    assert body["routingPolicy"]["rules"]
    assert any(m["route_id"] == "administrative-extraction-v1" for m in body["models"])

    cfg = load_config(ROOT, active_experiment_id="mock-default")
    assert cfg.question_set()["version"] == "intent-v1"
    assert "administrative-extraction-v1" in cfg.prompts or "administrative-extraction-v1" in cfg.models


def test_openrouter_usage_captures_cost():
    from medtrack_gateway.adapters.openrouter import _usage

    parsed = _usage(
        {
            "usage": {
                "prompt_tokens": 12,
                "completion_tokens": 8,
                "cost": 0.00042,
            }
        }
    )
    assert parsed["prompt_tokens"] == 12
    assert parsed["completion_tokens"] == 8
    assert parsed["total_tokens"] == 20
    assert parsed["costUsd"] == 0.00042


def test_workbench_uses_backend_routing_policy():
    import workbench.routing as shim
    from medtrack_gateway.orchestration.routing_policy import select_routes as production

    assert shim.select_routes is production
    cfg = load_config(ROOT)
    policy = cfg.routing_policy()
    result = select_routes(
        policy,
        answers={},
        purpose="SINGLE_PATIENT_CAPTURE",
        identity_resolved=False,
        text="Move bed 12 to ICU",
    )
    assert result["primary"]["capability"] == "CLARIFICATION"
    assert "identity" in result["primary"]["reason"].lower()
