# MedTrack: High-Level Product Specification

Status: Draft for product and implementation planning  
Date: 2026-09-19  
Primary platform: Android

Detailed architecture: [LLM orchestration and chat-first interface](llm-and-chat-architecture.md).

Deployment and authority: [Hybrid architecture specification](hybrid-architecture.md). The first release serves Ankita on one active device; the backend is account-isolated and prepared for additional independent users. Clinical records and commits remain local; shared records and multi-device synchronization are later scope.

Decision layer: [Jev backend harness](jev-orchestration.md). All language-model inference is cloud-based; optional on-device speech transcription remains available independently.

Clinical entity and notification design: [Patient medical record and actionable reminders](patient-record-and-reminder-workflow.md).

Start with the [patient information model](patient-information-model.md), then the [field-level data contract](patient-management-data-contract.md). The [patient-management workflow for Ankita](patient-management-workflows.md) is downstream of those models. Establish reusable entities and relationships before finalizing application flows.

## 1. Product purpose

MedTrack is a personal inpatient management and clinical support app for a doctor caring for approximately 20 patients across different hospital locations. It helps the doctor maintain an accurate account of each patient's condition, treatment, care team, pending work, and next review.

The doctor should be able to answer these questions quickly:

- Who is this patient, and where are they now?
- What has happened since I last reviewed them?
- What is the current diagnosis and treatment plan?
- What treatment was ordered, given, changed, held, or stopped?
- What results, questions, or specialist advice need my attention?
- What needs to happen next, who is responsible, and when is it due?

The app combines structured records, a chronological patient timeline, tasks and reminders, and an AI assistant. AI helps interpret inputs and propose actions; the saved patient record remains the source of truth.

## 2. Intended users and scope

Each signed-in user has a [cloud professional profile and subscription/access record](user-profile-and-subscription.md), editable through My Profile on Android. Specialty and hospital affiliations use reusable references. The cloud is authoritative for profile/subscription information; clinical patient records remain local in the first release.

The initial user is a single doctor managing their inpatient workload on an Android device. Nurses, patients, and other physicians are sources of information and members of the care team; they do not require app accounts in the initial version.

Access to the app requires Google sign-in for that doctor. Firebase Authentication with Google is the planned identity provider for the first release. Unsigned or anonymous use is out of scope once authentication is enabled.

The app supports multiple concurrent admissions under the doctor's care and retains prior admission history. It should remain useful for manual record keeping when AI or network access is unavailable, after the doctor has already signed in on the device.

Initial scope does not include a complete hospital information system, billing, outpatient scheduling, automatic medication administration, or autonomous diagnosis and prescribing. Direct interpretation of raw radiology images is outside initial scope; analysis starts with written radiology reports.

Until the data-protection and recovery work in Phase 1 has passed its acceptance
criteria, development and verification must use synthetic patient data only.
Cloud AI is opt-in, and local tool-server access is disabled by default. Schema
work must fail without deleting an incompatible database.

## 3. Core workflows

### 3.1 Patient census and location

- Register or identify a patient and open an admission episode.
- Maintain a stable patient identity and hospital identifier when available.
- Record hospital, department, unit, floor, ward, room, and bed as applicable.
- Show active patients with current location, concise clinical status, and outstanding work.
- Search by identity or location and filter by ward, unit, or pending work.
- Record transfers with previous location, new location, and effective time.
- Discharge a patient without deleting their admission history; support later readmission as a separate episode.

Location is a changing attribute, not the patient's identity. Tasks and reminders remain linked to the patient and admission after a transfer.

### 3.2 Clinical record and timeline

- Record presenting concerns, relevant history, allergies, observations, diagnoses, and progress notes.
- Distinguish suspected diagnoses from confirmed diagnoses.
- Maintain the current treatment plan and its revisions.
- Show a chronological timeline of reviews, transfers, treatment changes, reports, consultations, and completed tasks.
- Preserve event time, entry time, author or reported source, and links to supporting material.
- Clearly distinguish documented facts, reported information, AI interpretations, and doctor-approved decisions.

