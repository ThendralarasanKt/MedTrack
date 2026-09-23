# Care model Room table map (PM-001–PM-013)

Logical objects from [patient-management-data-contract.md](../spec/patient-management-data-contract.md)
map to parallel `care_*` tables. Legacy Int-keyed tables remain until cutover.

| Logical object | Room table |
| --- | --- |
| Account | care_accounts |
| Person | care_persons |
| Patient | care_patients |
| ProfessionalRole | care_professional_roles |
| PersonContact | care_person_contacts |
| Hospital | care_hospitals |
| Department | care_departments |
| ClinicalUnit | care_clinical_units |
| ClinicalTeam | care_clinical_teams |
| TeamMembership | care_team_memberships |
| PhysicalLocation | care_physical_locations |
| StationCoverage | care_station_coverage |
| DepartmentLocationUse | care_department_location_use |
| ReferenceConcept | care_reference_concepts |
| ConceptAlias | care_concept_aliases |
| PatientIdentifier | care_patient_identifiers |
| HospitalEpisode | care_hospital_episodes |
| EpisodeLink | care_episode_links |
| CareInvolvement | care_involvements |
| UnassignedIntake | care_unassigned_intakes |
| LocationAssignment | care_location_assignments |
| DepartmentAssignment | care_department_assignments |
| AdmissionTeamAssignment | care_admission_team_assignments |
| NursingAssignment | care_nursing_assignments |
| ClinicalEvent | care_clinical_events |
| EventParticipant | care_event_participants |
| RecordRevision | care_record_revisions |
| AuditEntry | care_audit_entries |
| AppliedOperation | care_applied_operations |
| LegacyLink | care_legacy_links |
| Encounter | care_encounters |
| ClinicalNote | care_clinical_notes |
| Problem | care_problems |
| Allergy | care_allergies |
| AllergyAssessment | care_allergy_assessments |
| Observation | care_observations |
| ClinicalDecision | care_clinical_decisions |
| DecisionProblemLink | care_decision_problem_links |
| CarePlanRevision | care_plan_revisions |
| MedicationDefinition | care_medication_definitions |
| MedicationHistoryItem | care_medication_history_items |
| MedicationOrder | care_medication_orders |
| MedicationOrderEvent | care_medication_order_events |
| MedicationAdministration | care_medication_administrations |
| ProcedureOrder | care_procedure_orders |
| ProcedureEvent | care_procedure_events |
| InvestigationOrder | care_investigation_orders |
| Specimen | care_specimens |
| OrderSpecimenLink | care_order_specimen_links |
| ImagingStudy | care_imaging_studies |
| Document | care_documents |
| DiagnosticReport | care_diagnostic_reports |
| ReportOrderLink | care_report_order_links |
| ReportDocumentLink | care_report_document_links |
| ReportReview | care_report_reviews |
| Referral | care_referrals |
| ReferralMilestone | care_referral_milestones |
| ConsultationAdvice | care_consultation_advice |
| AdviceDecisionLink | care_advice_decision_links |
| ClinicalQuestion | care_clinical_questions |
| QuestionResponse | care_question_responses |
| CommunicationEvent | care_communication_events |
| CommunicationRecipient | care_communication_recipients |
| CommunicationSubject | care_communication_subjects |
| CareTask | care_tasks |
| TaskAssignment | care_task_assignments |
| TaskClinicalLink | care_task_clinical_links |
| TaskResponse | care_task_responses |
| ReminderSchedule | care_reminder_schedules |
| NotificationAttempt | care_notification_attempts |
| SchedulingOutbox | care_scheduling_outbox |

Shared: UUID string `id`, `ownerAccountId`, `createdAt`, `createdBy`, `version`.
`ClinicalTime` / `Regimen` stored as JSON via converters.
Database version **11**. Migrations: 5→6 masters/episodes/assignments; 6→7
provenance; 7→8 legacy links; 8→9 assessment; 9→10 therapy/labs; 10→11
referrals + tasks/reminders.
