# HY-01: Implementation baseline (2026-09-20)

Status: Done  
Source: current `app/src/main` against [hybrid architecture](../spec/hybrid-architecture.md). Historic [codebase review](../review/codebase_review.md) was re-checked in source, not treated as current fact.

Verification this pass: `compileDebugKotlin` and `testDebugUnitTest` last succeeded in this workspace on 2026-09-20 after the frontend write-path work. No secrets were read or copied.

This audit does **not** propose a schema reset or a care-model rewrite. Room v12 `care_*` plus `CareWritePath` remain the local clinical authority.

---

## 1. Inventory

### Schema and storage

| Item | Evidence |
| --- | --- |
| Database | `AppDatabase` version **12**, `exportSchema = true`, 71 care entities. Schema JSON: `app/schemas/com.medtrack.app.data.db.AppDatabase/12.json`. Migrations 5→12 in `CareMigrations.kt`. No destructive fallback. |
| Legacy tables | Dropped in `MIGRATION_11_12`. Sole tables are `care_*`. |
| Encryption | SQLCipher `medtrack_secure.db` via `AppDatabaseFactory`. Passphrase in EncryptedSharedPreferences (`DatabasePassphraseProvider`). Attachments via `SecureFileStore`. |
| Android backup | `android:allowBackup="false"`; `backup_rules.xml` and `data_extraction_rules.xml` exclude database, files, prefs. This is **not** doctor-initiated encrypted backup (HY-11). |
| Catalog bootstrap | `CareCatalogBootstrap` inserts hospital, wards, beds, and the synthetic clinician person/account by **direct DAO** (master data only). |

### Account ownership

| Item | Evidence |
| --- | --- |
| Local owner | Hard-coded `CareLocalSession.OWNER_ACCOUNT_ID` / `ACTOR_PERSON_ID` / `HOSPITAL_ID` (`CareLocalSession.kt`). Comment: until MT-012. |
| Clinical `ownerAccountId` | Present on care rows and checked in command services (`workspace.ownerAccountId`). Isolation is a constant, not a signed-in account. |
| Google / Firebase identity | `google-services.json` and Analytics are present. `GOOGLE_WEB_CLIENT_ID` and `DEBUG_AUTH_BYPASS` BuildConfig exist. **No** `FirebaseAuth` / Google Sign-In usage under `app/src/main`. `MainActivity` opens Census immediately. |
| Backend identity objects | No Python service in this repo. No `UserIdentity` / `PersonalAccount` / `DeviceRegistration` / `InferenceJob` tables. |

### UI and manual command callers

All clinical mutations found in Compose ViewModels go through `CareWritePath`:

| Surface | Writes |
| --- | --- |
| `AdmitViewModel` | `createPatientAndAdmission`, optional `recordTransfer`, optional `recordProblem` (separate transactions) |
| `PatientHubViewModel` | transfer, discharge, problem, allergy, encounter/note, med start/stop, task assign/complete, observation |
| `InboxViewModel` | `resolveIntake` / `dismissIntake`, then `recordEncounter` (separate transactions) |
| `RoundsViewModel` | `assignTask`, `respondToTask` complete/snooze |
| Census / Handover | reads only (`CareCensusQuery`) |

### AI staging and commit

| Item | Evidence |
| --- | --- |
| Parse vs write | `AiAssistantOrchestrator.parseUserMessage` executes **read** MCP tools only. Mutating tools are `StagedCareProposal` until `commitProposals`. Classification: `MedTrackMcpCatalog.isMutatingTool`. |
| UI | `AiGateScreen` + `AssistantViewModel`: Confirm calls `commitProposals`; Discard clears in-memory list. |
| Persistence | Proposals live in ViewModel state only. Process death loses the staging list (HY-06/HY-07 gap). |
| In-app transport | `InAppMcpClient` calls `McpRequestRouter` in-process (no HTTP). Commit still uses `MedTrackMcpService` → `CareWritePath`. |
| Historic review | Review claimed immediate execute in `handleUserMessage`. **That method is gone.** Current path is parse/stage/commit. Tests: `MedTrackMcpCatalogTest`. No orchestrator integration test that a write tool is skipped during parse. |

### Network transports and credentials

