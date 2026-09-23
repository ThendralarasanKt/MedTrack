from __future__ import annotations

import sqlite3
import threading
from pathlib import Path


class IdentityStore:
    """Durable membership, device, and account authorization state."""

    def __init__(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        self._path = path
        self._lock = threading.RLock()
        self._conn = sqlite3.connect(str(path), check_same_thread=False)
        self._conn.execute("PRAGMA journal_mode=WAL")
        self._init()

    def _init(self) -> None:
        with self._lock:
            self._conn.executescript(
                """
                CREATE TABLE IF NOT EXISTS disabled_memberships (
                    account_id TEXT NOT NULL,
                    user_id TEXT NOT NULL,
                    PRIMARY KEY (account_id, user_id)
                );
                CREATE TABLE IF NOT EXISTS disabled_accounts (
                    account_id TEXT PRIMARY KEY
                );
                CREATE TABLE IF NOT EXISTS disabled_users (
                    user_id TEXT PRIMARY KEY
                );
                CREATE TABLE IF NOT EXISTS revoked_devices (
                    account_id TEXT NOT NULL,
                    device_id TEXT NOT NULL,
                    PRIMARY KEY (account_id, device_id)
                );
                CREATE TABLE IF NOT EXISTS registered_devices (
                    account_id TEXT NOT NULL,
                    user_id TEXT NOT NULL,
                    device_id TEXT NOT NULL,
                    PRIMARY KEY (account_id, user_id)
                );
                """
            )
            self._conn.commit()

    def reset(self) -> None:
        with self._lock:
            for table in (
                "disabled_memberships",
                "disabled_accounts",
                "disabled_users",
                "revoked_devices",
                "registered_devices",
            ):
                self._conn.execute(f"DELETE FROM {table}")
            self._conn.commit()

    def disable_membership(self, account_id: str, user_id: str) -> None:
        with self._lock:
            self._conn.execute(
                "INSERT OR IGNORE INTO disabled_memberships(account_id, user_id) VALUES (?, ?)",
                (account_id, user_id),
            )
            self._conn.commit()

    def membership_disabled(self, account_id: str, user_id: str) -> bool:
        row = self._conn.execute(
            "SELECT 1 FROM disabled_memberships WHERE account_id = ? AND user_id = ?",
            (account_id, user_id),
        ).fetchone()
        return row is not None

    def disable_account(self, account_id: str) -> None:
        with self._lock:
            self._conn.execute(
                "INSERT OR IGNORE INTO disabled_accounts(account_id) VALUES (?)",
                (account_id,),
            )
            self._conn.commit()

    def account_disabled(self, account_id: str) -> bool:
        row = self._conn.execute(
            "SELECT 1 FROM disabled_accounts WHERE account_id = ?",
            (account_id,),
        ).fetchone()
        return row is not None

    def disable_user(self, user_id: str) -> None:
        with self._lock:
            self._conn.execute(
                "INSERT OR IGNORE INTO disabled_users(user_id) VALUES (?)",
                (user_id,),
            )
            self._conn.commit()

    def user_disabled(self, user_id: str) -> bool:
        row = self._conn.execute(
            "SELECT 1 FROM disabled_users WHERE user_id = ?",
            (user_id,),
        ).fetchone()
        return row is not None

    def revoke_device(self, account_id: str, device_id: str) -> None:
        with self._lock:
            self._conn.execute(
                "INSERT OR IGNORE INTO revoked_devices(account_id, device_id) VALUES (?, ?)",
                (account_id, device_id),
            )
            self._conn.execute(
                "DELETE FROM registered_devices WHERE account_id = ? AND device_id = ?",
                (account_id, device_id),
            )
            self._conn.commit()

    def device_revoked(self, account_id: str, device_id: str) -> bool:
        row = self._conn.execute(
            "SELECT 1 FROM revoked_devices WHERE account_id = ? AND device_id = ?",
            (account_id, device_id),
        ).fetchone()
        return row is not None

    def active_device(self, account_id: str, user_id: str) -> str | None:
        row = self._conn.execute(
            "SELECT device_id FROM registered_devices WHERE account_id = ? AND user_id = ?",
            (account_id, user_id),
        ).fetchone()
        return None if row is None else str(row[0])

    def register_device(self, account_id: str, user_id: str, device_id: str) -> None:
        with self._lock:
            self._conn.execute(
                """
                INSERT INTO registered_devices(account_id, user_id, device_id)
                VALUES (?, ?, ?)
                ON CONFLICT(account_id, user_id) DO UPDATE SET device_id = excluded.device_id
                """,
                (account_id, user_id, device_id),
            )
            self._conn.execute(
                "DELETE FROM revoked_devices WHERE account_id = ? AND device_id = ?",
                (account_id, device_id),
            )
            self._conn.commit()
