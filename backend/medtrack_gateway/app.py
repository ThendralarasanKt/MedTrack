from __future__ import annotations

import hashlib
import logging
import os
from typing import Any

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from . import COMMAND_SCHEMA_VERSION, ROUTE_VERSION, __version__
from .adapters import DecisionError, QUESTION_SET_V1
from .adapters.orchestration import BoundedOrchestrator
from .auth import DIRECTORY, DeviceReplacementRequired, Principal, bootstrap_payload, parse_bearer, resolve_principal
from .config import SETTINGS, validate_runtime_settings
from .profiles import PROFILES
from .proposals import ProposalValidationError, digest_obj, validate_request_envelope
from .store import STORE, Usage, digest_bytes, job_public, new_artifact, new_job

logger = logging.getLogger("medtrack.gateway")
logging.basicConfig(level=logging.INFO)


def create_app() -> FastAPI:
    validate_runtime_settings()
    app = FastAPI(title="MedTrack Gateway", version=__version__, docs_url=None, redoc_url=None)
    orchestrator = BoundedOrchestrator()

    @app.exception_handler(HTTPException)
    async def http_exc(_, exc: HTTPException):
        detail = exc.detail if isinstance(exc.detail, dict) else {"code": "UNAUTHORIZED", "message": str(exc.detail)}
        return JSONResponse(status_code=exc.status_code, content={"error": detail})

    @app.exception_handler(RequestValidationError)
    async def valid_exc(_, exc: RequestValidationError):
        return JSONResponse(
            status_code=400,
            content={"error": {"code": "VALIDATION", "message": "Invalid request body."}},
        )

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/v1/capabilities")
    def capabilities(request: Request) -> dict[str, Any]:
        principal = resolve_principal(request)
        access = PROFILES.access_for_account(principal.account_id)
        entitled = access["entitlement"]["accessState"] == "AUTHORIZED"
        return {
            **bootstrap_payload(principal),
            "commandSchemaVersion": COMMAND_SCHEMA_VERSION,
            "compatibleClientVersions": ["1.0"],
            "routeVersion": ROUTE_VERSION,
            "enabledRoutes": [r for r in SETTINGS.enabled_routes if r not in STORE.route_disabled] if entitled else [],
            "limits": {
                "maxArtifactBytes": SETTINGS.max_artifact_bytes,
                "maxTextChars": SETTINGS.max_text_chars,
                "maxJobsPerHour": SETTINGS.max_jobs_per_account_hour,
                "maxResponseBytes": 262144,
            },
            "questionSetVersion": QUESTION_SET_V1["version"],
            "flags": {
                "sharedCharts": False,
                "clinicalSync": False,
                "whatsappIngestion": False,
                "multiDevice": False,
            },
            "commitEndpoint": None,
            "access": access,
        }

    @app.post("/v1/me/bootstrap")
    async def bootstrap_me(request: Request) -> dict[str, Any]:
        claims = parse_bearer(request)
        body = {}
        if request.headers.get("content-type", "").startswith("application/json"):
            body = await request.json()
        display_name = (body.get("displayName") if isinstance(body, dict) else None) or "Clinician"
        email = claims.get("email")
        result = PROFILES.ensure_identity(
            issuer=claims["iss"],
            subject=claims["sub"],
            account_id=None,
            user_id=None,
            display_name=display_name,
            email=email,
            email_verified=bool(claims.get("email_verified")),
        )
        identity = result["identity"]
        profile = result["profile"]
        access = PROFILES.access_for_account(profile["accountId"])
        provisioning = (
            "PRODUCT_ENABLED" if access["entitlement"]["accessState"] == "AUTHORIZED" else "ACCESS_PENDING"
        )
        _audit_profile(identity["user_id"], "profile.bootstrap", profile["profileId"])
        return {
            "identity": {
                "userId": identity["user_id"],
                "authIssuer": identity["auth_issuer"],
                "authSubject": identity["auth_subject"],
                "email": identity["email"],
                "emailVerified": bool(identity["email_verified"]),
                "status": identity["status"],
            },
            "profile": profile,
            "access": access,
            "provisioningState": provisioning,
            "created": result["created"],
        }

    @app.get("/v1/me/profile")
    def get_profile(request: Request) -> dict[str, Any]:
        principal = resolve_principal(request)
        profile = PROFILES.profile_by_user(principal.user_id)
        if profile is None:
            raise HTTPException(status_code=404, detail={"code": "NOT_FOUND", "message": "Profile not found"})
        return profile

    @app.post("/v1/me/profile")
    @app.patch("/v1/me/profile")
    async def patch_profile(request: Request) -> dict[str, Any]:
        principal = resolve_principal(request)
        body = await request.json()
        return PROFILES.patch_profile(principal.user_id, body, principal.user_id)

    @app.get("/v1/me/access")
    def get_access(request: Request) -> dict[str, Any]:
        principal = resolve_principal(request)
        return PROFILES.access_for_account(principal.account_id)

    @app.get("/v1/catalogues/specialties")
    def catalogue_specialties(request: Request, q: str = "") -> dict[str, Any]:
        resolve_principal(request)
        return {"items": PROFILES.search_specialties(q)}

    @app.get("/v1/catalogues/hospitals")
    def catalogue_hospitals(request: Request, q: str = "") -> dict[str, Any]:
        resolve_principal(request)
        return {"items": PROFILES.search_hospitals(q)}

    @app.post("/v1/devices")
    async def register_device(request: Request) -> dict[str, Any]:
        body = await request.json()
        device_id = body.get("deviceId")
        if not device_id:
            raise HTTPException(status_code=400, detail={"code": "VALIDATION", "message": "deviceId required"})
        principal = resolve_principal(request, claimed_device_id=device_id)
        replace_existing = bool(body.get("replaceExisting"))
        try:
            DIRECTORY.register_device(
                principal.account_id,
                principal.user_id,
                device_id,
                replace_existing=replace_existing,
            )
        except DeviceReplacementRequired as exc:
            raise HTTPException(
                status_code=409,
                detail={
                    "code": "DEVICE_REPLACEMENT_REQUIRED",
                    "message": "Another device is already registered. Replacement must be explicit.",
                    "activeDeviceId": exc.active_device_id,
                },
            ) from exc
        _audit(principal, "device.register", device_id)
        return {"deviceId": device_id, "status": "ACTIVE", "accountId": principal.account_id}

    @app.post("/v1/artifacts")
    async def upload_artifact(request: Request) -> dict[str, Any]:
        principal = resolve_principal(request)
        body = await request.json()
        raw = body.get("contentBase64")
        mime = body.get("mimeType") or "application/octet-stream"
        purpose = body.get("purpose") or "input"
        if not raw:
            raise HTTPException(status_code=400, detail={"code": "VALIDATION", "message": "contentBase64 required"})
        import base64

        try:
            payload = base64.b64decode(raw)
        except Exception as exc:  # noqa: BLE001
            raise HTTPException(status_code=400, detail={"code": "VALIDATION", "message": "Invalid base64"}) from exc
        if len(payload) > SETTINGS.max_artifact_bytes:
            raise HTTPException(status_code=413, detail={"code": "OVERSIZED_INPUT", "message": "Artifact exceeds size limit"})
        digest = digest_bytes(payload)
        if body.get("digest") and body["digest"] != digest:
            raise HTTPException(status_code=400, detail={"code": "VALIDATION", "message": "Digest mismatch"})
        art = new_artifact(
            account_id=principal.account_id,
            job_id=None,
            purpose=purpose,
            mime_type=mime,
            size=len(payload),
            digest=digest,
            storage_key=f"{principal.account_id}/{digest}",
            state="UPLOADED",
            payload=payload,
        )
        STORE.put_artifact(art)
        _audit(principal, "artifact.upload", art.artifact_id)
        return {
            "artifactId": art.artifact_id,
            "digest": art.digest,
            "size": art.size,
            "expiresAt": int(art.expires_at),
        }

    @app.delete("/v1/artifacts/{artifact_id}")
    def delete_artifact(artifact_id: str, request: Request) -> dict[str, str]:
        principal = resolve_principal(request)
        art = STORE.get_artifact(principal.account_id, artifact_id)
        if art is None:
            raise HTTPException(status_code=404, detail={"code": "NOT_FOUND", "message": "Artifact not found."})
        art.state = "DELETED"
        art.payload = b""
        _audit(principal, "artifact.delete", artifact_id)
        return {"status": "DELETED"}

    @app.post("/v1/inference-jobs")
    async def create_job(request: Request) -> JSONResponse:
        body = await request.json()
        principal = resolve_principal(request, claimed_device_id=body.get("deviceId"))
        if not PROFILES.has_inference(principal.account_id):
            raise HTTPException(
                status_code=403,
                detail={"code": "UNAUTHORIZED", "message": "Account is not entitled to inference."},
            )
        if "accountId" in body and body["accountId"] != principal.account_id:
            # Forged account field is ignored for lookup; never leaks another account.
            logger.info("ignored forged accountId field")
        if body.get("commandSchemaVersion") not in (None, COMMAND_SCHEMA_VERSION):
            raise HTTPException(
                status_code=400,
                detail={"code": "UNSUPPORTED_SCHEMA", "message": "commandSchemaVersion is not supported."},
            )
        if STORE.jobs_this_hour(principal.account_id) >= SETTINGS.max_jobs_per_account_hour:
            raise HTTPException(status_code=429, detail={"code": "QUOTA_EXCEEDED", "message": "Account hourly quota exceeded"})
        text = body.get("text") or ""
        if len(text) > SETTINGS.max_text_chars:
            raise HTTPException(status_code=413, detail={"code": "OVERSIZED_INPUT", "message": "Input exceeds size limit"})
        try:
            validate_request_envelope(body)
        except ProposalValidationError as exc:
            raise HTTPException(status_code=400, detail={"code": exc.code, "message": exc.message}) from exc
        request_id = body["requestId"]
        input_digest = body.get("inputDigest") or digest_obj({"text": text, "sourceRefs": body.get("sourceRefs")})
        existing = STORE.find_by_request(principal.account_id, request_id)
        if existing is not None:
            if existing.input_digest != input_digest:
                raise HTTPException(
                    status_code=409,
                    detail={"code": "CONFLICT", "message": "requestId reused with a different input digest"},
                )
            return JSONResponse(status_code=200, content=job_public(existing))
        fixture = request.headers.get("X-MedTrack-Fixture")
        job = new_job(
            account_id=principal.account_id,
            requested_by_user_id=principal.user_id,
            device_id=body.get("deviceId") or principal.device_id or "unknown-device",
            request_id=request_id,
            input_digest=input_digest,
            context_digest=body.get("contextDigest") or "",
            status="QUEUED",
            route_version=ROUTE_VERSION,
            purpose=body.get("purpose") or "SINGLE_PATIENT_CAPTURE",
            request_body=body,
        )
        STORE.put_job(job)
        STORE.count_job(principal.account_id)
        _run_job(job, orchestrator, fixture)
        _audit(principal, "job.create", job.job_id)
        return JSONResponse(status_code=201, content=job_public(job))

    @app.get("/v1/inference-jobs/{job_id}")
    def get_job(job_id: str, request: Request) -> dict[str, Any]:
        principal = resolve_principal(request)
        job = STORE.get_job(principal.account_id, job_id)
        if job is None:
            raise HTTPException(status_code=404, detail={"code": "NOT_FOUND", "message": "Job not found."})
        STORE.cleanup()
        job = STORE.get_job(principal.account_id, job_id)
        if job is None:
            raise HTTPException(status_code=404, detail={"code": "NOT_FOUND", "message": "Job not found."})
        return job_public(job)

    @app.post("/v1/inference-jobs/{job_id}/cancel")
    def cancel_job(job_id: str, request: Request) -> dict[str, Any]:
        principal = resolve_principal(request)
        job = STORE.get_job(principal.account_id, job_id)
        if job is None:
            raise HTTPException(status_code=404, detail={"code": "NOT_FOUND", "message": "Job not found."})
        job.cancelled = True
        job.status = "CANCELLED"
        job.error_code = "CANCELLED"
        job.result = None
        _audit(principal, "job.cancel", job.job_id)
        return job_public(job)

    @app.post("/v1/ops/cleanup")
    def cleanup(request: Request) -> dict[str, Any]:
        resolve_principal(request)
        removed = STORE.cleanup()
        return {"removedArtifacts": len(set(removed))}

    @app.post("/v1/ops/disable-route")
    async def disable_route(request: Request) -> dict[str, Any]:
        principal = resolve_principal(request)
        body = await request.json()
        route = body.get("route")
        if route:
            STORE.route_disabled.add(route)
        _audit(principal, "route.disable", route or "")
        return {"disabled": sorted(STORE.route_disabled)}

    return app


