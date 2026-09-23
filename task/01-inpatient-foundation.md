# Task set 01: Dependable inpatient organizer

Status: MT-001 Done; next MT-002  
Source: [High-level specification](../spec/high-level-spec.md)  
Objective: A doctor can manage approximately 20 inpatients through manual Android workflows, retain accurate history, and reliably track outstanding care.

## Scope and working assumptions

- One doctor using one Android device; no shared accounts or synchronization in this set.
- Preserve existing patients, visits, medicines, tasks, attachments, and follow-ups through upgrades.
- Keep visits as clinical reviews within admissions rather than replacing or discarding them.
- Use stable patient and admission IDs for all operations; location and names are search attributes.
- Allow one active admission per patient in this initial version. Preserve prior admissions for readmission history.
- Enforce the one-active-admission rule in the database and shared transactional
  write operation, including concurrent or repeated requests.
- Build manual forms and structured operations first. Relative reminders initially use a duration picker; natural-language routing follows later.
- OCR, clinical report interpretation, model selection by task, voice transcription, and automated conversation extraction are deferred. Existing AI operations must still be made compatible with the new data model.

## MT-001: Establish a reproducible build and synthetic test baseline

Priority: P0 | Depends on: None | Status: Done

### Work

- Remove startup purging and destructive database fallback before launching the
  existing app against any retained database.
- Disable the unauthenticated local MCP HTTP server by default; allow an explicit
  developer-only opt-in for synthetic-data testing.
- Verify the Gradle wrapper, required JDK/Android SDK, debug build, and existing test configuration.
- Document exact setup and build commands without exposing API keys.
- Create a repeatable synthetic dataset with about 20 patients, duplicate names, multiple rooms, active and discharged records, medications, attachments, tasks, and follow-ups.
- Smoke-test the current manual workflow on an emulator or device and record existing failures separately from new changes.

### Acceptance

- App startup never purges discharged records and an unsupported schema fails
  without rebuilding the database.
- The local MCP HTTP server does not start unless a developer explicitly enables
  it for synthetic-data testing.
- A documented clean setup produces an installable debug APK, or concrete build blockers are fixed before completing this task.
- Manual patient, visit, report, task, and reminder screens have recorded smoke-test results.
- Manual workflows launch without a configured model API key.
- Fixtures contain no real patient records and can be recreated without depending on the database snapshots in the repository.

### Starting points

`app/build.gradle.kts`, `MainActivity.kt`, `ui/navigation/AppNavGraph.kt`, and existing test directories. Kotlin paths below are relative to `app/src/main/java/com/medtrack/app/` unless otherwise stated.

### Delivery notes

Status: Done

Implementation:

- Removed startup record purging and destructive Room fallback.
- Made the local MCP HTTP server an explicit debug-only opt-in that is disabled
  by default and always disabled in release builds.
- Added `StartupRecordPolicy` as the Application startup retention entry point.
- Moved synthetic fixtures into debug, with fixture unit tests in `testDebug`.
- `SyntheticDataSeeder` writes valid PDFs under `reports/visit_<id>/` inside a
  Room transaction; debug builds seed by default (`medtrack.seedSyntheticData`,
  default `true`); release always disables seed.
- Encrypted Room database (`medtrack_secure.db` via SQLCipher) and
  `SecureFileStore` attachment encryption scaffolding are in place ahead of
  MT-012 Google Sign-In gating.
- Added unit and instrumented safeguard regression tests covering purge removal,
  unsupported-schema retention (forced open), startup retention policy, default-off
  MCP, and seeder behavior. Debug-only seed BuildConfig assertion lives in
  `testDebug`.
- Firebase Analytics + Google Services plugin with BoM 34.19.0 and
  `app/google-services.json` (OAuth client for Sign-In still deferred).
- Notification post path guards `POST_NOTIFICATIONS` before `notify`.
- Added `docs/development-setup.md` and `docs/mt-001-smoke-test.md`.

Verification (2026-09-19, JDK 27):

- `lintDebug`: BUILD SUCCESSFUL; 46 warnings, 0 Error/Fatal; no
  `MissingPermission`.
- `testDebugUnitTest` / `testReleaseUnitTest`: BUILD SUCCESSFUL.
- `assembleDebug` + `adb install` of debug APK: SUCCESSFUL on Xiaomi
  `24069PC21I` (API 36).
