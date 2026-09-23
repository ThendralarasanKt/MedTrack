# MedTrack implementation tasks

The first task set is [01-inpatient-foundation.md](01-inpatient-foundation.md), derived from the [high-level specification](../spec/high-level-spec.md).

**Active delivery spine (data-model first):** [04-patient-information-model.md](04-patient-information-model.md)
and the checklist [PM-backlog.md](PM-backlog.md). Specs:
[patient information model](../spec/patient-information-model.md),
[data contract](../spec/patient-management-data-contract.md),
[workflows](../spec/patient-management-workflows.md). Interview notes live under `spec/Business User Convo/`.

The [chat, LLM, clinical-record and notification workflow task set](02-chat-clinical-workflows.md) and [Jev through OpenRouter set](03-jev-openrouter-integration.md) remain valid for AI/orchestration work after the care model exists. Where they overlap admissions/locations/events/census, implement once under **PM-*** and link evidence.

The [hybrid architecture implementation tasks](05-hybrid-architecture-implementation.md) define HY-01 through HY-14: Ankita's single-device first release, account-isolated cloud AI, local clinical commits, recovery and pilot verification. They coordinate existing WF/JV/MT work without duplicating the completed PM model. Shared records and clinical synchronization remain deferred.

The [clarification card and receipt tasks](06-clarification-cards-and-receipts.md) bind typed assistant turns to trusted Android actions, local commands, and receipts. CL-01–CL-16 are Done, including the conversation timeline on the Capture route.

Its goal is a usable single-doctor inpatient organizer: identify a patient, record care, track location and treatment changes, see pending work, and receive reminders. Manual workflows must work offline. Advanced AI work follows this foundation.

All new tasks start as **Todo**. Task creation does not mean implementation or validation has been completed.

## Execution order

**Next architecture delivery task:** [HY-14](05-hybrid-architecture-implementation.md#hy-14-run-the-controlled-ankita-pilot) is Blocked pending authorized deployment. HY-01–HY-13 are Done; evidence [HY-02-14-delivery.md](HY-02-14-delivery.md). PM-001–PM-013 remain Done.

### Patient information model (PM)

| ID | Task | Depends on | Status |
| --- | --- | --- | --- |
| PM-001 | Shared contracts, schema export, migration baseline | MT-001 | Done |
| PM-002 | Master data (people, org, space, catalogues) | PM-001 | Done |
| PM-003 | Identity, episodes, involvement, intake stub | PM-002 | Done |
| PM-004 | Independent dated assignments + RecordTransfer | PM-003 | Done |
| PM-005 | Provenance core (events, revisions, audit, operations) | PM-003 | Done |
| PM-006 | Legacy bridge (patients/visits → care model) | PM-004, PM-005 | Done |
| PM-007 | Clinical assessment objects | PM-005 | Done |
| PM-008 | Medication, procedure, investigation objects | PM-007 | Done |
| PM-009 | Referrals, advice, questions, communication | PM-005 | Done |
| PM-010 | CareTask, reminders, response write-back | PM-004, PM-009 | Done |
| PM-011 | Census, workspace, inbox UI | PM-006, PM-010 | Done |
| PM-012 | Single write-path for forms, MCP, AI | PM-005–PM-010 | Done |
| PM-013 | Model and workflow acceptance | PM-011, PM-012 | Done |

### Legacy foundation index (refined by PM-*)

| ID | Task | Depends on | Status |
| --- | --- | --- | --- |
| MT-001 | Establish a reproducible build and synthetic test baseline | None | Done |
| MT-001A | Finish lint and device verification | MT-001 | Done |
| MT-002 | Prevent automatic record loss / migrations | MT-001 | Refined by PM-001 |
| MT-003 | Introduce admissions and stable record context | MT-002 | Refined by PM-003 |
| MT-004 | Add transactional events and audit history | MT-003 | Refined by PM-005 |
| MT-005 | Track structured locations and transfers | MT-004 | Refined by PM-004 |
| MT-006 | Record clinical reviews, problems, and plan revisions | MT-004 | Refined by PM-007 |
| MT-007 | Preserve medication and delivered-treatment history | MT-006 | Refined by PM-008 |
| MT-008 | Unify tasks and reliable review reminders | MT-005, MT-007 | Refined by PM-010 |
| MT-009 | Capture care-team advice, questions, and reports | MT-006, MT-008 | Refined by PM-009 |
| MT-010 | Complete the census, patient workspace, and work queue | MT-005–MT-009 | Refined by PM-011 |
| MT-011 | Align existing AI tools with the new record model | MT-010 | Refined by PM-012 |
| MT-012 | Require Google sign-in and encrypt local clinical storage | MT-011 | Done |
| MT-013 | Validate the first usable inpatient workflow | MT-012 | Refined by PM-013 |

Work PM-001 → PM-013 unless dependencies already completed. Keep each change buildable; update affected UI and tools with schema changes or explicitly disable incompatible operations until their replacement is available.

## Completion convention

A task is Done only when its acceptance criteria pass. Record the implementation commit or changed files, verification performed, and remaining limitations in that task's delivery notes. Use synthetic patient data for development and verification.

Until a live Ankita onboarding is authorized (HY-14), only synthetic patient data may be used. This backlog does not authorize a production release.