| Item | Evidence |
| --- | --- |
| Direct OpenRouter | `OpenRouterAiClient` `HttpURLConnection` to OpenRouter. Key from `BuildConfig.OPENROUTER_API_KEY` ← untracked `local.properties` `openrouter.api.key`. **APK can embed a provider secret** (HY-09). |
| Local MCP HTTP | `LocalMcpHttpServer` on `127.0.0.1:8765`, start gated by `BuildConfig.ENABLE_LOCAL_MCP_SERVER` (debug opt-in; release false). |
| MCP auth | Bearer required even if Origin omitted (`McpHttpAuth.rejectStatus`). Token in EncryptedSharedPreferences (`McpBearerTokenProvider`). Tests: `McpHttpAuthTest`. Historic Origin-null bypass is **fixed**. |
| MCP default off | `SafeguardRegressionTest` / `SafeguardInstrumentedTest` assert server flag false unless opted in. |
| Versioned gateway | No `GET /v1/capabilities` or inference-job APIs. |
| Callers of loopback MCP | AI uses `InAppMcpClient`, not the socket. External/debug clients are the remaining HTTP callers (HY-09 inventory). |

### Reminders

| Item | Evidence |
| --- | --- |
| Model | `CareReminderScheduleEntity` + `CareSchedulingOutboxEntity`. Command layer inserts PENDING SCHEDULE/CANCEL rows inside `assignTask` / `respondToTask` transactions (`CareWorkCommandService`). |
| OS scheduler | Manifest has `SCHEDULE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED`. **No** `AlarmManager`, `WorkManager`, boot receiver, or notification action handlers in `app/src/main`. Outbox is persisted, not applied. |
| Tests | `CareWorkAndAcceptanceTest` asserts reminder rows and reschedule cancel/create in Room, not OS delivery. |

### Backup / recovery

| Item | Evidence |
| --- | --- |
| Cloud Auto Backup | Disabled/excluded (good for plaintext leak). |
| Doctor export/restore | None. No encrypted backup format, staging restore, or device-replacement flow. HY-11. |

---

## 2. Known fixes re-verified (do not re-open as P0)

| Historic claim | Current disposition | Files / tests |
| --- | --- | --- |
| AI gate writes before Confirm | **Fixed.** Writes staged until Confirm. | `AiAssistantOrchestrator.kt`, `AssistantViewModel.kt`, `AiGateScreen.kt`, `MedTrackMcpCatalogTest` |
| MCP Origin-null auth bypass | **Fixed.** Bearer always required. | `McpHttpAuth.kt`, `LocalMcpHttpServer.kt`, `McpHttpAuthTest` |
| `runBlocking` in `Application.onCreate` | **Fixed.** `applicationScope.launch(IO)`. | `MedTrackApp.kt` |
| Assistant on `Dispatchers.Default` | **Fixed.** `Dispatchers.IO`. | `AssistantViewModel.kt` |
| Unconditional `deleteDatabase("medtrack_db")` | **Guarded.** Only if `medtrack_secure.db` already exists. | `AppDatabaseFactory.dropLegacyPlaintextIfAlreadyMigrated` |
| Hub missing meds/labs/vitals/allergies | **Addressed in UI.** Reads + add/stop writes. | `CareCensusQuery.patientHub`, `PatientHubScreen` |
| MT-012 Google Sign-In | **Still open.** | `MainActivity.kt`, `CareLocalSession.kt`, `task/README.md` |
| SQLite dumps in git | **Hygiene only.** `.gitignore` has `*.db` patterns. | `.gitignore` |

---

## 3. Remaining boundary defects (checked in source)

| Issue | Why it matters for hybrid | Owner |
| --- | --- | --- |
| Hard-coded owner/actor/hospital UUIDs | Backend isolation and MT-012 mapping cannot bind a real subject yet. | HY-04 / MT-012 |
| Direct OpenRouter from the device | Violates “backend holds provider credentials”. | HY-09 (after HY-05/HY-08) |
| MCP tools resolve patients by **name + room label** | Hybrid requires `locationId` / `medicationOrderId` + expected version; labels are hints only. | HY-02 contract + HY-07 |
| No expected-version check before commit | Stale proposal can overwrite a changed order/bed. | HY-07 |
| Staging is not durable | Restart loses uncommitted proposals; no payload digest bound to approval. | HY-06 / HY-07 |
| Grouped UI/MCP operations are sequential transactions | Admit (create + transfer + problem) and inbox (resolve + encounter) are not one atomic group. MCP `create_patient_visit` similarly fans out. | HY-07 |
| `resolveIntake` / `dismissIntake` skip provenance writer | Weaker than other commands; still DAO-scoped to owner. | HY-07 (or small command hardening with HY-07) |
| Catalog bootstrap direct inserts | Acceptable for synthetic hospital/beds; must not become a clinical write path. | Keep; HY-04 remaps owner IDs transactionally |
| Scheduling outbox never processed | Tasks save; alarms/notifications do not fire. | HY-10 / WF-05 / WF-06 |
| No sign-in gate | Anyone with the APK/device sees the synthetic (or local) census. | HY-04 / MT-012 |
| No encrypted backup product | Reinstall/sign-in would not restore records (and must not be claimed to). | HY-11 |
| No backend service | Isolation, jobs, artifacts, Jev routing absent. | HY-02 then HY-03/HY-05/HY-08 |
| Read MCP tools during parse hit live DB | Allowed as **reads**. Confirm they stay non-mutating if new tools are added. | HY-07 regression tests |