- `connectedDebugAndroidTest`: BUILD SUCCESSFUL — 5/5 tests on device after
  updating `ExampleInstrumentedTest` to expect `com.medtrack.app`.
- Manual smoke on seeded debug install: search, visit create, task update,
  medicine/PDF, follow-up edit/done, restart persistence, discharge +
  discharged list, AI without OpenRouter key, MCP port closed. Details in
  `docs/mt-001-smoke-test.md`. Unsupported-schema retention covered by
  instrumented tests.

Limitations or blockers:

- Add Patient FAB taps were flaky under MIUI gesture navigation during the
  final uiautomator pass; Add Patient UI was reached earlier in the same
  session. Seeded synthetic create/search remains the baseline path.
- Lint dependency/API currency warnings only (non-blocking).

## MT-002: Prevent automatic record loss and establish migration support

Priority: P0 | Depends on: MT-001 | Status: Refined by [PM-001](04-patient-information-model.md#pm-001-shared-contracts-schema-export-migration-baseline)

### Already done (do not redo)

- Startup purge of discharged patients older than 30 days removed.
- `fallbackToDestructiveMigration()` removed from app and notification database builders.
- Shared `AppDatabaseFactory` opens the encrypted `medtrack_secure.db` for the app
  and notification receivers.
- `StartupRecordPolicy` is the explicit retention entry point on application start.

### Remaining work

- Replace ad-hoc schema changes with explicit, data-preserving Room migrations and enable schema export.
- Establish the current encrypted database version as a tested upgrade baseline; document which older versions are supported. Unsupported schemas must fail clearly without resetting the database.
- Add migration fixtures and checks for preserved records, relationships, and attachment references.
- Keep notification receivers and Hilt database construction on the same migration list.

### Acceptance

- A synthetic patient discharged more than 30 days ago remains accessible after restart (already covered by safeguard tests; keep green).
- App startup, boot handling, and reminder delivery cannot silently rebuild and erase an incompatible database (already covered for missing migrations; keep green as migrations are added).
- A migration verification harness checks row contents and foreign-key integrity and is extended by every subsequent schema task.

### Starting points

`MedTrackApp.kt`, `di/DatabaseModule.kt`, `data/db/AppDatabase.kt`, `notification/FollowUpBootReceiver.kt`, and `notification/FollowUpNotificationReceiver.kt`.

## MT-003: Introduce admissions and stable record context

Priority: P0 | Depends on: MT-002 | Status: Refined by [PM-003](04-patient-information-model.md#pm-003-identity-episodes-involvement-intake-stub)

### Work

- Add admission records with patient ID, status, admission/discharge times, and responsible doctor.
- Link visits and their dependent records to an explicit admission context, with integrity checks preventing cross-patient associations.
- Add hospital identifiers as optional identity fields; do not require a phone number to identify an inpatient.
- Provide manual admission, discharge, and readmission actions.
- Write and test a legacy conversion policy. Preserve uncertain historical grouping as explicitly marked legacy context rather than inventing admission dates or separate episodes.
- Stop using the patient's global discharge flag as the authoritative admission state.

### Acceptance

- Discharging and readmitting the same patient creates two distinct episodes without copying or losing earlier records.
- Concurrent or repeated admission requests cannot create two active admissions
  for one patient.
- Duplicate names require explicit selection before an update; room alone never identifies the patient.
- Existing visit-linked records remain accessible after migration, including patients with no visits.
- New writes require a valid patient/admission relationship. Incompatible legacy write paths are adapted or disabled with an explanation.

### Starting points

`data/db/entity/`, `data/db/dao/PatientDao.kt`, `data/repository/`, and `ui/patient/`.

## MT-004: Add transactional events and audit history

Priority: P0 | Depends on: MT-003 | Status: Refined by [PM-005](04-patient-information-model.md#pm-005-provenance-core-events-revisions-audit-operations)

### Work

- Add an event/audit foundation carrying patient/admission IDs, event time, recorded time, actor/source, event type, and related record IDs.
- Record corrections as revisions with links to earlier entries; preserve prior values.
- Make related record changes and their audit entries atomic through repository transactions.
- Add operation IDs for retryable commands so a retry cannot apply the same change twice.
- Introduce a testable clock and a documented time-storage/display convention.
- Implement shared write operations for manual UI and AI tools to prevent either path bypassing validation or history.

### Acceptance

- An injected failure during a multi-record write leaves no partially applied clinical change.
- Retrying a successful command with the same operation ID produces no duplicate event.
- A corrected entry exposes its original value, replacement, source, and timestamps.
- Actor attribution distinguishes the local doctor from imported information and AI proposals; it does not imply authenticated multi-user identity.

### Starting points

`data/repository/ClinicalRepository.kt`, `ui/visit/NewVisitViewModel.kt`, and `mcp/MedTrackMcpService.kt`.

## MT-005: Track structured locations and transfers

Priority: P1 | Depends on: MT-004 | Status: Refined by [PM-004](04-patient-information-model.md#pm-004-independent-dated-assignments--recordtransfer)

### Work

- Add hospital, department, unit, floor, ward, room, and bed fields, allowing unknown values.
- Maintain location history with effective times and a derived current location per admission.
- Add a transfer form and show current placement in patient headers.
- Preserve existing room values as legacy location information without inferring missing ward or bed data.
- Resolve reminder location from the current admission rather than the room on an old visit.

### Acceptance

- Two transfers preserve all placements and show the correct current location.
- A backdated transfer does not incorrectly replace a more recent current location.
- A reminder created before a transfer displays the new location when delivered.
- Correcting a transfer retains its prior values and provenance.

### Starting points

`data/db/entity/VisitEntity.kt`, `data/db/model/PatientListItem.kt`, `data/db/dao/FollowUpDao.kt`, and the room-update tool.

## MT-006: Record clinical reviews, problems, and plan revisions

Priority: P1 | Depends on: MT-004 | Status: Refined by [PM-007](04-patient-information-model.md#pm-007-clinical-assessment-objects)

### Work

- Extend manual clinical reviews with attributed observations and separate event/entry times.
- Add allergies with explicit unknown versus documented absence, and problem/diagnosis status including suspected and confirmed.
- Add current treatment-plan text with preserved revisions.
- Build the initial patient timeline using clinical and administrative events.
- Surface missing data as unknown; migrate free-text history and diagnoses without asserting clinical certainty not present in the original.

### Acceptance

- A new review updates the current overview and appears in the timeline.
- Changing a suspected diagnosis to confirmed retains the earlier assessment.
- Editing the treatment plan exposes both current and prior versions.
- All records remain viewable and editable through manual workflows offline.

### Starting points

`ui/visit/`, `ui/patient/PatientProfileScreen.kt`, `data/db/entity/PatientEntity.kt`, and `data/db/entity/VisitEntity.kt`.

## MT-007: Preserve medication and delivered-treatment history

Priority: P0 | Depends on: MT-006 | Status: Refined by [PM-008](04-patient-information-model.md#pm-008-medication-procedure-investigation-objects)

### Work

- Represent medication orders with dose, route, frequency, duration, and instructions when known.
- Add start, change, hold, resume, and stop operations with reason and effective time.
- Replace deletion-as-discontinuation with a retained stopped state and history.
- Add manual administration and procedure events, separate from orders and intended tasks.
- Preserve legacy medication text without inferring administration or current activity from an old prescription.
- Update both manual and tool-based medication paths to use the shared operations.

### Acceptance

- Start, change, hold, resume, and stop produce a traceable history and the correct active regimen.
- Stopping an order never removes its previous versions or administration events.
- Creating an order does not record a dose as administered.
- Two orders for the same medicine can be distinguished by ID; tools cannot silently modify the first name match.

### Starting points

`data/db/entity/MedicineEntity.kt`, `data/db/dao/MedicineDao.kt`, and medication operations in `mcp/MedTrackMcpService.kt`.

## MT-008: Unify tasks and reliable review reminders

Priority: P0 | Depends on: MT-005, MT-007 | Status: Refined by [PM-010](04-patient-information-model.md#pm-010-caretask-reminders-response-write-back)

### Work

- Make `Task` the clinical work item. Link zero or more reminder schedules to a
  task and store notification attempts separately from both.
- Preserve existing task and follow-up records using documented matching and
  deduplication rules; ambiguous legacy pairs remain separate and flagged rather
  than being silently merged.
- Add owner/role, priority, due time, pending/in-progress/completed/cancelled status, and outcome.
- Separate task state from notification attempt/delivery state.
- Add absolute date/time and relative-duration controls with a preview of the resolved time.
- Persist task changes before scheduling and reconcile alarms on startup/reboot so interruption does not strand reminders.
- Support reschedule, completion, cancellation, permission-denied states, and inexact-alarm fallback messaging.
- Define discharge behaviour: explicitly resolve, cancel, or retain outstanding work with attribution; never silently carry it into a readmission.

### Acceptance

- A four-hour reminder resolves correctly across midnight using a controllable clock.
- Completion or cancellation prevents later stale alarm delivery; rescheduling does not duplicate notifications.
- Pending work survives process death and device reboot and remains overdue until acted on.
- Notification denial is visible in the app and does not falsely mark a task complete or a notification delivered.
- Notification dismissal leaves the task pending; a transfer updates displayed location.
- Multiple reminder attempts do not create duplicate clinical tasks, and a task
  remains the source of truth for completion state.

### Starting points

`data/db/entity/TaskEntity.kt`, `data/db/entity/FollowUpEntity.kt`, `ui/followup/`, and `notification/`.

## MT-009: Capture care-team advice, questions, and reports manually

Priority: P1 | Depends on: MT-006, MT-008 | Status: Refined by [PM-009](04-patient-information-model.md#pm-009-referrals-advice-questions-communication)

### Work

- Add care-team members with name, role, specialty, and admission relationship.
- Add attributed consultation advice and a separate treating-doctor decision to adopt, decline, or defer it.
- Allow manual entry of nurse questions and original pasted messages with open/resolved status and linked tasks.
- Associate attachments with admissions and optional reviews; retain original files and manual document category.
- Store collection/reporting times separately from upload time when provided.
- Expose unreadable, missing, and failed file imports; do not label attached documents as analysed.

### Acceptance

- A specialist's advice remains separate from the active plan until the doctor records its adoption.
- A nurse question can link to a task and a documented response without losing the original text.
- Existing and newly attached PDFs/images reopen after app restart.
- Failed imports do not appear as successfully attached reports; a saved clinical note is not lost because an attachment fails.

### Starting points

`data/storage/FileStorageManager.kt`, `data/db/entity/ReportEntity.kt`, and `ui/visit/VisitDetailViewModel.kt`.

## MT-010: Complete the census, patient workspace, and work queue

Priority: P1 | Depends on: MT-005 through MT-009 | Status: Refined by [PM-011](04-patient-information-model.md#pm-011-census-workspace-inbox-ui)

### Work

- Update the census to show active admissions, current location, concise status, and pending work counts.
- Add search and filters for identity, ward/unit, and outstanding work.
- Assemble overview, timeline, medications, reports, team, and tasks into the patient workspace.
- Provide a cross-patient due/overdue queue and unresolved-question list with direct navigation to the correct admission.
- Make discharged admission history accessible and handle empty, loading, and failure states.

### Acceptance

- With 20 synthetic patients, the doctor can locate a patient by name or current bed and reach their pending tasks.
- Census counts and work queues agree with saved task states after edits and restart.
- The overview distinguishes active orders, delivered treatment, unresolved questions, and unknown information.
- All core screens remain usable in airplane mode without waiting for AI.

### Starting points

`ui/dashboard/`, `ui/patient/`, `ui/common/components/PatientCard.kt`, and `ui/navigation/AppNavGraph.kt`.

## MT-011: Align existing AI tools with the new record model

Priority: P0 | Depends on: MT-010 | Status: Refined by [PM-012](04-patient-information-model.md#pm-012-single-write-path-for-forms-mcp-ai)

### Work

- Update tool schemas and service methods to use patient, admission, and target-record IDs and shared audited write operations.
- For every preceding schema delivery unit, inventory affected AI/MCP operations
  and disable each incompatible operation until its replacement passes tests.
- Replace first-match resolution with explicit ambiguous/not-found results and patient selection.
- Remove or adapt operations that delete medication history or overwrite location history.
- Persist assistant operation outcomes and distinguish proposed, applied, rejected, and failed changes.
- Require review for AI-generated clinical changes; allow validated explicit administrative changes with a visible receipt.
- Bound multi-step execution, handle follow-up tool calls explicitly, and report partial failures without claiming all actions succeeded.
- Show unsupported OCR/clinical-analysis requests honestly; do not present a report filename lookup as analysis.

### Acceptance

- Duplicate-name and stale-location tests cannot update the wrong patient.
- Repeated requests with the same operation ID cannot duplicate tasks or transfers.
- AI and manual updates produce equivalent validation and audit records.
- Rejected proposals do not change clinical records, and missing API access leaves manual workflows available.
- Mock model responses verify tool execution; routine tests require no live provider credentials.

### Starting points

`ai/AiAssistantOrchestrator.kt`, `mcp/MedTrackMcpCatalog.kt`, `mcp/MedTrackMcpService.kt`, `mcp/transport/`, and `ui/assistant/`.

## MT-012: Require Google sign-in and encrypt local clinical storage

Priority: P0 before real-patient use | Depends on: MT-011 | Status: Todo

Note: Encryption scaffolding (SQLCipher database, `SecureFileStore` attachments,
disabled Auto Backup) may land before MT-011. Google Sign-In UI gating and the
remaining backup/key-recovery acceptance criteria still block real-patient use.
Do not treat partial encryption as MT-012 Done.

### Work

- Review local database/files, Android backup rules, notification text, logs, API credentials, and the local MCP server's exposure.
- Preserve the Phase 0 defaults: synthetic data only, cloud AI opt-in, local MCP
  disabled by default, and no destructive database recovery.
- Require Google sign-in through Firebase Authentication before clinical screens or
  local clinical data are accessible; support signed-in session restore and sign-out.
- Encrypt the Room database, report/photo files, notes, and future conversation
  artifacts at rest using Android Keystore-backed keys so browsing app storage
  outside the unlocked app does not reveal readable patient content.
- Disable or encrypt Android Auto Backup / off-device copies so plaintext clinical
  databases and attachments are not uploaded.
- Document key-loss and recovery limitations; provide protected backup/export and
  restore for encrypted records plus attachments, with an explicit retention policy.
- Replace bundled production API credentials with an explicit protected credential flow or backend design.
- Restrict or disable external tool-server access by default; require authenticated access if enabled.
- Make cloud AI opt-in for patient context and enforce configured provider/data-handling constraints on every fallback path.
- Make restore validate compatibility and recover without destroying existing data if interrupted or invalid.
- Keep notifications, logs, crash reports, and Analytics free of patient identifiers and clinical content.

### Acceptance

- Clinical screens are blocked until Google sign-in succeeds; sign-out returns the app to that blocked state.
- After a prior successful sign-in, encrypted local records remain usable offline for manual workflows.
- Copying the app database or report files off-device, or opening them with a generic viewer, does not reveal readable patient content.
- No production secret or patient content appears in committed fixtures, routine logs, or default lock-screen text.
- AI-disabled mode makes no patient-data network requests; disallowed providers are never used as fallbacks.
- An unauthorized client cannot invoke record-changing tools.
- A backup restores into a clean installation with matching records, history, and accessible attachments after authentication.
- Invalid restore input preserves the existing database and gives a clear failure message.
- Key-loss behaviour is documented and verified in a controlled synthetic scenario.

### Starting points

`app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/`, `app/build.gradle.kts`,
`app/google-services.json`, `di/FirebaseModule.kt`, `ai/openrouter/`,
`mcp/transport/LocalMcpHttpServer.kt`, `data/db/`, and `data/storage/`.

## MT-013: Validate the first usable inpatient workflow

Priority: P0 | Depends on: MT-012 | Status: Refined by [PM-013](04-patient-information-model.md#pm-013-model-and-workflow-acceptance)

### Work

- Run an end-to-end scenario: admit, record assessment and plan, add medication, record administration, transfer, hold/stop medication, schedule review, complete work, discharge, and readmit.
- Verify manual question/advice capture and attachment retrieval through the same scenario.
- Exercise migration from the supported baseline, offline operation, process death, reboot, denied permissions, and backup/restore.
- Run targeted automated tests, Android lint, and debug assembly; record device/API versions used for notification checks.
- Write a short usage guide and known-limitations report.

### Acceptance

- No record loss, wrong-patient update, duplicate retry, or false completion occurs in the scripted scenarios.
- Task/reminder state and medication/location history remain consistent after restart and restore.
- The core workflow works without AI and with approximately 20 synthetic active patients.
- Any device-specific notification limitations are reproducible and visible to the user.
- Remaining blocking defects are resolved before marking this task Done; deferred clinical intelligence is explicitly documented.

## Delivery notes template

Append this information to each task as implementation proceeds:

```text
Status: Todo / In progress / Blocked / Done
Implementation: commit or changed files
Verification: commands, scenarios, and results
Limitations or blockers: details, or none
```

## Next task set, after this milestone

Create a separate backlog for intent-based model routing, OCR and structured report extraction, source-linked clinical analysis with review, and automated conversation processing. Use the tested inpatient record and tool operations from this set as their foundation.
