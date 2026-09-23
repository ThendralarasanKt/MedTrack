# Task set 02: Chat, LLM workflows, clinical records, and actionable reminders

Status: Planned; all tasks Todo  
Scope: Implementation backlog only; no application code is changed by creating this list.

Sources: [LLM/chat architecture](../spec/llm-and-chat-architecture.md), [record and reminder design](../spec/patient-record-and-reminder-workflow.md), [foundation backlog](01-inpatient-foundation.md).

Current decision: no local LLM. Implement the [Jev backend harness](../spec/jev-orchestration.md) for structured decisions and cloud generative models for extraction/reasoning. Device-first English transcription remains independently in scope.

## Relationship to the existing backlog

WF-01 through WF-08 refine existing foundation milestones; they are not duplicate implementations. Complete the shared outcome once and link its evidence in both places. WF-09 onward break down the chat and LLM architecture into implementation units.

The existing Google sign-in and encrypted-storage requirements remain in force. Use synthetic records until their acceptance criteria and the applicable clinical-use checks pass. API keys are unnecessary for UI, schemas, domain operations, and mock-model tests; live inference evaluation requires configured backend credentials and usage authorization.

| Task | Deliverable | Dependencies | Foundation mapping |
| --- | --- | --- | --- |
| WF-01 | Final record schema and migration contract | Existing schema inventory | MT-002 through MT-009 |
| WF-02 | Patient/admission identity, people and clinical event core | WF-01, migration support | MT-003, MT-004, MT-005 |
| WF-03 | Typed clinical record modules | WF-02 | MT-006, MT-007, MT-009 |
| WF-04 | Task, schedule and response model | WF-02 | MT-008 |
| WF-05 | Precise timed scheduling and recovery | WF-04 | MT-008 |
| WF-06 | Notification completion and note write-back | WF-03, WF-05; authenticated write boundary | MT-008, MT-012 |
| WF-07 | Clinical timeline and unified work queue | WF-03, WF-04, WF-06 | MT-010 |
| WF-08 | Reminder/record integration verification | WF-05 through WF-07; identity/encryption integration | MT-013 |
| WF-09 | Persistent bottom chat and conversation storage | WF-01 for schema conventions | Chat shell |
| WF-10 | Typed workflow coordinator and model-independent contracts | WF-02, WF-04, WF-09 | MT-011 |
| WF-11 | Authenticated inference gateway and provider policy | WF-10 contracts; MT-012 identity contract | MT-011, MT-012 |
| WF-12 | Administrative chat and conversational patient creation | WF-05, WF-10, WF-11, WF-13, WF-20 for live routing | MT-011 |
| WF-13 | Review cards and versioned command execution | WF-03, WF-10; required before enabling writes in WF-12 | MT-011 |
| WF-14 | JPEG/PDF extraction with source evidence | WF-03, WF-11, WF-13 | Document processing |
| WF-15 | Source-linked clinical reasoning and summaries | WF-14, evaluated clinical model | Clinical support |
| WF-16 | Nurse questions and specialist conversation extraction | WF-13, WF-15 | Conversation workflows |
| WF-17 | End-to-end evaluation, observability and release gates | WF-08, WF-12 through WF-16, WF-20; WF-19 if voice enabled | MT-013 extension |
| WF-18 | Device/English transcription capability evaluation | Target device inventory | Local voice input |
| WF-19 | Device-first short dictation | WF-09, WF-18; WF-06 for notification-note integration | Voice capture |
| WF-20 | Jev decision service and evaluated routing policy | WF-10, WF-11, mock/evaluation contracts | Cloud decision layer |

Implementation sequence: record contracts, shared domain operations, reminders, notification responses, then model-enabled writes. WF-09 can proceed alongside record work using draft/mock data. WF-13 can precede live gateway integration using deterministic proposals; do not enable WF-12 writes before it passes.

## WF-01: Finalize the record schema and migration contract

Deliver:

