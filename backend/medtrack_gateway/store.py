from __future__ import annotations

import hashlib
import json
import threading
import time
import uuid
from dataclasses import dataclass, field
from typing import Any


TERMINAL = {"SUCCEEDED", "FAILED", "CANCELLED", "EXPIRED"}


def _now() -> float:
    return time.time()


def _id(prefix: str) -> str:
    return f"{prefix}-{uuid.uuid4()}"


@dataclass
class Job:
    job_id: str
    account_id: str
    requested_by_user_id: str
    device_id: str
    request_id: str
    input_digest: str
    context_digest: str
    status: str
    created_at: float
    expires_at: float
    route_version: str
    purpose: str
    request_body: dict
    result: dict | None = None
    error_code: str | None = None
    claimed_by: str | None = None
    retry_count: int = 0
    cancelled: bool = False


@dataclass
class Artifact:
    artifact_id: str
    account_id: str
    job_id: str | None
    purpose: str
    mime_type: str
    size: int
    digest: str
    storage_key: str
    expires_at: float
    state: str
    created_at: float
    payload: bytes = field(repr=False, default=b"")


@dataclass
class Usage:
    account_id: str
    job_id: str
    provider: str
    model: str
    units: int
    recorded_at: float


class GatewayStore:
    def __init__(self) -> None:
        self._lock = threading.RLock()
        self.jobs: dict[str, Job] = {}
        self.jobs_by_request: dict[tuple[str, str], str] = {}
        self.artifacts: dict[str, Artifact] = {}
        self.usage: list[Usage] = []
        self.hour_counts: dict[tuple[str, int], int] = {}
        self.route_disabled: set[str] = set()
        self.cache: dict[str, Any] = {}

    def reset(self) -> None:
        with self._lock:
            self.jobs.clear()
            self.jobs_by_request.clear()
            self.artifacts.clear()
            self.usage.clear()
            self.hour_counts.clear()
            self.route_disabled.clear()
            self.cache.clear()

    def count_job(self, account_id: str) -> int:
        hour = int(_now() // 3600)
        key = (account_id, hour)
        with self._lock:
            self.hour_counts[key] = self.hour_counts.get(key, 0) + 1
            return self.hour_counts[key]

    def jobs_this_hour(self, account_id: str) -> int:
        hour = int(_now() // 3600)
        return self.hour_counts.get((account_id, hour), 0)

    def put_job(self, job: Job) -> Job:
        with self._lock:
            self.jobs[job.job_id] = job
            self.jobs_by_request[(job.account_id, job.request_id)] = job.job_id
            return job

    def get_job(self, account_id: str, job_id: str) -> Job | None:
        job = self.jobs.get(job_id)
        if job is None or job.account_id != account_id:
            return None
        return job

    def find_by_request(self, account_id: str, request_id: str) -> Job | None:
        job_id = self.jobs_by_request.get((account_id, request_id))
        if not job_id:
            return None
        return self.jobs.get(job_id)

    def put_artifact(self, artifact: Artifact) -> Artifact:
        with self._lock:
            self.artifacts[artifact.artifact_id] = artifact
            return artifact

    def get_artifact(self, account_id: str, artifact_id: str) -> Artifact | None:
        art = self.artifacts.get(artifact_id)
        if art is None or art.account_id != account_id:
            return None
        return art

    def record_usage(self, usage: Usage) -> None:
        with self._lock:
            self.usage.append(usage)

    def cleanup(self, now: float | None = None) -> list[str]:
        now = now or _now()
        removed: list[str] = []
        with self._lock:
            for job in list(self.jobs.values()):
                if now >= job.expires_at and job.status not in TERMINAL:
                    job.status = "EXPIRED"
                    job.error_code = "EXPIRED"
                terminal_at = job.created_at
                if job.status in TERMINAL:
                    max_keep = max(job.expires_at, job.created_at + 24 * 3600)
                    # Purge artifacts within 24h of terminal and never later than 48h from creation.
                    cutoff = min(job.created_at + 48 * 3600, now)
                    if now >= job.created_at + 24 * 3600 or now >= job.created_at + 48 * 3600:
                        for art in list(self.artifacts.values()):
                            if art.job_id == job.job_id or (
                                art.account_id == job.account_id and art.created_at <= cutoff and art.state != "DELETED"
                            ):
                                if art.job_id == job.job_id and now >= min(
                                    job.created_at + 48 * 3600,
                                    (job.created_at if job.status in TERMINAL else job.created_at) + 24 * 3600,
                                ):
                                    art.state = "DELETED"
                                    art.payload = b""
                                    removed.append(art.artifact_id)
                if now >= job.created_at + 48 * 3600:
                    for art in list(self.artifacts.values()):
                        if art.account_id == job.account_id and (
                            art.job_id == job.job_id or art.created_at <= job.created_at + 48 * 3600
                        ):
                            if art.state != "DELETED":
                                art.state = "DELETED"
                                art.payload = b""
                                removed.append(art.artifact_id)
            for art in list(self.artifacts.values()):
                if art.state != "DELETED" and now >= art.expires_at:
                    art.state = "DELETED"
                    art.payload = b""
                    removed.append(art.artifact_id)
        return removed

    def cache_put(self, account_id: str, key: str, value: Any) -> None:
        self.cache[f"{account_id}:{key}"] = value

    def cache_get(self, account_id: str, key: str) -> Any:
        return self.cache.get(f"{account_id}:{key}")


STORE = GatewayStore()


def digest_bytes(data: bytes) -> str:
    return "sha256:" + hashlib.sha256(data).hexdigest()


def new_job(**kwargs: Any) -> Job:
    created = _now()
    return Job(
        job_id=_id("job"),
        created_at=created,
        expires_at=created + 48 * 3600,
        **kwargs,
    )


def new_artifact(**kwargs: Any) -> Artifact:
    created = _now()
    return Artifact(
        artifact_id=_id("art"),
        created_at=created,
        expires_at=kwargs.pop("expires_at", created + 48 * 3600),
        **kwargs,
    )


def job_public(job: Job) -> dict:
    body: dict[str, Any] = {
        "jobId": job.job_id,
        "status": job.status,
        "requestId": job.request_id,
        "createdAt": int(job.created_at),
        "expiresAt": int(job.expires_at),
        "routeVersion": job.route_version,
        "errorCode": job.error_code,
    }
    if job.status == "SUCCEEDED" and job.result is not None and not job.cancelled:
        body["result"] = job.result
    return body
