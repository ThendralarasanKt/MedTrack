# PM-003: Identity, episodes, involvement, intake stub

Status: Done | Priority: P0 | Depends on: PM-002  
Parent: [04-patient-information-model.md](04-patient-information-model.md)

## Outcome

PatientIdentifier, HospitalEpisode, EpisodeLink, CareInvolvement,
UnassignedIntake; `CreatePatientAndAdmission` command.

## Acceptance

- [x] Primary + consulting involvement without duplicate patient/episode
- [x] Ending involvement does not discharge
- [x] UnassignedIntake without fabricating Patient/Episode