- A concrete field dictionary and relationship schema from the record spec, including required/optional fields, enums, indexes, account ownership, and timestamps with precision/zone.
- Explicit representation of decision-maker, author, recorder, performer, consultant and approver.
- Mapping from the six current entities into admissions, events and typed records without fabricated times, medication activity or identities.
- Versioned migration plan and legacy fixtures; account/encryption migrations coordinated with MT-012.

Acceptance: every requested clinical category has a typed storage destination and timeline linkage; old records remain readable, uncertain values remain unknown, and schema review identifies no cross-patient relation gaps.

## WF-02: Implement identity, people, admissions and shared event writes

Deliver stable patient/admission/person IDs, care-team membership, transfers, the clinical-event envelope, revisions/audit, operation IDs, and shared transactional commands for UI/tools/notification responses.

Acceptance: duplicate names and changing beds cannot select a write target implicitly; same-operation retries do not duplicate events; actor attribution and occurrence/recorded times survive restart; concurrent admissions obey the one-active-admission rule.

## WF-03: Implement typed clinical record modules

Deliver in separately reviewable units:

- Problems, diagnoses, allergies, clinical decisions, encounter notes, and plan revisions.
- Medication definition/order, start/change/hold/resume/stop events, and separate administered/omitted/refused doses.
- Procedure orders and actual procedure events with performers and outcomes.
- Investigation orders, imaging-study metadata, versioned lab/radiology reports, original attachments, and typed observations with units/source references.
- Manual consultation/advice and question records, keeping external advice separate from the adopted plan.

Acceptance: medication orders cannot imply administered doses; scans requested cannot imply scans performed; report upload time cannot substitute for collection time; corrections retain originals; all categories are usable manually without AI. Source JPEG/PDF files reopen after restart.

## WF-04: Implement task, reminder and response entities

Deliver CareTask, ReminderSchedule, NotificationAttempt, TaskResponse and explicit state transitions. Migrate current tasks/follow-ups with documented deduplication rules. Preserve ambiguous legacy pairs separately.

Acceptance: task status is independent from notification status; tasks support due time, assignee, priority and outcomes; multiple schedules cannot create duplicate care tasks; completion/note responses link to the same patient/admission event model.

## WF-05: Implement 17:30 scheduling and recovery

Deliver:

- Explicit date/time/zone and relative-duration resolution, with clarification for vague or past times.
- Exact-alarm and notification capability checks and user-visible degraded scheduling status.
- Alarm registration through a persistent outbox; revision-specific identities; suppression of stale/completed/cancelled work.
- Startup, reboot, app update, clock/zone and permission-change reconciliation.
- Current location at display time and an overdue queue independent from notification delivery.

Acceptance: measure scheduled/actual times on the supported-device matrix against the record spec's timing target; test idle state, offline operation, denied/revoked permissions, restart and duplicate alarms. An inexact fallback must never display an exact-delivery promise. No LLM request occurs at reminder-trigger time.

## WF-06: Implement Complete, Add note and Complete with note

Deliver an authenticated notification task sheet and shared response command. Completion and notes include stable target IDs, actor, actual care time and entry time; structured treatment outcomes require their relevant fields. Provide Reschedule with preserved history.

Acceptance:

- Add note alone leaves the task open; Complete with note saves both in one transaction.
- Notes appear immediately in the correct patient's timeline after local commit, including offline.
- The record distinguishes the person entering the note from a reported decision-maker or performer.
- Duplicate taps/retries create one response; stale notifications and account mismatches cannot mutate records.
- Lock-screen actions require the authorized app session/unlock before clinical writes; errors preserve drafts and do not show false success.
- Follow-up medication/test changes mentioned in a note remain proposals until separately accepted.

Inline keyboard reply from the notification shade is optional later work. First deliver Add note as a direct, context-filled task sheet opened by the notification action.

## WF-07: Assemble medical record and work views

Deliver the PatientMedicalRecord query contract, source-linked timeline, current plan/medication views, investigation/result views, and unified task queue. Distinguish event time from entry time and show participant roles and provenance.

Acceptance: the 17:30 review example in the record spec produces consistent timeline, task, medication/investigation and conversation views. Historical admissions remain separate; pending results/questions and overdue work remain discoverable.

## WF-08: Verify reminders with identity and encrypted storage