The doctor can review the latest state and trace how it developed. Corrections retain the previous entry and identify what changed.

### 3.3 Medication and treatment tracking

- Record medication orders with drug, dose, route, frequency, intended duration, and instructions when provided.
- Record starts, dose changes, holds, resumptions, and discontinuations with times and reasons.
- Keep medication orders separate from recorded administration events. An order does not imply that a dose was given.
- Allow treatment and procedure events beyond medications.
- Display current treatment alongside historical changes.

Stopping a medication must preserve its history rather than delete it.

### 3.4 Tasks, reviews, and reminders

- Create patient-linked tasks with an owner or role, priority, due time, and instructions.
- Support pending, in-progress, completed, and cancelled states, with completion details.
- Accept absolute times and relative instructions such as “review this patient in four hours.”
- Display the resolved date and time before saving an AI-interpreted reminder.
- Show due and overdue work across all patients.
- Provide notifications with patient identity, current location, and the required action, subject to lock-screen privacy settings.
- Allow completion and rescheduling, retaining the change history.
- Provide Add note and Complete with note from a notification-linked task sheet. Save attributed notes, actual care time, entry time and completion events into the patient's admission record atomically; adding a note alone does not complete the task.
- Restore pending reminders after device restart and expose notification or scheduling permission problems.

Notifications are prompts to act; delivery or dismissal does not mean the clinical task was completed. Exact delivery depends on Android permissions and device conditions and must be tested on supported devices.

### 3.5 Reports and documents

- Attach photographed, scanned, or imported images and PDFs to the correct patient and admission.
- Retain the original document alongside extracted text and derived information.
- Classify documents, initially including laboratory reports, written radiology reports, prescriptions, and clinical notes.
- Use OCR or document-capable models to extract content and flag uncertain or unreadable sections.
- Extract lab values with units, collection times, and reference ranges when present; avoid inventing missing fields.
- Support comparison of compatible results over time.
- Summarize written radiology findings and recommendations with links to the source text.
- Present suggested follow-up actions for doctor review.

A report's upload time, collection time, and reporting time are distinct. AI analysis must not silently become a confirmed diagnosis or treatment order.

### 3.6 Conversations, nurse questions, and specialist input

- Accept typed notes or pasted conversations, with voice capture and transcription as a later input channel.
- Identify the patient, participants, source, and time where available.
- Extract questions, observations, recommendations, decisions, and proposed actions.
- Ask for clarification when the patient, speaker, or intended action is ambiguous.
- Track nurse questions as unresolved, answered, or requiring further review.
- Associate specialist advice with the physician, specialty, relevant consultation, and resulting decisions.
- Preserve the original conversation or note so extracted statements can be checked.

Advice from another physician remains attributed advice until the treating doctor records its adoption into the plan. Sending messages to external people is outside the initial scope.

### 3.7 Patient overview and clinical support

- Produce a concise overview of current condition, active problems, treatments, recent changes, pending results, and upcoming actions.
- Answer questions using the selected patient's longitudinal record and cite the relevant entries or documents.
- Support doctor-requested interpretation and diagnostic reasoning while identifying missing information and uncertainty.
- Keep AI suggestions separate from approved clinical records.
- Require doctor review before adopting AI-generated diagnoses, treatment changes, or clinically consequential recommendations.

## 4. AI and multi-model routing

### 4.1 Input and event flow

The intended flow is:

```text
Typed request / document / conversation / recorded update
    -> Identify patient and admission
    -> Classify intent and required capabilities
    -> Retrieve relevant record context
    -> Execute an administrative workflow or request clinical/document analysis
    -> Validate the proposed result and request review where needed
    -> Save approved changes and their provenance
    -> Update timeline, patient overview, tasks, and reminders
```

