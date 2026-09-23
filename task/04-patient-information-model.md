# Task set 04: Patient information model (data-model first)

Status: PM-001–PM-013 Done  
Source: [Patient information model](../spec/patient-information-model.md),
[data contract](../spec/patient-management-data-contract.md),
[workflows](../spec/patient-management-workflows.md)  
Objective: Persist reusable masters, dated assignments, and episodes so bed,
department, consultant, and nurse changes are independent and every write path
produces the same structured records.

Checklist: [PM-backlog.md](PM-backlog.md) · Table map: [docs/care-model-table-map.md](../docs/care-model-table-map.md)

## Scope and working assumptions

- Parallel **care_*** tables (UUID string PKs) beside legacy Int-keyed tables until
  PM-006 migrates/bridges them. Do not break the current synthetic UI/seed path.
- No destructive Room fallback. Explicit migrations only; `exportSchema = true`.
- Synthetic data only until MT-012. Ankita workflow UI waits on this model (PM-011).
- Overlapping MT-002–MT-010 outcomes are delivered through PM-* tasks; do not
  re-implement the same schema twice under both IDs.

## PM-001: Shared contracts, schema export, migration baseline

Priority: P0 | Depends on: MT-001 | Status: Done

### Work

- Document Room table mapping for logical objects in the data contract.
- Add `ClinicalTime` (INSTANT / DATE / UNKNOWN) and Room converters; never
  fabricate midnight for unknown times.
- Add shared ownership columns: `id`, `ownerAccountId`, `createdAt`, `createdBy`,
  `version`.
- Enable Room schema export; bump database version with a non-destructive
  migration that only adds care tables.

### Acceptance

- Schema JSON exports for the new version.
- Unit tests cover ClinicalTime precision rules.
- App opens existing encrypted DB after migration without data loss on legacy tables.

### Starting points

`data/db/AppDatabase.kt`, `AppDatabaseFactory.kt`, `app/build.gradle.kts`.

### Delivery notes

Implemented under `app/src/main/java/com/medtrack/app/data/care/` and
`data/db/CareMigrations.kt`. Schema export:
`app/schemas/com.medtrack.app.data.db.AppDatabase/6.json`.
Verified: `testDebugUnitTest` (19 tests, including ClinicalTime + CareModelFoundation
Robolectric suite). Device `connectedDebugAndroidTest` blocked by MIUI USB install
restriction at time of run.

## PM-002: Master data (people, org, space, catalogues)

Priority: P0 | Depends on: PM-001 | Status: Done

### Work

- Persist Account (bootstrap stub), Person, Patient, ProfessionalRole,
  PersonContact, Hospital, Department, ClinicalUnit, ClinicalTeam,
  TeamMembership, PhysicalLocation, StationCoverage, DepartmentLocationUse,
  ReferenceConcept, ConceptAlias.
- Enforce scoped uniqueness and PhysicalLocation kind/containment rules in the
  shared write helpers (not UI-only).

### Acceptance

- Two wards can each have bed code `12` without collision.
- Department and ward are independent objects.
- Doctor/nurse are ProfessionalRole views of Person, not duplicated demographics.

### Starting points

`data/care/entity/`, `data/care/dao/`.

### Delivery notes

Implemented as `care_*` Room entities + DAOs.

## PM-003: Identity, episodes, involvement, intake stub

Priority: P0 | Depends on: PM-002 | Status: Done

### Work

- Add PatientIdentifier, HospitalEpisode, EpisodeLink, CareInvolvement,
  UnassignedIntake (+ source link stub columns as needed).
- One active inpatient episode per patient (owner workspace).
- CareInvolvement PRIMARY_TEAM / REFERRAL / ON_CALL with active/ended state.

### Acceptance

- Primary + consulting involvement without duplicate patient/episode rows.
- Ending involvement does not discharge the episode.
- UnassignedIntake retains hints without fabricating Patient/Episode.

### Starting points

`data/care/entity/`, `data/care/command/`.

### Delivery notes

`CreatePatientAndAdmission` command for tests and future UI.