Deliver automated domain tests and device scenarios for denied permissions, locked storage after reboot, logout/login, account change, time change, interrupted writes, and stale notifications.

Acceptance: no wrong-patient/account writes, false task completions, duplicate responses, or leaked clinical lock-screen content; recovery behaviour and actual timing are recorded. Close matching MT-008/MT-013 criteria only with executed evidence.

## WF-09: Build chat-first shell and persistent capture

Deliver a bottom composer on home/work/patient screens, immediate expansion/focus, explicit context chip, retained Add Patient action, typed messages/cards, local threads/drafts/attachments and recoverable job state. Keep provider/model names out of routine patient-facing UI.

Acceptance: doctor can start describing the first patient without opening Add Patient; keyboard does not obscure controls; drafts survive navigation/process recreation; patient changes do not reuse another patient's uncommitted actions; offline capture reports pending processing honestly.

## WF-10: Build typed coordinator and context assembly

Deliver versioned route/proposal/result schemas, patient/admission resolution, bounded multi-intent workflows, pending clarification state, scoped context queries, source/version tracking and mock provider responses.

Acceptance: duplicate names, negation, multiple patients, stale bed references, readmission and conflicting context require correct resolution; untrusted source text cannot issue commands; model output is validated before execution and raw chat is not the clinical source of truth.

## WF-11: Build inference gateway and model registry

Deliver server-side OpenRouter credentials, verification of the app's Google/Firebase identity, authorization, limits, provider adapters, role/capability mapping, policy-constrained fallbacks, cancellation, timeouts and content-free metrics. Use the same OpenRouter credential for Jev and generative models, with separate decision/chat adapters and route-specific policy validation. Integrate the Python decision adapter with LangChain orchestration where useful; do not assume the direct TypeSafe SDK supports OpenRouter unchanged. Direct clinical writes remain Android commands.

Acceptance: provider credentials are absent from shipped APKs; unauthorized requests fail; disallowed provider/model routes never execute; no background clinical-content analytics; transient retries do not trigger record writes. Development uses mocks until live credentials are configured outside source control.

## WF-12: Implement conversational administrative workflows

Deliver new-patient/admission drafting, lookup, location updates, task queries/completion, and absolute/relative follow-up scheduling. Use WF-20's evaluated Jev decisions before enabling live routing; mocked decisions may support earlier UI work. Ask only for essential missing fields. Record explicit date/zone for “5:30 pm” and preview natural-language relative deadlines.

Acceptance: a multi-action description produces patient-labelled actions and accurate receipts; identity creation is reviewed; missing information remains unknown; scheduling unavailable is distinguishable from saved task; all writes use WF-13 validation/review and shared commands.

## WF-13: Implement proposals, review and commit receipts

Deliver Save/Edit/Discard cards, clinical approval policy, expected-version checks, idempotent operations, immutable source links, transaction/outbox coordination, and correction events.

Acceptance: clinical AI suggestions cannot silently become orders; reject/edit does not retain accidental changes; stale approval refreshes; lost acknowledgements retrieve existing receipts; narrative streaming cannot falsely claim a committed action.

## WF-14: Implement JPEG/PDF document processing

Deliver persistent intake, original file preservation, type/size validation, text/OCR/vision processing, patient identity matching, page/region-linked extraction, structured observations, manual correction and revised-report handling.

Acceptance: test typed and scanned PDFs plus JPEGs, blurred pages, decimals, units, report amendments and patient mismatch. Unknown values are flagged, source material remains accessible, and no extracted finding becomes a confirmed diagnosis automatically.

## WF-15: Implement clinical reasoning and summaries

Deliver read-only reasoning over verified scoped context, current versus historical distinctions, source-linked summaries, explicit hypotheses/missing facts, and reviewable clinical proposals. Establish approved knowledge retrieval for guideline claims separately from patient-record retrieval.

Acceptance: clinician-reviewed synthetic cases assess factual support and uncertainty; citations resolve to permitted sources; model/prompt changes rerun evaluations; outdated summaries cannot overwrite newer facts. General software-test success is not a clinical approval gate.