One input may contain several actions. For example, “the patient moved to bed 12, the antibiotic was stopped, and review is due in four hours” must produce separate, traceable proposed updates.

### 4.2 Routing responsibilities

A backend routing layer uses Jev structured decisions plus deterministic policy to classify inputs into one or more workflow types. Cloud generative models extract fields and produce clinical explanations; Jev does not generate those contents:

| Workflow | Typical processing |
| --- | --- |
| Record lookup or administrative update | Structured tools; lightweight model when language interpretation is needed |
| Task or reminder | Resolve patient, action, owner, and time; use scheduling tools |
| Clinical note or conversation | Extract attributed events and proposed record changes |
| Document ingestion | OCR or document extraction followed by classification and structured extraction |
| Clinical interpretation | More capable model with relevant patient context and source material |
| Ambiguous or unsupported request | Clarification or explicit unsupported result |

Routing is based on intent, required capabilities, uncertainty, and clinical consequence. Model fallback for availability is a separate mechanism and must preserve the capabilities and data-handling requirements of the workflow.

### 4.3 Execution rules

- Models access and change records through constrained, validated tools.
- Straightforward, explicit administrative updates may execute directly after identity and field validation, with a visible receipt and correction path.
- Ambiguous identity, uncertain extraction, and clinically consequential AI-generated changes require review before saving as approved facts or actions.
- Use stable patient and admission IDs internally; never silently select the first of several matching patients.
- Document text and conversations are data, not instructions granting permission to execute tools.
- Separate proposed changes from applied changes and report partial failures accurately.
- Prevent duplicate updates when requests are retried.
- Track model, processing time, source inputs, and approval status for generated results.
- Send only the context needed for the selected task; provider and model policies must be explicit configuration.

Jev is the chosen decision service, with version and thresholds subject to evaluation. Generative models and their providers remain implementation choices. No on-device LLM is planned; device speech recognition is a separate input capability.

## 5. High-level information model

| Record | Purpose |
| --- | --- |
| Patient | Stable identity, demographics, relevant background, and allergies |
| Admission | Inpatient episode, responsible doctor, admission and discharge details |
| Location history | Current placement and timestamped transfers |
| Care-team member / consultation | Physicians, specialties, roles, advice, and related decisions |
| Clinical event / note | Attributed observations, reviews, decisions, and corrections |
| Problem / diagnosis | Status, evidence, and changes in diagnostic assessment |
| Treatment plan / revision | Current intended care and its version history |
| Medication order / event | Intended medication regimen and its lifecycle |
| Administration / procedure event | Recorded care actually delivered |
| Task / reminder | Intended action, owner, due time, status, and outcome |
| Document / extracted result | Original report, extracted content, and structured observations |
| Conversation / question | Source communication, participants, unresolved issues, and responses |
| AI analysis / proposed change | Derived interpretation, supporting sources, and review state |
| Audit entry | Who or what changed a record, when, and why |

The current patient overview is derived from these records. A chat transcript alone must not serve as the clinical record.

## 6. Main app surfaces

Chat is a primary entry point: the census and patient workspace have a persistent bottom composer that opens directly into conversation. Doctors can describe a new patient without opening a form first. Manual Add Patient remains available. Conversations show explicit patient context, preserve drafts, and render reviewable changes and saved receipts alongside answers.

- **Sign-in:** required Google account authentication before clinical data is accessible.
- **Patient census:** active admissions, locations, clinical summaries, and pending work.
- **Patient workspace:** overview, timeline, treatment plan, medications, reports, care team, and tasks.
- **Work queue:** due reviews, overdue tasks, new reports, unresolved questions, and items awaiting approval.
- **Capture and assistant:** typed input, document upload, and later voice input, with explicit patient context.
- **Review screen:** original source, extracted information, proposed changes, and approve/edit/reject controls.
- **History:** discharged admissions and prior records available for reference.

## 7. Authentication, encryption, and reliability

### 7.1 Google sign-in