## PM-004: Independent dated assignments + RecordTransfer

Priority: P0 | Depends on: PM-003 | Status: Done

### Work

- LocationAssignment, DepartmentAssignment, AdmissionTeamAssignment,
  NursingAssignment with half-open `[start, end)` ClinicalTime intervals.
- `RecordTransfer` commits any combination of bed / department / team / nurse
  changes atomically; unchanged relationships stay intact.
- One current location, one current primary department, one current primary team
  per active episode; confirmed bed exclusivity in the workspace.

### Acceptance

- Location change without consultant change, and the reverse.
- Nurse shift change without altering department.
- Synthetic model §8 scenarios pass in automated tests.

### Starting points

`data/care/command/RecordTransferCommand.kt`.

### Delivery notes

Covered by `CareModelFoundationTest` / instrumented tests.

## PM-005: Provenance core (events, revisions, audit, operations)

Priority: P0 | Depends on: PM-003 | Status: Done

### Work

- ClinicalEvent, EventParticipant, RecordRevision, AuditEntry, AppliedOperation.
- Wire commands to create event/audit/operation rows in the same transaction.
- Idempotent operation IDs.

### Acceptance

- Retry with same operationId returns original receipt; different payload rejected.
- Partial failure leaves no orphan clinical rows.

### Delivery notes

`CareProvenanceWriter` + schema 7; `CareProvenanceTest` covers retry, conflict,
and transactional rollback.

## PM-006: Legacy bridge (patients / visits → care model)

Priority: P0 | Depends on: PM-004, PM-005 | Status: Done

### Work

- Map legacy Patient/Visit/Medicine/Task/Report/FollowUp per data-contract §13.
- Preserve unknown episode boundaries as explicitly uncertain; do not invent
  admission dates.
- Keep legacy UI reading legacy tables until cutover flags say otherwise.

### Acceptance

- Seeded synthetic fixtures remain readable after bridge.
- No invented precision for missing fields.

### Delivery notes

`LegacyCareBridge` / `care_legacy_links`; schema 8. Clinical destinations for
visit/meds/tasks/reports/follow-ups deferred to later PM tasks.

## PM-007: Clinical assessment objects

Priority: P1 | Depends on: PM-005 | Status: Done

Encounter, ClinicalNote, Problem, Allergy, AllergyAssessment, Observation,
ClinicalDecision, CarePlanRevision — see data contract §5.

### Delivery notes

`CareClinicalAssessmentService` + schema 9; `CareClinicalAssessmentTest`.

## PM-008: Medication, procedure, investigation objects

Priority: P1 | Depends on: PM-007 | Status: Done

MedicationDefinition/Order/Administration, Procedure*, Investigation*, Specimen,
DiagnosticReport, Document links — data contract §6–§7.

### Delivery notes

`CareTherapyCommandService` + schema 10; `CareTherapyCommandTest`.

## PM-009: Referrals, advice, questions, communication

Priority: P1 | Depends on: PM-005 | Status: Done

Referral, milestones, ConsultationAdvice, ClinicalQuestion, CommunicationEvent —
data contract §8; workflows §6.

## PM-010: CareTask, reminders, response write-back

Priority: P0 | Depends on: PM-004, PM-009 | Status: Done

CareTask, TaskAssignment, TaskResponse, ReminderSchedule, NotificationAttempt,
SchedulingOutbox — data contract §9.

## PM-011: Census, workspace, inbox UI

Priority: P0 | Depends on: PM-006, PM-010 | Status: Done

Implement Ankita workflow screens (Patients / Work / Inbox / History, patient
workspace, composer) against care queries — workflows §5–§6.

## PM-012: Single write-path for forms, MCP, AI

Priority: P0 | Depends on: PM-005 through PM-010 | Status: Done

All input methods call the same command/validation layer; no weaker chat schema.

## PM-013: Model and workflow acceptance

Priority: P0 | Depends on: PM-011, PM-012 | Status: Todo

Run information-model §8 scenarios end-to-end plus workflow success criteria.
