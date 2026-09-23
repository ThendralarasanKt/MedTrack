from __future__ import annotations

import base64
import json
import time
from dataclasses import dataclass

from fastapi import HTTPException, Request

from .config import SETTINGS, load_provisioning, validate_runtime_settings
from .firebase import FirebaseTokenError, firebase_verifier
from .identity_store import IdentityStore
from .profiles import PROFILES


@dataclass(frozen=True)
class Principal:
    user_id: str
    account_id: str
    auth_issuer: str
    auth_subject: str
    device_id: str | None
    role: str
    membership_status: str
    device_status: str
    account_status: str
    actor_person_id: str | None = None


class DeviceReplacementRequired(Exception):
    def __init__(self, active_device_id: str) -> None:
        super().__init__(active_device_id)
        self.active_device_id = active_device_id


class IdentityDirectory:
    def __init__(self, store: IdentityStore | None = None) -> None:
        data = load_provisioning(SETTINGS.provisioning_path)
        self._rows = list(data.get("identities", []))
        self._store = store or IdentityStore(SETTINGS.db_path)

    def provisioned(self, issuer: str, subject: str) -> dict | None:
        for row in self._rows:
            if row["authIssuer"] == issuer and row["authSubject"] == subject:
                return row
        return None

    def disable_membership(self, account_id: str, user_id: str) -> None:
        self._store.disable_membership(account_id, user_id)

    def revoke_device(self, account_id: str, device_id: str) -> None:
        self._store.revoke_device(account_id, device_id)

    def disable_account(self, account_id: str) -> None:
        self._store.disable_account(account_id)

    def register_device(
        self,
        account_id: str,
        user_id: str,
        device_id: str,
        *,
        replace_existing: bool = False,
    ) -> str:
        previous = self._store.active_device(account_id, user_id)
        if previous and previous != device_id:
            if not replace_existing:
                raise DeviceReplacementRequired(previous)
            self.revoke_device(account_id, previous)
        self._store.register_device(account_id, user_id, device_id)
        return device_id

    def active_device(self, account_id: str, user_id: str) -> str | None:
        return self._store.active_device(account_id, user_id)

    def reset_runtime(self) -> None:
        self._store.reset()
        PROFILES.reset()


DIRECTORY = IdentityDirectory()


def parse_bearer(request: Request) -> dict:
    header = request.headers.get("Authorization", "")
    if not header.startswith("Bearer "):
        raise HTTPException(status_code=401, detail={"code": "UNAUTHENTICATED", "message": "Missing bearer token"})
    token = header[7:].strip()
    if not token:
        raise HTTPException(status_code=401, detail={"code": "UNAUTHENTICATED", "message": "Empty token"})
    if token.startswith("mt-dev."):
        if SETTINGS.env.lower() == "production" or not SETTINGS.allow_dev_tokens:
            raise HTTPException(status_code=401, detail={"code": "UNAUTHENTICATED", "message": "Dev tokens disabled"})
        return _decode_dev_token(token)
    return _decode_firebase(token)


def _decode_dev_token(token: str) -> dict:
    try:
        payload = json.loads(base64.urlsafe_b64decode(token.split(".", 1)[1] + "==").decode("utf-8"))
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=401, detail={"code": "UNAUTHENTICATED", "message": "Malformed token"}) from exc
    exp = int(payload.get("exp", 0))
    if exp and exp < int(time.time()):
        raise HTTPException(status_code=401, detail={"code": "UNAUTHENTICATED", "message": "Expired token"})
    iss = payload.get("iss") or payload.get("authIssuer")
    sub = payload.get("sub") or payload.get("authSubject")
    if not iss or not sub:
        raise HTTPException(status_code=401, detail={"code": "UNAUTHENTICATED", "message": "Token missing issuer/subject"})
    if iss == f"https://securetoken.google.com/{SETTINGS.firebase_project_id}":
        raise HTTPException(
            status_code=401,
            detail={"code": "UNAUTHENTICATED", "message": "Development tokens cannot impersonate Firebase"},
        )
    return {"iss": iss, "sub": sub, "device_id": payload.get("device_id")}


def _decode_firebase(token: str) -> dict:
    try:
        claims = firebase_verifier().verify(token)
    except FirebaseTokenError as exc:
        raise HTTPException(status_code=401, detail={"code": "UNAUTHENTICATED", "message": str(exc)}) from exc
    return {"iss": claims["iss"], "sub": claims["sub"], "device_id": claims.get("device_id")}


def resolve_principal(request: Request, claimed_device_id: str | None = None) -> Principal:
    claims = parse_bearer(request)
    row = _identity_row(claims["iss"], claims["sub"])
    if row is None:
        raise HTTPException(status_code=403, detail={"code": "UNPROVISIONED", "message": "Identity is not provisioned"})
    device_id = claimed_device_id or claims.get("device_id") or request.headers.get("X-MedTrack-Device")
    if DIRECTORY._store.user_disabled(row["userId"]):
        raise HTTPException(status_code=403, detail={"code": "UNAUTHORIZED", "message": "User disabled"})
    if DIRECTORY._store.account_disabled(row["accountId"]):
        raise HTTPException(status_code=403, detail={"code": "UNAUTHORIZED", "message": "Account disabled"})
    if DIRECTORY._store.membership_disabled(row["accountId"], row["userId"]):
        raise HTTPException(status_code=403, detail={"code": "UNAUTHORIZED", "message": "Membership disabled"})
    device_status = "ACTIVE"
    if device_id and DIRECTORY._store.device_revoked(row["accountId"], device_id):
        device_status = "REVOKED"
        raise HTTPException(status_code=403, detail={"code": "UNAUTHORIZED", "message": "Device revoked"})
    return Principal(
        user_id=row["userId"],
        account_id=row["accountId"],
        auth_issuer=row["authIssuer"],
        auth_subject=row["authSubject"],
        device_id=device_id,
        role=row.get("role", "OWNER"),
        membership_status="ACTIVE",
        device_status=device_status,
        account_status="ACTIVE",
        actor_person_id=row.get("actorPersonId"),
    )


def _identity_row(issuer: str, subject: str) -> dict | None:
    found = PROFILES.identity_by_iss_sub(issuer, subject)
    if found:
        profile = PROFILES.profile_by_user(found["user_id"])
        if profile is None:
            return None
        return {
            "userId": found["user_id"],
            "accountId": profile["accountId"],
            "authIssuer": found["auth_issuer"],
            "authSubject": found["auth_subject"],
            "role": "OWNER",
            "actorPersonId": None,
        }
    return DIRECTORY.provisioned(issuer, subject)


def encode_dev_token(issuer: str, subject: str, device_id: str | None = None, exp: int | None = None) -> str:
    payload = {"iss": issuer, "sub": subject, "exp": exp or int(time.time()) + 3600}
    if device_id:
        payload["device_id"] = device_id
    raw = base64.urlsafe_b64encode(json.dumps(payload).encode("utf-8")).decode("ascii").rstrip("=")
    return f"mt-dev.{raw}"


def bootstrap_payload(principal: Principal) -> dict:
    return {
        "accountId": principal.account_id,
        "userId": principal.user_id,
        "authIssuer": principal.auth_issuer,
        "authSubject": principal.auth_subject,
        "deviceId": principal.device_id,
        "role": principal.role,
        "actorPersonId": principal.actor_person_id,
        "membershipStatus": principal.membership_status,
        "deviceStatus": principal.device_status,
        "accountStatus": principal.account_status,
    }


validate_runtime_settings()
