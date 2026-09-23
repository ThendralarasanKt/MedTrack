from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from fastapi.exceptions import RequestValidationError
from starlette.middleware.base import BaseHTTPMiddleware

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
BACKEND = REPO / "backend"
for path in (str(ROOT), str(BACKEND)):
    if path not in sys.path:
        sys.path.insert(0, path)

from medtrack_gateway import COMMAND_SCHEMA_VERSION, ROUTE_VERSION, __version__ as gateway_version
from medtrack_gateway.config import SETTINGS

from workbench.config import load_config
from workbench.coordinator.mock_runner import MockCoordinator
from workbench.coordinator.live_runner import LiveSyntheticCoordinator, openrouter_key_configured
from workbench.repository.store import WorkbenchStore

WORKBENCH_DIR = ROOT
DATA_DIR = WORKBENCH_DIR / ".workbench"
SEED_PATH = WORKBENCH_DIR / "datasets" / "synthetic" / "starter_cases.json"
TEMPLATES = Jinja2Templates(directory=str(WORKBENCH_DIR / "workbench" / "web" / "templates"))

REVIEW_DECISIONS = {
    "Correct",
    "Incorrect",
    "Partially correct",
    "Should clarify",
    "Unsupported by source",
    "Simulate approval",
}


class LoopbackOnlyMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        client = request.client.host if request.client else ""
        if client not in {"127.0.0.1", "::1", "testclient"}:
            return JSONResponse(
                status_code=403,
                content={"error": {"code": "FORBIDDEN", "message": "Workbench binds to loopback only."}},
            )
        return await call_next(request)


