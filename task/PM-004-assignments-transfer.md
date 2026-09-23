# PM-004: Independent dated assignments + RecordTransfer

Status: Done | Priority: P0 | Depends on: PM-003  
Parent: [04-patient-information-model.md](04-patient-information-model.md)

## Outcome

LocationAssignment, DepartmentAssignment, AdmissionTeamAssignment,
NursingAssignment; atomic `RecordTransfer` for independent bed / department /
team / nurse changes.

## Acceptance

- [x] Location change without consultant change (and reverse)
- [x] Nurse shift without department change
- [x] Model §8 synthetic tests green