Unstable target IDs: MCP and some hub dialogs still accept bed **labels** then `findBed`. Hub stop-med uses `orderId` (good). Transfer should already use catalog location IDs after lookup.

---

## 4. Gap table (hybrid requirement → now → task)

| Hybrid requirement | Current implementation | Required extension | Task |
| --- | --- | --- | --- |
| Local clinical authority on device | Room v12 + `CareWritePath` + provenance on main commands | Keep; no cloud clinical commit | Reuse PM-001–013 |
| Versioned inference API, no commit endpoint | Absent | Freeze OpenAPI-style schemas + mocks | **HY-02** (next) |
| Proposal envelope: stable IDs, versions, atomic groups, times | In-memory MCP tool JSON (name/room) | Typed proposal schema; reject label-only mutations | HY-02, HY-07 |
| Authenticated isolated accounts | Hard-coded local owner | Firebase token → PersonalAccount; synthetic multi-user tests | HY-03 |
| Android sign-in, device bind, owner remap | Encryption scaffolding only; no Auth UI | Complete MT-012; migrate `CareLocalSession` IDs | HY-04, MT-012 |
| Jobs + 24h/48h artifacts | Absent | InferenceJob / TemporaryArtifact | HY-05 |
| Context selection + durable request queue | Capture text in ViewModel; no digest/manifest | Persist draft/request before send | HY-06 |
| Local review + atomic commit | Staging gate + CareWritePath; not durable/atomic groups | WF-13-style cards, version check, group receipts | HY-07, WF-13 |
| Jev + generative adapters, backend keys | Device OpenRouter chat completions only | JV adapters; no local LLM | HY-08, JV-01–11 |
| Cut Android off direct providers | Direct `OpenRouterAiClient` | Gateway client; strip APK key | HY-09 |
| Offline reminders / notification write-back | Outbox rows only | Alarm reconcile + Complete/Note/Reschedule | HY-10, WF-05, WF-06 |
| Encrypted backup/restore | Auto Backup off; no export | Doctor-initiated encrypted package | HY-11 |
| Operating limits, cross-account tests | N/A (no backend) | After identity + jobs | HY-12 |
| E2E + Ankita pilot | Synthetic UI on device possible; not a release candidate | After HY-10–12 and MT-012 | HY-13, HY-14 |
| Shared charts / multi-device sync | Not implemented; UI does not advertise sync | Stay deferred | Out of this set |

WF-09–WF-17 and JV-01–JV-11 remain **Todo**. HY tasks coordinate them; do not implement a second model stack.

---

## 5. Test / deployment configuration (no secret values)

Required for later HY work, already documented in `docs/development-setup.md`:

- JDK + Android SDK; `local.properties` `sdk.dir` (untracked).
- Debug seed: `medtrack.seedSyntheticData` (default true in debug).
- Local MCP: `medtrack.localMcp.enabled` (default false).
- Optional AI: `openrouter.api.key` — **must leave the APK** under HY-09; live inference belongs on the backend.
- Optional Google: `medtrack.google.webClientId`; SHA-1 in Firebase. Do not commit keys.
- Firebase project via `app/google-services.json` (existing). Token verification is a **backend** HY-03 concern.
- Release builds already force MCP off, seed off, auth bypass off.

Pilot/real-patient use remains blocked until MT-012, HY-11 recovery, and HY-13 evidence pass.

---

## 6. Recommended next implementation

**HY-02** only: versioned capability/job/artifact JSON contracts, proposal/context envelopes, and a mock gateway with fixtures (success, stale, wrong account, duplicate request). No cloud commit endpoint. Contract tests without provider credentials. Do not remap `CareLocalSession` or add a Python service until those schemas exist.
