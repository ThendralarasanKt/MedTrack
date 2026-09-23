from __future__ import annotations

import json
import logging
import time
import urllib.request
from typing import Any

import jwt
from jwt.algorithms import RSAAlgorithm

from .config import SETTINGS

logger = logging.getLogger("medtrack.gateway.firebase")

GOOGLE_JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"


class FirebaseTokenError(Exception):
    pass


class FirebaseTokenVerifier:
    def __init__(
        self,
        project_id: str,
        *,
        static_keys: dict[str, Any] | None = None,
        jwks_url: str = GOOGLE_JWKS_URL,
        fetch_jwks: bool = True,
    ) -> None:
        self.project_id = project_id
        self._static_keys = static_keys or {}
        self._jwks_url = jwks_url
        self._fetch_jwks = fetch_jwks
        self._cached_keys: dict[str, Any] = {}
        self._cached_at = 0.0

    def verify(self, token: str) -> dict:
        try:
            header = jwt.get_unverified_header(token)
        except Exception as exc:  # noqa: BLE001
            raise FirebaseTokenError("Malformed token header") from exc
        alg = header.get("alg")
        if alg != "RS256":
            raise FirebaseTokenError("Unsupported token algorithm")
        kid = header.get("kid")
        if not kid:
            raise FirebaseTokenError("Token missing key id")
        key = self._key_for(str(kid))
        issuer = f"https://securetoken.google.com/{self.project_id}"
        try:
            return jwt.decode(
                token,
                key=key,
                algorithms=["RS256"],
                audience=self.project_id,
                issuer=issuer,
                options={
                    "require": ["exp", "iat", "sub", "aud", "iss"],
                    "verify_signature": True,
                    "verify_exp": True,
                    "verify_aud": True,
                    "verify_iss": True,
                },
                leeway=5,
            )
        except FirebaseTokenError:
            raise
        except Exception as exc:  # noqa: BLE001
            raise FirebaseTokenError("Invalid Firebase identity token") from exc

    def _key_for(self, kid: str) -> Any:
        if kid in self._static_keys:
            return self._static_keys[kid]
        keys = self._jwks_keys()
        if kid not in keys:
            keys = self._jwks_keys(force=True)
        if kid not in keys:
            raise FirebaseTokenError("Unknown signing key")
        return keys[kid]

    def _jwks_keys(self, force: bool = False) -> dict[str, Any]:
        if not self._fetch_jwks:
            return {}
        now = time.time()
        if not force and self._cached_keys and now - self._cached_at < 3600:
            return self._cached_keys
        try:
            with urllib.request.urlopen(self._jwks_keys_url(), timeout=10) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except Exception as exc:  # noqa: BLE001
            logger.warning("firebase-jwks-fetch-failed err=%s", exc)
            if self._cached_keys:
                return self._cached_keys
            raise FirebaseTokenError("Unable to load Firebase signing keys") from exc
        keys: dict[str, Any] = {}
        for entry in payload.get("keys", []):
            key_id = entry.get("kid")
            if not key_id:
                continue
            keys[str(key_id)] = RSAAlgorithm.from_jwk(json.dumps(entry))
        self._cached_keys = keys
        self._cached_at = now
        return keys

    def _jwks_keys_url(self) -> str:
        return self._jwks_url


_VERIFIER: FirebaseTokenVerifier | None = None


def firebase_verifier() -> FirebaseTokenVerifier:
    global _VERIFIER
    if _VERIFIER is None:
        _VERIFIER = FirebaseTokenVerifier(
            SETTINGS.firebase_project_id,
            fetch_jwks=SETTINGS.env.lower() != "test",
        )
    return _VERIFIER


def set_firebase_verifier(verifier: FirebaseTokenVerifier | None) -> None:
    global _VERIFIER
    _VERIFIER = verifier


def reset_firebase_verifier() -> None:
    set_firebase_verifier(None)