def create_app(store: WorkbenchStore | None = None) -> FastAPI:
    app = FastAPI(title="MedTrack AI Testing Workbench", docs_url=None, redoc_url=None)
    app.add_middleware(LoopbackOnlyMiddleware)
    config = load_config(WORKBENCH_DIR, active_experiment_id="mock-default")
    app.state.config = config
    app.state.store = store or WorkbenchStore(DATA_DIR)
    app.state.mock = MockCoordinator(config)
    app.state.live = LiveSyntheticCoordinator(config)
    _seed(app.state.store)
    app.mount(
        "/workbench/static",
        StaticFiles(directory=str(WORKBENCH_DIR / "workbench" / "web" / "static")),
        name="workbench-static",
    )

    @app.exception_handler(HTTPException)
    async def http_exc(_, exc: HTTPException):
        detail = exc.detail if isinstance(exc.detail, dict) else {"code": "ERROR", "message": str(exc.detail)}
        return JSONResponse(status_code=exc.status_code, content={"error": detail})

    @app.exception_handler(RequestValidationError)
    async def valid_exc(_, exc: RequestValidationError):
        return JSONResponse(
            status_code=400,
            content={"error": {"code": "VALIDATION", "message": "Invalid request body."}},
        )

    @app.get("/workbench", response_class=HTMLResponse)
    async def workbench_page(request: Request):
        key_ready = openrouter_key_configured()
        banner = (
            "LIVE PROVIDER AVAILABLE — default still MOCK"
            if key_ready
            else "MOCK — NO PROVIDER CALLS"
        )
        cases = app.state.store.list_cases()
        return TEMPLATES.TemplateResponse(
            request,
            "index.html",
            {
                "banner": banner,
                "cases": cases,
                "review_decisions": sorted(REVIEW_DECISIONS),
                "key_ready": key_ready,
                "config_summary": app.state.config.public_status(),
            },
        )

    @app.get("/workbench/api/status")
    def status() -> dict[str, Any]:
        key_ready = openrouter_key_configured()
        return {
            "modeDefault": "MOCK",
            "modes": {
                "MOCK": True,
                "LIVE_SYNTHETIC": key_ready,
            },
            "providerAvailable": key_ready,
            "openRouterKeyConfigured": key_ready,
            "banner": "MOCK — NO PROVIDER CALLS" if not key_ready else "LIVE SYNTHETIC READY WHEN SELECTED",
            "commandSchemaVersion": COMMAND_SCHEMA_VERSION,
            "routeVersion": ROUTE_VERSION,
            "questionSetVersion": app.state.config.question_set()["version"],
            "routingPolicyVersion": app.state.config.routing_policy()["version"],
            "gatewayVersion": gateway_version,
            "jevModelConfigured": SETTINGS.jev_model,
            "generationModelConfigured": SETTINGS.generation_model,
            "androidWriteEnabled": False,
            "careWritePath": False,
            "openRouterKeyExposed": False,
            "config": app.state.config.public_status(),
            "nextStep": None
            if key_ready
            else "Set OPENROUTER_API_KEY in the environment, restart the workbench, then choose LIVE_SYNTHETIC.",
        }

    @app.get("/workbench/api/config")
    def config_inspect() -> dict[str, Any]:
        cfg = app.state.config
        policy = cfg.routing_policy()
        return {
            "active": cfg.public_status(),
            "questionSet": {
                "version": cfg.question_set()["version"],
                "questionIds": sorted((cfg.question_set().get("questions") or {}).keys()),
            },
            "routingPolicy": {
                "version": policy["version"],
                "noulThreshold": policy.get("noulThreshold"),
                "rules": [
                    {"id": r.get("id"), "route": r.get("route"), "reason": r.get("reason")}
                    for r in policy.get("rules") or []
                ],
                "defaultRoute": policy.get("defaultRoute"),
                "modelRouteMap": policy.get("modelRouteMap"),
            },
            "models": [
                {
                    "route_id": m["route_id"],
                    "capability": m["capability"],
                    "model_id": m["model_id"],
                    "prompt_version": m.get("prompt_version"),
                    "enabled": m.get("enabled", True),
                }
                for m in cfg.models.values()
            ],
        }

    @app.get("/workbench/api/cases")
    def list_cases() -> dict[str, Any]:
        return {"items": app.state.store.list_cases()}

    @app.post("/workbench/api/cases")
    async def create_case(request: Request) -> dict[str, Any]:
        body = await request.json()
        classification = (body.get("dataClassification") or "SYNTHETIC").upper()
        if classification != "SYNTHETIC":
            raise HTTPException(
                status_code=400,
                detail={"code": "FORBIDDEN", "message": "Only SYNTHETIC cases are allowed."},
            )
        try:
            return app.state.store.create_case(body)
        except ValueError as exc:
            raise HTTPException(status_code=400, detail={"code": "VALIDATION", "message": str(exc)}) from exc

    @app.post("/workbench/api/cases/{case_id}/run")
    async def run_case(case_id: str, request: Request) -> dict[str, Any]:
        body = {}
        if request.headers.get("content-type", "").startswith("application/json"):
            raw = await request.body()
            if raw:
                body = json.loads(raw)
        mode = (body.get("mode") or "MOCK").upper()
        if mode not in {"MOCK", "LIVE_SYNTHETIC"}:
            raise HTTPException(
                status_code=400,
                detail={"code": "VALIDATION", "message": "mode must be MOCK or LIVE_SYNTHETIC"},
            )
        if body.get("dataClassification") and str(body["dataClassification"]).upper() != "SYNTHETIC":
            raise HTTPException(
                status_code=400,
                detail={"code": "FORBIDDEN", "message": "Non-synthetic classification rejected."},
            )
        if mode == "LIVE_SYNTHETIC" and not openrouter_key_configured():
            raise HTTPException(
                status_code=400,
                detail={
                    "code": "KEY_REQUIRED",
                    "message": "Set OPENROUTER_API_KEY, restart the workbench, then retry LIVE_SYNTHETIC.",
                },
            )
        case = app.state.store.get_case(case_id)
        if case is None:
            raise HTTPException(status_code=404, detail={"code": "NOT_FOUND", "message": "Case not found"})
        if body.get("inputText") is not None:
            case = {**case, "inputText": body["inputText"]}
        if body.get("fixtureTag") is not None:
            case = {**case, "fixtureTag": body["fixtureTag"] or None}
        if body.get("context") is not None:
            case = {**case, "context": body["context"]}
        try:
            if mode == "LIVE_SYNTHETIC":
                # Live path ignores fixture tags — real Jev/generation.
                case = {**case, "fixtureTag": None}
                result = app.state.live.run_case(case, mode="LIVE_SYNTHETIC")
            else:
                result = app.state.mock.run_case(case, mode="MOCK")
        except ValueError as exc:
            raise HTTPException(status_code=400, detail={"code": "VALIDATION", "message": str(exc)}) from exc
        saved = app.state.store.save_run(result)
        return saved

    @app.get("/workbench/api/runs/{run_id}")
    def get_run(run_id: str) -> dict[str, Any]:
        run = app.state.store.get_run(run_id)
        if run is None:
            raise HTTPException(status_code=404, detail={"code": "NOT_FOUND", "message": "Run not found"})
        return run

    @app.post("/workbench/api/case-runs/{run_id}/review")
    async def review_run(run_id: str, request: Request) -> dict[str, Any]:
        body = await request.json()
        decision = body.get("decision")
        if decision not in REVIEW_DECISIONS:
            raise HTTPException(
                status_code=400,
                detail={"code": "VALIDATION", "message": f"decision must be one of {sorted(REVIEW_DECISIONS)}"},
            )
        try:
            return app.state.store.add_review(run_id, body)
        except KeyError as exc:
            raise HTTPException(status_code=404, detail={"code": "NOT_FOUND", "message": "Run not found"}) from exc

    @app.post("/workbench/api/evaluations")
    def evaluations_stub() -> JSONResponse:
        return JSONResponse(
            status_code=501,
            content={"error": {"code": "NOT_IMPLEMENTED", "message": "Batch evaluation arrives in WB-04."}},
        )

    @app.post("/workbench/api/replay")
    def replay_stub() -> JSONResponse:
        return JSONResponse(
            status_code=501,
            content={"error": {"code": "NOT_IMPLEMENTED", "message": "Replay mode arrives in a later phase."}},
        )

    return app


def _seed(store: WorkbenchStore) -> None:
    if not SEED_PATH.exists():
        return
    cases = json.loads(SEED_PATH.read_text(encoding="utf-8"))
    store.seed_if_empty(cases)


app = create_app()