def _run_job(job, orchestrator: BoundedOrchestrator, fixture: str | None) -> None:
    job.status = "RUNNING"
    job.claimed_by = "worker-1"
    try:
        if fixture == "timeout":
            raise DecisionError("TRANSIENT_PROVIDER", "Provider timed out before a proposal was produced.")
        result = orchestrator.run(job.job_id, job.request_body, fixture=fixture)
        if job.cancelled:
            job.status = "CANCELLED"
            job.error_code = "CANCELLED"
            job.result = None
            return
        job.status = "SUCCEEDED"
        job.result = result
        STORE.record_usage(
            Usage(
                account_id=job.account_id,
                job_id=job.job_id,
                provider="mock" if not SETTINGS.openrouter_api_key else "openrouter",
                model="mock-jev",
                units=1,
                recorded_at=job.created_at,
            )
        )
    except ProposalValidationError as exc:
        job.status = "FAILED"
        job.error_code = exc.code
        job.result = {"error": {"code": exc.code, "message": exc.message}}
    except DecisionError as exc:
        job.status = "FAILED"
        job.error_code = exc.code
        job.result = {"error": {"code": exc.code, "message": exc.message}}
    except Exception:  # noqa: BLE001
        logger.exception("job-failed")
        job.status = "FAILED"
        job.error_code = "TRANSIENT_PROVIDER"
        job.result = {"error": {"code": "TRANSIENT_PROVIDER", "message": "Job failed"}}
    job.claimed_by = None


def _audit(principal: Principal, action: str, target: str) -> None:
    logger.info(
        "audit action=%s account=%s user=%s target=%s",
        action,
        principal.account_id,
        principal.user_id,
        target,
    )


def _audit_profile(user_id: str, action: str, target: str) -> None:
    logger.info("audit action=%s user=%s target=%s", action, user_id, target)


app = create_app()


def run() -> None:
    import uvicorn

    uvicorn.run("medtrack_gateway.app:app", host="127.0.0.1", port=int(os.environ.get("PORT", "8088")), reload=False)
