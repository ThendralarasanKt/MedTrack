from __future__ import annotations

import json
import os
import sqlite3
import threading
import time
import uuid
from pathlib import Path
from typing import Any

from fastapi import HTTPException

from .config import SETTINGS, load_provisioning

FORBIDDEN_PROFILE_FIELDS = {
    "isSubscriber",
    "subscription",
    "subscriptionStatus",
    "grant",
    "grants",
    "entitlement",
    "accessState",
    "verificationStatus",
    "accountId",
    "userId",
    "profileId",
    "providerCustomerRef",
    "providerSubscriptionRef",
}

SPECIALTIES = (
    ("spec-nephrology", "local", "NEPH", "Nephrology"),
    ("spec-cardiology", "local", "CARD", "Cardiology"),
    ("spec-general-med", "local", "GM", "General Medicine"),
    ("spec-icu", "local", "ICU", "Intensive Care"),
)

HOSPITALS = (
    ("hosp-city", "City Hospital", "Mumbai", "MH", "IN"),
    ("hosp-county", "County General", "Pune", "MH", "IN"),
)

PLAN_PILOT = "plan-pilot"


def _now() -> float:
    return time.time()


def _id(prefix: str) -> str:
    return f"{prefix}-{uuid.uuid4()}"


class ProfileStore:
    def __init__(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()
        self._conn = sqlite3.connect(str(path), check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        self._conn.execute("PRAGMA journal_mode=WAL")
        self._init()
        self.seed()

    def _init(self) -> None:
        with self._lock:
            self._conn.executescript(
                """
                CREATE TABLE IF NOT EXISTS user_identities (
                    user_id TEXT PRIMARY KEY,
                    auth_issuer TEXT NOT NULL,
                    auth_subject TEXT NOT NULL,
                    email TEXT,
                    email_verified INTEGER NOT NULL DEFAULT 0,
                    status TEXT NOT NULL,
                    UNIQUE(auth_issuer, auth_subject)
                );
                CREATE TABLE IF NOT EXISTS accounts (
                    account_id TEXT PRIMARY KEY,
                    owner_user_id TEXT NOT NULL,
                    status TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS user_profiles (
                    profile_id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL UNIQUE,
                    account_id TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    preferred_name TEXT,
                    profession_code TEXT,
                    phone TEXT,
                    time_zone_id TEXT,
                    preferred_language TEXT,
                    onboarding_status TEXT NOT NULL,
                    version INTEGER NOT NULL,
                    created_at REAL NOT NULL,
                    updated_at REAL NOT NULL
                );
                CREATE TABLE IF NOT EXISTS specialty_concepts (
                    specialty_id TEXT PRIMARY KEY,
                    code_system TEXT NOT NULL,
                    code TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    active INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS profile_specialties (
                    id TEXT PRIMARY KEY,
                    profile_id TEXT NOT NULL,
                    specialty_id TEXT,
                    reported_specialty_text TEXT,
                    is_primary INTEGER NOT NULL,
                    verification_status TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS hospital_directory (
                    hospital_id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    city TEXT,
                    state_or_region TEXT,
                    country_code TEXT,
                    status TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS profile_affiliations (
                    affiliation_id TEXT PRIMARY KEY,
                    profile_id TEXT NOT NULL,
                    hospital_id TEXT,
                    reported_hospital_name TEXT,
                    reported_city TEXT,
                    department_name TEXT,
                    job_title TEXT,
                    starts_on TEXT,
                    ends_on TEXT,
                    is_primary INTEGER NOT NULL,
                    verification_status TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS plan_definitions (
                    plan_id TEXT PRIMARY KEY,
                    code TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    active INTEGER NOT NULL,
                    plan_version TEXT NOT NULL,
                    feature_policy TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS account_subscriptions (
                    subscription_id TEXT PRIMARY KEY,
                    account_id TEXT NOT NULL,
                    plan_id TEXT NOT NULL,
                    status TEXT NOT NULL,
                    provider TEXT NOT NULL,
                    period_start REAL,
                    period_end REAL,
                    cancel_at_period_end INTEGER NOT NULL DEFAULT 0,
                    created_at REAL NOT NULL
                );
                CREATE TABLE IF NOT EXISTS access_grants (
                    grant_id TEXT PRIMARY KEY,
                    account_id TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    feature_policy TEXT NOT NULL,
                    starts_at REAL NOT NULL,
                    ends_at REAL,
                    status TEXT NOT NULL,
                    granted_by TEXT NOT NULL,
                    reason TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS profile_audit (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    actor_id TEXT NOT NULL,
                    object_type TEXT NOT NULL,
                    object_id TEXT NOT NULL,
                    previous_version INTEGER,
                    new_version INTEGER NOT NULL,
                    action TEXT NOT NULL,
                    recorded_at REAL NOT NULL
                );
                CREATE TABLE IF NOT EXISTS profile_operations (
                    operation_id TEXT PRIMARY KEY,
                    profile_id TEXT NOT NULL,
                    result_json TEXT NOT NULL
                );
                """
            )
            self._conn.commit()

    def reset(self) -> None:
        with self._lock:
            for table in (
                "user_identities",
                "accounts",
                "user_profiles",
                "profile_specialties",
                "profile_affiliations",
                "account_subscriptions",
                "access_grants",
                "profile_audit",
                "profile_operations",
                "specialty_concepts",
                "hospital_directory",
                "plan_definitions",
            ):
                self._conn.execute(f"DELETE FROM {table}")
            self._conn.commit()
        self.seed()

    def seed(self) -> None:
        with self._lock:
            for specialty_id, system, code, name in SPECIALTIES:
                self._conn.execute(
                    """
                    INSERT OR IGNORE INTO specialty_concepts
                    (specialty_id, code_system, code, display_name, active)
                    VALUES (?, ?, ?, ?, 1)
                    """,
                    (specialty_id, system, code, name),
                )
            for hospital_id, name, city, region, country in HOSPITALS:
                self._conn.execute(
                    """
                    INSERT OR IGNORE INTO hospital_directory
                    (hospital_id, name, city, state_or_region, country_code, status)
                    VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                    """,
                    (hospital_id, name, city, region, country),
                )
            self._conn.execute(
                """
                INSERT OR IGNORE INTO plan_definitions
                (plan_id, code, display_name, active, plan_version, feature_policy)
                VALUES (?, 'PILOT', 'Pilot access', 1, '1', ?)
                """,
                (PLAN_PILOT, json.dumps({"features": ["inference", "profile"], "limits": {"jobsPerHour": 40}})),
            )
            self._conn.commit()
        data = load_provisioning(SETTINGS.provisioning_path)
        for row in data.get("identities", []):
            self.ensure_identity(
                issuer=row["authIssuer"],
                subject=row["authSubject"],
                account_id=row["accountId"],
                user_id=row["userId"],
                display_name=row.get("displayName") or "Clinician",
                email=None,
                email_verified=False,
            )
            self.ensure_pilot_grant(row["accountId"], granted_by="provisioning", reason="synthetic-test")
        production = data.get("productionAnkita") or {}
        env_name = production.get("authSubjectEnv")
        subject = os.environ.get(env_name, "").strip() if env_name else ""
        if subject:
            self.ensure_identity(
                issuer=production["authIssuer"],
                subject=subject,
                account_id=production["accountId"],
                user_id="user-ankita",
                display_name="Dr. Ankita",
                email=None,
                email_verified=True,
            )
            self.ensure_pilot_grant(production["accountId"], granted_by="admin", reason="ankita-pilot")

    def identity_by_iss_sub(self, issuer: str, subject: str) -> dict | None:
        row = self._conn.execute(
            "SELECT * FROM user_identities WHERE auth_issuer = ? AND auth_subject = ?",
            (issuer, subject),
        ).fetchone()
        return dict(row) if row else None

    def ensure_identity(
        self,
        *,
        issuer: str,
        subject: str,
        account_id: str | None,
        user_id: str | None,
        display_name: str,
        email: str | None,
        email_verified: bool,
    ) -> dict:
        existing = self.identity_by_iss_sub(issuer, subject)
        if existing:
            if existing["status"] != "ACTIVE":
                raise HTTPException(
                    status_code=403,
                    detail={"code": "UNAUTHORIZED", "message": "Account is blocked."},
                )
            profile = self.profile_by_user(existing["user_id"])
            return {"identity": existing, "profile": profile, "created": False}
        user_id = user_id or _id("user")
        account_id = account_id or str(uuid.uuid4())
        profile_id = _id("prof")
        now = _now()
        with self._lock:
            self._conn.execute(
                """
                INSERT INTO user_identities
                (user_id, auth_issuer, auth_subject, email, email_verified, status)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                """,
                (user_id, issuer, subject, email, 1 if email_verified else 0),
            )
            self._conn.execute(
                "INSERT INTO accounts(account_id, owner_user_id, status) VALUES (?, ?, 'ACTIVE')",
                (account_id, user_id),
            )
            self._conn.execute(
                """
                INSERT INTO user_profiles(
                    profile_id, user_id, account_id, display_name, preferred_name,
                    profession_code, phone, time_zone_id, preferred_language,
                    onboarding_status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, NULL, NULL, NULL, 'Asia/Kolkata', NULL, 'NOT_STARTED', 1, ?, ?)
                """,
                (profile_id, user_id, account_id, display_name, now, now),
            )
            self._audit(user_id, user_id, "UserProfile", profile_id, None, 1, "bootstrap")
            self._conn.commit()
        identity = self.identity_by_iss_sub(issuer, subject)
        return {"identity": identity, "profile": self.profile_by_user(user_id), "created": True}

    def profile_by_user(self, user_id: str) -> dict | None:
        row = self._conn.execute("SELECT * FROM user_profiles WHERE user_id = ?", (user_id,)).fetchone()
        if row is None:
            return None
        return self._profile_payload(dict(row))

    def profile_by_id(self, profile_id: str) -> dict | None:
        row = self._conn.execute("SELECT * FROM user_profiles WHERE profile_id = ?", (profile_id,)).fetchone()
        if row is None:
            return None
        return self._profile_payload(dict(row))

    def _profile_payload(self, row: dict) -> dict:
        specialties = [
            dict(item)
            for item in self._conn.execute(
                "SELECT * FROM profile_specialties WHERE profile_id = ?",
                (row["profile_id"],),
            ).fetchall()
        ]
        affiliations = [
            dict(item)
            for item in self._conn.execute(
                "SELECT * FROM profile_affiliations WHERE profile_id = ?",
                (row["profile_id"],),
            ).fetchall()
        ]
        return {
            "profileId": row["profile_id"],
            "userId": row["user_id"],
            "accountId": row["account_id"],
            "displayName": row["display_name"],
            "preferredName": row["preferred_name"],
            "professionCode": row["profession_code"],
            "phone": row["phone"],
            "timeZoneId": row["time_zone_id"],
            "preferredLanguage": row["preferred_language"],
            "onboardingStatus": row["onboarding_status"],
            "version": row["version"],
            "createdAt": int(row["created_at"]),
            "updatedAt": int(row["updated_at"]),
            "specialties": [
                {
                    "id": item["id"],
                    "specialtyId": item["specialty_id"],
                    "reportedSpecialtyText": item["reported_specialty_text"],
                    "isPrimary": bool(item["is_primary"]),
                    "verificationStatus": item["verification_status"],
                }
                for item in specialties
            ],
            "affiliations": [
                {
                    "affiliationId": item["affiliation_id"],
                    "hospitalId": item["hospital_id"],
                    "reportedHospitalName": item["reported_hospital_name"],
                    "reportedCity": item["reported_city"],
                    "departmentName": item["department_name"],
                    "jobTitle": item["job_title"],
                    "startsOn": item["starts_on"],
                    "endsOn": item["ends_on"],
                    "isPrimary": bool(item["is_primary"]),
                    "verificationStatus": item["verification_status"],
                }
                for item in affiliations
            ],
        }

    def patch_profile(self, user_id: str, body: dict, actor_id: str) -> dict:
        forbidden = FORBIDDEN_PROFILE_FIELDS.intersection(body.keys())
        if forbidden:
            raise HTTPException(
                status_code=400,
                detail={"code": "FORBIDDEN_FIELD", "message": f"Cannot write {sorted(forbidden)}"},
            )
        operation_id = body.get("operationId")
        if not operation_id:
            raise HTTPException(status_code=400, detail={"code": "VALIDATION", "message": "operationId required"})
        expected = body.get("expectedVersion")
        if expected is None:
            raise HTTPException(status_code=400, detail={"code": "VALIDATION", "message": "expectedVersion required"})
        replay = self._conn.execute(
            "SELECT result_json FROM profile_operations WHERE operation_id = ?",
            (operation_id,),
        ).fetchone()
        if replay:
            return json.loads(replay["result_json"])
        profile = self.profile_by_user(user_id)
        if profile is None:
            raise HTTPException(status_code=404, detail={"code": "NOT_FOUND", "message": "Profile not found"})
        if int(expected) != int(profile["version"]):
            raise HTTPException(
                status_code=409,
                detail={"code": "CONFLICT", "message": "Profile version does not match expectedVersion."},
            )
        specialties = body.get("specialties", profile["specialties"])
        affiliations = body.get("affiliations", profile["affiliations"])
        self._validate_specialties(specialties)
        self._validate_affiliations(affiliations)
        display_name = body.get("displayName", profile["displayName"])
        if not display_name:
            raise HTTPException(status_code=400, detail={"code": "VALIDATION", "message": "displayName required"})
        now = _now()
        new_version = profile["version"] + 1
        onboarding = "COMPLETE" if specialties and affiliations else "INCOMPLETE"
        if not specialties and not affiliations:
            onboarding = profile["onboardingStatus"] if profile["onboardingStatus"] != "NOT_STARTED" else "INCOMPLETE"
        with self._lock:
            self._conn.execute(
                """
                UPDATE user_profiles SET
                    display_name = ?, preferred_name = ?, profession_code = ?, phone = ?,
                    time_zone_id = ?, preferred_language = ?, onboarding_status = ?,
                    version = ?, updated_at = ?
                WHERE profile_id = ?
                """,
                (
                    display_name,
                    body.get("preferredName", profile["preferredName"]),
                    body.get("professionCode", profile["professionCode"]),
                    body.get("phone", profile["phone"]),
                    body.get("timeZoneId", profile["timeZoneId"]),
                    body.get("preferredLanguage", profile["preferredLanguage"]),
                    onboarding,
                    new_version,
                    now,
                    profile["profileId"],
                ),
            )
            self._conn.execute("DELETE FROM profile_specialties WHERE profile_id = ?", (profile["profileId"],))
            self._conn.execute("DELETE FROM profile_affiliations WHERE profile_id = ?", (profile["profileId"],))
            for item in specialties:
                self._conn.execute(
                    """
                    INSERT INTO profile_specialties
                    (id, profile_id, specialty_id, reported_specialty_text, is_primary, verification_status)
                    VALUES (?, ?, ?, ?, ?, 'SELF_REPORTED')
                    """,
                    (
                        item.get("id") or _id("ps"),
                        profile["profileId"],
                        item.get("specialtyId"),
                        item.get("reportedSpecialtyText"),
                        1 if item.get("isPrimary") else 0,
                    ),
                )
            for item in affiliations:
                self._conn.execute(
                    """
                    INSERT INTO profile_affiliations
                    (affiliation_id, profile_id, hospital_id, reported_hospital_name, reported_city,
                     department_name, job_title, starts_on, ends_on, is_primary, verification_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SELF_REPORTED')
                    """,
                    (
                        item.get("affiliationId") or _id("aff"),
                        profile["profileId"],
                        item.get("hospitalId"),
                        item.get("reportedHospitalName"),
                        item.get("reportedCity"),
                        item.get("departmentName"),
                        item.get("jobTitle"),
                        item.get("startsOn"),
                        item.get("endsOn"),
                        1 if item.get("isPrimary") else 0,
                    ),
                )
            self._audit(user_id, actor_id, "UserProfile", profile["profileId"], profile["version"], new_version, "patch")
            updated = self.profile_by_id(profile["profileId"])
            self._conn.execute(
                "INSERT INTO profile_operations(operation_id, profile_id, result_json) VALUES (?, ?, ?)",
                (operation_id, profile["profileId"], json.dumps(updated)),
            )
            self._conn.commit()
        return updated or {}

    def _validate_specialties(self, specialties: list) -> None:
        if not isinstance(specialties, list):
            raise HTTPException(status_code=400, detail={"code": "VALIDATION", "message": "specialties must be a list"})
        primaries = [item for item in specialties if item.get("isPrimary")]
        if specialties and len(primaries) != 1:
            raise HTTPException(status_code=400, detail={"code": "VALIDATION", "message": "Exactly one primary specialty is required"})
        for item in specialties:
            has_id = bool(item.get("specialtyId"))
            has_text = bool(item.get("reportedSpecialtyText"))
            if has_id == has_text:
                raise HTTPException(
                    status_code=400,
                    detail={"code": "VALIDATION", "message": "Each specialty needs a catalogue id or unlisted text, not both."},
                )
            if has_id and not self._conn.execute(
                "SELECT 1 FROM specialty_concepts WHERE specialty_id = ? AND active = 1",
                (item["specialtyId"],),
            ).fetchone():
                raise HTTPException(status_code=400, detail={"code": "VALIDATION", "message": "Unknown specialtyId"})
            if item.get("verificationStatus") == "VERIFIED":
                raise HTTPException(status_code=400, detail={"code": "FORBIDDEN_FIELD", "message": "Users cannot self-award VERIFIED"})

    def _validate_affiliations(self, affiliations: list) -> None:
        if not isinstance(affiliations, list):
            raise HTTPException(status_code=400, detail={"code": "VALIDATION", "message": "affiliations must be a list"})
        current = [item for item in affiliations if not item.get("endsOn")]
        primaries = [item for item in current if item.get("isPrimary")]
        if current and len(primaries) != 1:
            raise HTTPException(
                status_code=400,
                detail={"code": "VALIDATION", "message": "Exactly one primary active hospital affiliation is required"},
            )
        for item in affiliations:
            has_id = bool(item.get("hospitalId"))
            has_name = bool(item.get("reportedHospitalName"))
            if has_id == has_name:
                raise HTTPException(
                    status_code=400,
                    detail={"code": "VALIDATION", "message": "Each affiliation needs a directory hospital or unlisted name, not both."},
                )
            if has_id and not self._conn.execute(
                "SELECT 1 FROM hospital_directory WHERE hospital_id = ? AND status = 'ACTIVE'",
                (item["hospitalId"],),
            ).fetchone():
                raise HTTPException(status_code=400, detail={"code": "VALIDATION", "message": "Unknown hospitalId"})
            start, end = item.get("startsOn"), item.get("endsOn")
            if start and end and end < start:
                raise HTTPException(status_code=400, detail={"code": "VALIDATION", "message": "Affiliation end cannot precede start"})
            if item.get("verificationStatus") == "VERIFIED":
                raise HTTPException(status_code=400, detail={"code": "FORBIDDEN_FIELD", "message": "Users cannot self-award VERIFIED"})

    def ensure_pilot_grant(self, account_id: str, *, granted_by: str, reason: str) -> None:
        existing = self._conn.execute(
            """
            SELECT 1 FROM access_grants
            WHERE account_id = ? AND kind = 'PILOT' AND status = 'ACTIVE'
            """,
            (account_id,),
        ).fetchone()
        if existing:
            return
        with self._lock:
            self._conn.execute(
                """
                INSERT INTO access_grants
                (grant_id, account_id, kind, feature_policy, starts_at, ends_at, status, granted_by, reason)
                VALUES (?, ?, 'PILOT', ?, ?, NULL, 'ACTIVE', ?, ?)
                """,
                (
                    _id("grant"),
                    account_id,
                    json.dumps({"features": ["inference", "profile"], "limits": {"jobsPerHour": 40}}),
                    _now(),
                    granted_by,
                    reason,
                ),
            )
            self._conn.commit()

    def revoke_grants(self, account_id: str) -> None:
        with self._lock:
            self._conn.execute(
                "UPDATE access_grants SET status = 'REVOKED' WHERE account_id = ?",
                (account_id,),
            )
            self._conn.commit()

    def search_specialties(self, query: str = "") -> list[dict]:
        q = f"%{query.strip()}%"
        rows = self._conn.execute(
            """
            SELECT * FROM specialty_concepts
            WHERE active = 1 AND (display_name LIKE ? OR code LIKE ?)
            ORDER BY display_name
            """,
            (q, q),
        ).fetchall()
        return [
            {
                "specialtyId": row["specialty_id"],
                "codeSystem": row["code_system"],
                "code": row["code"],
                "displayName": row["display_name"],
                "active": True,
            }
            for row in rows
        ]

    def search_hospitals(self, query: str = "") -> list[dict]:
        q = f"%{query.strip()}%"
        rows = self._conn.execute(
            """
            SELECT * FROM hospital_directory
            WHERE status = 'ACTIVE' AND (name LIKE ? OR city LIKE ?)
            ORDER BY name
            """,
            (q, q),
        ).fetchall()
        return [
            {
                "hospitalId": row["hospital_id"],
                "name": row["name"],
                "city": row["city"],
                "stateOrRegion": row["state_or_region"],
                "countryCode": row["country_code"],
                "status": row["status"],
            }
            for row in rows
        ]

    def access_for_account(self, account_id: str) -> dict:
        account = self._conn.execute("SELECT * FROM accounts WHERE account_id = ?", (account_id,)).fetchone()
        disabled = account is not None and account["status"] != "ACTIVE"
        grants = [
            dict(row)
            for row in self._conn.execute(
                "SELECT * FROM access_grants WHERE account_id = ? ORDER BY starts_at DESC",
                (account_id,),
            ).fetchall()
        ]
        subs = [
            dict(row)
            for row in self._conn.execute(
                "SELECT * FROM account_subscriptions WHERE account_id = ? ORDER BY created_at DESC",
                (account_id,),
            ).fetchall()
        ]
        now = _now()
        features: list[str] = []
        limits: dict[str, Any] = {}
        label = "No subscription"
        access_state = "DISABLED"
        grant_summaries = []
        for grant in grants:
            active = grant["status"] == "ACTIVE" and grant["starts_at"] <= now and (
                grant["ends_at"] is None or grant["ends_at"] >= now
            )
            grant_summaries.append(
                {
                    "kind": grant["kind"],
                    "status": grant["status"] if active or grant["status"] != "ACTIVE" else "EXPIRED",
                    "startsAt": int(grant["starts_at"]),
                    "endsAt": int(grant["ends_at"]) if grant["ends_at"] else None,
                    "label": "Pilot access" if grant["kind"] == "PILOT" else grant["kind"].title(),
                }
            )
            if active and not disabled:
                policy = json.loads(grant["feature_policy"])
                features = sorted(set(features + policy.get("features", [])))
                limits = policy.get("limits") or limits
                access_state = "AUTHORIZED"
                if grant["kind"] == "PILOT":
                    label = "Pilot access"
        sub_summary = None
        if subs:
            sub = subs[0]
            plan = self._conn.execute(
                "SELECT * FROM plan_definitions WHERE plan_id = ?",
                (sub["plan_id"],),
            ).fetchone()
            in_window = True
            if sub["period_end"] and sub["period_end"] < now:
                in_window = False
            paid = sub["status"] in {"ACTIVE", "TRIALING"} and in_window
            sub_summary = {
                "status": sub["status"],
                "planLabel": plan["display_name"] if plan else sub["plan_id"],
                "periodEnd": int(sub["period_end"]) if sub["period_end"] else None,
            }
            if paid and not disabled:
                access_state = "AUTHORIZED"
                label = sub_summary["planLabel"]
                if plan:
                    policy = json.loads(plan["feature_policy"])
                    features = sorted(set(features + policy.get("features", [])))
                    limits = policy.get("limits") or limits
        if disabled:
            access_state = "DISABLED"
            features = []
        if access_state != "AUTHORIZED" and (grants or subs):
            access_state = "PENDING" if not disabled else "DISABLED"
        return {
            "subscription": sub_summary,
            "grants": grant_summaries,
            "entitlement": {
                "accountId": account_id,
                "policyVersion": "entitlement-v1",
                "features": features,
                "limits": limits,
                "effectiveAt": int(now),
                "refreshAfter": int(now + 300),
                "accessState": access_state,
                "label": label,
            },
        }

    def has_inference(self, account_id: str) -> bool:
        access = self.access_for_account(account_id)
        return access["entitlement"]["accessState"] == "AUTHORIZED" and "inference" in access["entitlement"]["features"]

    def _audit(self, user_id: str, actor_id: str, object_type: str, object_id: str, previous: int | None, new: int, action: str) -> None:
        self._conn.execute(
            """
            INSERT INTO profile_audit
            (id, user_id, actor_id, object_type, object_id, previous_version, new_version, action, recorded_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (_id("aud"), user_id, actor_id, object_type, object_id, previous, new, action, _now()),
        )


PROFILES = ProfileStore(SETTINGS.db_path)
