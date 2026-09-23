# Hybrid delivery notes (HY-02–HY-14)

Date: 2026-09-20  
Verification: `python -m pytest` in `backend/` (30 passed). `.\gradlew.bat testDebugUnitTest` (passed). Schema export `app/schemas/.../13.json`. No provider secrets were read or committed.

Clinical records remain on-device. There is still **no** cloud commit endpoint.

| ID | Status | Evidence |
| --- | --- | --- |
| HY-02 | Done | `contracts/v1/`, fixtures, `backend/tests/test_contracts.py`, `ProposalContractTest` |
| HY-03 | Done | `backend/medtrack_gateway/auth.py`, `data/provisioning.json`, `test_auth_isolation.py` |
| HY-04 | Done | Sign-in gate, `AccountSession`, device bind; debug synthetic Ankita. Live Google needs web client ID + SHA-1. |
| HY-05 | Done | Jobs/artifacts in gateway store, idempotency, cancel, cleanup tests |
| HY-06 | Done | `hybrid_inference_requests` Room table, `ContextSelector`, queued/offline states |
| HY-07 | Done | Durable `hybrid_proposals`, version checks, atomic groups via `ProposalCommitService` |
| HY-08 | Done | Mock Jev + OpenRouter adapters; [JV-01 contract](JV-01-openrouter-jev-contract.md). Live Jev not executed in this workspace. |
| HY-09 | Done | `InferenceGateway` replaces device OpenRouter. `OPENROUTER_API_KEY` removed from BuildConfig. Local MCP remains opt-in debug. |
| HY-10 | Done | Scheduling outbox → AlarmManager; notification Complete vs Add note; boot/startup reconcile |
| HY-11 | Done | `EncryptedBackupService`, [recovery notes](../docs/hybrid-recovery.md) |
| HY-12 | Done | Per-account quota, route disable, cache isolation tests |
| HY-13 | Done* | Synthetic E2E in unit/backend tests. Mid-tier device latency not measured this pass. |
| HY-14 | Blocked | Synthetic rehearsal only. Live Ankita onboarding needs authorized deployment. |

\*HY-13 is a code release candidate, not a claim that Ankita has used real patients.

## Remaining limitations

- Live OpenRouter Jev/chat requires a **server** `OPENROUTER_API_KEY`; mock gateway is the default debug path.
- Google Sign-In Credential Manager is not wired; debug uses the synthetic Ankita bind. Production needs `medtrack.google.webClientId` and a provisioned Firebase subject.
- Exact-alarm delivery is best-effort (`INEXACT`/`UNAVAILABLE` when permission is missing).
- Shared charts, multi-device sync, WhatsApp ingestion remain deferred.
