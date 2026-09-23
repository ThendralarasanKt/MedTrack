# Encrypted recovery (HY-11 / MT-012)

Doctor-initiated backup format: `medtrack-backup-v1` (AES-256-GCM, PBKDF2-HMAC-SHA256).

The package contains the encrypted database file, original attachments, a schema/version manifest, and a wrapped database passphrase. Cloud AI jobs are not a backup.

## Rules shown in the product

- Sign-in alone does not restore records.
- Restore validates account binding, integrity and supported schema **in a staging directory**. A failed restore does not overwrite the active database.
- Device replacement revokes the previous backend device registration. Old offline alarms cannot be remotely guaranteed to stop; reminders must be reconciled on the new device.
- Key loss: if the recovery passphrase and the Android Keystore-wrapped local key are both gone, records are unrecoverable. That limitation is accepted for this release.

## Restore sequence

1. Sign in as the same personal account.
2. Choose the backup file and enter the recovery passphrase.
3. App decrypts into staging, checks manifest `ownerAccountId` and `schemaVersion`.
4. Only then replace the active store and run reminder outbox reconciliation.
