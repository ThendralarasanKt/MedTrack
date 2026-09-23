# PM-010: CareTask, reminders, response write-back

Status: Done | Priority: P0 | Depends on: PM-004, PM-009  
Parent: [04-patient-information-model.md](04-patient-information-model.md)

## Outcome

CareTask, TaskAssignment, TaskResponse, ReminderSchedule, NotificationAttempt,
SchedulingOutbox (data contract §9).

## Acceptance

- [x] AssignTask with optional reminder + PENDING scheduling outbox
- [x] RespondToTask COMPLETE requires note when COMPLETE_WITH_NOTE/CANCEL
- [x] Reschedule invalidates prior reminder revision and enqueues cancel/schedule

## Delivery notes

Schema 11 + `CareWorkCommandService` task/reminder APIs.
Covered by `CareWorkAndAcceptanceTest`.