- The app requires Google sign-in before the doctor can view or change clinical records.
- Firebase Authentication with Google Sign-In is the planned mechanism for the Android release.
- Session state may persist on the device so the doctor can reopen the app without signing in on every launch, subject to Firebase/Auth session rules and device security.
- Sign-out must clear the authenticated session and block access to clinical screens until the next successful sign-in.
- First-time sign-in and account recovery require network access. After a valid local session exists, previously stored encrypted records remain available for offline manual use; features that need the network (AI, fresh authentication) fail honestly when offline.
- Authentication identifies the treating doctor for app access. It does not by itself create multi-user hospital accounts or shared ward workspaces in the first release.

### 7.2 Encrypted local storage

All clinically sensitive local data must be protected at rest so that opening the app's files, folders, database, caches, or exported working copies outside the unlocked app does not expose readable patient content.

This includes, at minimum:

- The Room/SQLite database and any derived indexes or temporary database files
- Patient photos and report attachments (images and PDFs)
- Free-text clinical notes, diagnoses, medicines, tasks, and follow-up content
- Conversation or pasted-message originals and extracted text once those features exist
- Local AI or assistant working artifacts that contain patient context
- Encrypted backups and exports produced by the app

Requirements:

- Encrypt database contents and attachment/file storage with keys that are not stored in plaintext alongside the data.
- Prefer platform-backed key protection (Android Keystore) so the encryption key is not trivially extractable from app storage.
- Document key-loss behaviour: if the key cannot be recovered, protected data may become unreadable; backup/recovery design must state this limitation clearly.
- Android Auto Backup / cloud device backup must not upload plaintext clinical databases or attachments. Disable unprotected backup or encrypt backup payloads before any off-device copy is made.
- Notifications, logs, crash reports, and Analytics events must not contain patient identifiers or clinical content.
- Passing FileProvider or share intents must not leave durable plaintext copies in world-readable locations.

### 7.3 Reliability and related data rules

- Local records and manual workflows remain available offline after prior successful sign-in; AI-dependent operations show pending or failed states honestly.
- Protect patient information in transport, model requests, and any future sync channel with the same confidentiality expectations as local storage.
- Select provider data-handling settings before using real patient information; do not treat unrestricted provider fallback as acceptable by default.
- Define backup, recovery, retention, and explicit deletion behaviour. Discharge must not automatically purge records after a fixed short period.
- Use database migrations that preserve records during upgrades.
- Keep persistent audit history for important changes and approvals.
- Handle date, time zone, and relative-time interpretation consistently.
- Validate patient matching, treatment history, reminders, document extraction, tool execution, sign-in gating, and encryption with meaningful automated and device-level tests.

Multi-device synchronization, shared clinical accounts, and hospital-system integrations can be added later. They are not prerequisites for the first single-doctor release.

## 8. Existing implementation and development phases

The current codebase supplies Android screens, an encrypted local Room/SQLCipher
database, patient and visit records, tasks, medication entries, encrypted report
attachments, follow-up notifications, Firebase Analytics, debug synthetic
seeding, and an OpenRouter assistant connected to record-management tools.

Startup no longer purges discharged patients or uses destructive Room migration
fallback. The local MCP HTTP server is disabled by default. Google sign-in is
specified but not yet enforced in the UI. Attachment and database encryption
scaffolding exists; complete auth gating, key-loss documentation, and backup
recovery remain in MT-012. The app still calls OpenRouter with a developer key
from local configuration; the intended architecture moves credentials to a
backend with Jev decision routing.

It currently uses visit-level room fields, a model fallback list, and filename-based blood-report filtering. It does not yet provide the inpatient event model, clinical model routing, OCR, report interpretation, or conversation-processing workflows described here. Medication deletion-as-stop and visit-level location modelling still conflict with the intended longitudinal record.

### Phase 0: Safe development baseline