## WF-16: Implement attributed conversation extraction

Deliver nurse-question and specialist-advice extraction from typed/pasted source conversations, participant attribution, decisions versus recommendations, and proposed tasks/events. Preserve original communications. Short dictation is specified separately in WF-19; long multi-speaker/ambient recording remains deferred.

Acceptance: “consider”, “not given”, “stopped yesterday”, and “nurse asks whether to stop” produce different statuses; unspecified actors remain unknown; specialist advice is adopted only through an explicit decision; no external messages are sent automatically.

## WF-17: Evaluate and release the combined workflow

Deliver a reproducible synthetic scenario suite across UI, notifications, records, inference, failure/retry and privacy boundaries. Record model/prompt versions, cost and latency per workflow without patient-content logging.

Acceptance: zero wrong-patient or unauthorized clinical writes in the release suite; device timing and notification response tests pass; missing permissions and network failures are accurately shown; model budget/fallback controls work; unresolved blockers remain open and prevent declaring the corresponding milestone Done.

## WF-18: Evaluate on-device capabilities

Deliver a representative **mid-tier Android / English** test matrix and runtime capability detection for explicit on-device transcription and language readiness. Exact handsets remain to be selected. Follow the [voice design](../spec/on-device-ai-and-voice.md); no local text model is evaluated or required.

Acceptance: test supported and unsupported devices/languages; older supported Android versions retain typed/manual flows. Document speech-language downloads and actual device results; do not equate an offline-preference flag with guaranteed local processing.

## WF-19: Add device-first short dictation

Deliver a tap-to-record microphone in chat and, after WF-06, the authenticated notification-note sheet. Use local speech when explicitly available, editable partial/final transcripts, lifecycle cleanup, permission/error handling, and no raw-audio retention by default. Cloud audio fallback requires separate opt-in.

Acceptance: airplane-mode dictation works on the documented device/language matrix; no duplicate submission from partial results; names, drug terms, doses, negation and times have measured error/correction cases; interruption retains existing drafts; approved transcripts follow normal record review. Explain separately if transcript text is sent for cloud reasoning.

## WF-20: Implement Jev decisions and evaluated routing policy

Detailed implementation units and dependencies: [JV-01 through JV-11](03-jev-openrouter-integration.md). Use that checklist for delivery and link its evidence here rather than implementing the same integration twice.

This task replaces the previous optional local-LLM experiment. Deliver in independently verified units:

- A backend `DecisionService` adapter for Jev through OpenRouter using the shared backend OpenRouter credential, request limits, timeouts, validation and minimized state. Verify the current decisions endpoint/schema, model ID and supported provider-policy controls through official references and synthetic contract tests before enabling live routing. No direct TypeSafe key or account is required by this plan.
- Versioned multi-intent Noul questions, segment-level Choice questions and any evaluated processing-demand Score. Extracted free-form fields remain the cloud extractor's responsibility.
- Per-question calibration and abstention thresholds based on labelled MedTrack cases, plus deterministic routing/approval constraints.
- A proposal-stage semantic check with source spans and typed actions; use as supplementary evidence, never as permission to commit.
- Pinned model/question/policy versions, content-free metrics, disabled raw clinical tracing, bounded failure handling and rollback.
- Mock contract tests, synthetic evaluation, then shadow-mode decisions before enabling routing.

Acceptance: mixed intents are preserved; low certainty/missing answers never become approval; Noul probability is not misread as a confidence field; raw images/audio are never sent as Jev state; malformed outputs, timeouts and policy-denied providers produce explicit fallbacks/queued states. Clinical approvals and patient identity remain enforced by deterministic code. Task completion/notes and timed notifications work without Jev. Both decision and chat adapters work through OpenRouter, with tested route-specific data controls and no direct TypeSafe credential. Release evidence includes false-negative/calibration measurements and no unauthorized or wrong-patient writes in the test suite.

## Tracking convention

For each WF task, record Status, changed files/commit, commands and executed scenarios, acceptance results, and remaining blockers. All entries are currently Todo. Creating this task list does not complete foundation milestones, enable live inference, or authorize production deployment.
