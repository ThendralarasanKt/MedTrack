from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
CONTRACTS_DIR = REPO_ROOT / "contracts" / "v1"


@dataclass
class Settings:
    env: str = os.environ.get("MEDTRACK_ENV", "test")
    firebase_project_id: str = os.environ.get("MEDTRACK_FIREBASE_PROJECT_ID", "medtrack-6497f")
    allow_dev_tokens: bool = os.environ.get("MEDTRACK_ALLOW_DEV_TOKENS", "true").lower() == "true"
    openrouter_api_key: str = os.environ.get("OPENROUTER_API_KEY", "")
    openrouter_referer: str = os.environ.get("MEDTRACK_OPENROUTER_REFERER", "https://medtrack.local")
    jev_model: str = os.environ.get("MEDTRACK_JEV_MODEL", "typesafe/jev-1.13")
    generation_model: str = os.environ.get(
        "MEDTRACK_GENERATION_MODEL",
        "nvidia/nemotron-3-super-120b-a12b:free",
    )
    artifact_dir: Path = Path(os.environ.get("MEDTRACK_ARTIFACT_DIR", str(REPO_ROOT / "backend" / ".data" / "artifacts")))
    db_path: Path = Path(os.environ.get("MEDTRACK_DB_PATH", str(REPO_ROOT / "backend" / ".data" / "gateway.sqlite")))
    max_artifact_bytes: int = int(os.environ.get("MEDTRACK_MAX_ARTIFACT_BYTES", str(5 * 1024 * 1024)))
    max_text_chars: int = int(os.environ.get("MEDTRACK_MAX_TEXT_CHARS", "20000"))
    max_jobs_per_account_hour: int = int(os.environ.get("MEDTRACK_MAX_JOBS_PER_HOUR", "40"))
    job_max_age_hours: int = 48
    artifact_ttl_hours_after_terminal: int = 24
    provisioning_path: Path = Path(
        os.environ.get(
            "MEDTRACK_PROVISIONING",
            str(Path(__file__).resolve().parents[1] / "data" / "provisioning.json"),
        )
    )
    enabled_routes: tuple[str, ...] = ("capture", "reasoning", "document_extract")
    log_clinical_payloads: bool = False


def load_provisioning(path: Path) -> dict:
    if not path.exists():
        return {"identities": []}
    return json.loads(path.read_text(encoding="utf-8"))


SETTINGS = Settings()


def validate_runtime_settings(settings: Settings | None = None) -> None:
    cfg = settings or SETTINGS
    if cfg.env.lower() == "production" and cfg.allow_dev_tokens:
        raise RuntimeError("Development authentication cannot be enabled in production.")