Before extending the record model, stop automatic record purging, remove
destructive database fallback, disable unauthenticated local tool-server access
by default, and establish a reproducible build with synthetic fixtures. These
minimum safeguards do not replace the complete protection, backup, and recovery
work later in Phase 1.

### Phase 1: Dependable inpatient organizer

Extend the existing app with admissions, structured locations and transfers, care-team records, a patient timeline, medication lifecycle history, improved tasks and reminders, and persistent audit history. Replace remaining destructive retention assumptions and add explicit schema migrations. Establish reliable manual workflows before introducing clinical automation. Before real-patient use, finish Google sign-in gating and complete encrypted storage, backup, and key-recovery work (MT-012); encryption scaffolding may land earlier than the rest of the record model.

### Phase 2: Routed administrative assistant

Introduce explicit intent routing, reliable patient/admission resolution, validated tools, multi-action handling, durable processing history, and review of proposed changes. Support record queries, transfers, task updates, and relative-time reminders.

### Phase 3: Document and clinical support

Add document extraction, report classification, structured lab results, written radiology summaries, source-linked patient summaries, and reviewed clinical analysis. Evaluate extraction and interpretation using representative test cases before clinical use.

### Phase 4: Conversation-assisted coordination

Add conversation ingestion, nurse-question tracking, specialist-advice extraction, and optional voice transcription. Derive proposed events and tasks while retaining attribution and review controls.

## 9. Representative acceptance scenarios

1. **Transfer:** A doctor moves a patient to another ward and bed. The current location changes, the previous location remains in history, and existing reminders use the new location.
2. **Medication stopped:** A doctor records that a medication was stopped and why. It leaves the active regimen while its order and prior events remain visible.
3. **Four-hour review:** A doctor requests review in four hours. The app shows the resolved time, saves a patient-linked task and reminder, and allows completion or rescheduling.
4. **New lab report:** A doctor uploads a report. The app retains the original, proposes extracted values with units and source references, flags uncertainty, and presents analysis for review.
5. **Radiology report:** A written radiology report is summarized with findings and recommendations linked to its text. Suggested actions remain proposals until accepted.
6. **Nurse question:** A pasted message becomes an attributed unresolved question and, after review, any related task or clinical event.
7. **Specialist recommendation:** Advice is linked to the specialist and consultation; the doctor's decision to adopt or decline it is recorded separately.
8. **Ambiguous patient:** Two patients match a name. The assistant asks the doctor to select one before modifying either record.
9. **Offline use:** After prior successful Google sign-in, the doctor can inspect encrypted local records and manually update care while AI is unavailable. The app does not claim that an unprocessed report has been analysed.
10. **Discharge and readmission:** Discharge removes an admission from the active census while preserving its history. Readmission creates a new episode for the same patient.
11. **Required sign-in:** Without a successful Google sign-in, clinical screens and local clinical records are inaccessible.
12. **At-rest confidentiality:** Copying the app's database or report files off the device, or browsing them with a generic file viewer outside the unlocked app, does not reveal readable patient content.

## 10. Decisions to resolve during detailed design

- Whether the first release must support multiple hospitals for one doctor.
- Required hospital identifiers and the initial source of patient demographics.
- Supported document formats, report languages, and expected scan quality.
- Whether voice input is needed in the first usable release.
- Model provider, hosting, data-handling requirements, and operating budget.
- Which changes can execute immediately and which always require explicit review.
- Retention periods, encrypted backup/export format, and key-recovery limitations if the Android Keystore-backed key is lost.
- Whether a second device factor (PIN/biometrics) is required in addition to Google sign-in before unlocking encrypted local data.

### Decisions already made for the first release

- Google sign-in is required; Firebase Authentication with Google is the planned provider.
- Local clinical storage (database, attachments, notes, conversations, and related artifacts) must be encrypted at rest.

These decisions refine implementation; the initial working scope is a single-doctor Android app with Google sign-in, encrypted local records, manual entry and document upload, configurable cloud AI, and reviewed clinical suggestions.
