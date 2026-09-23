package com.medtrack.app.data.care.model

object CareEnums {
    enum class IdentityState { PROVISIONAL, REPORTED, CONFIRMED }
    enum class ActiveStatus { ACTIVE, INACTIVE }
    enum class LocationKind { BUILDING, FLOOR, WARD, NURSING_STATION, ROOM, BED }
    enum class StructureState { COMPLETE, INCOMPLETE }
    enum class EpisodeKind { ER, INPATIENT }
    enum class EpisodeStatus { ACTIVE, DISCHARGED, CANCELLED }
    enum class IdentifierStatus { CURRENT, PREVIOUS, DISPUTED }
    enum class DepartmentRole { PRIMARY, CONSULTING }
    enum class TeamAssignmentRole { PRIMARY, CONSULTING }
    enum class InvolvementRole { PRIMARY_TEAM, REFERRAL, ON_CALL }
    enum class InvolvementStatus { ACTIVE, ENDED }
    enum class NursingRole { BEDSIDE, IN_CHARGE }
    enum class Verification { REPORTED, VERIFIED }
    enum class ContactKind { PHONE, EMAIL, OTHER }
    enum class IntakeStatus { UNRESOLVED, RESOLVED, DISMISSED }
    enum class AgeUnit { YEARS, MONTHS, DAYS }
    enum class LegacyMappingStatus { PROJECTED, DEFERRED }
    enum class LegacyTable {
        PATIENTS,
        VISITS,
        MEDICINES,
        TASKS,
        REPORTS,
        FOLLOW_UPS;

        val tableName: String
            get() = when (this) {
                PATIENTS -> "patients"
                VISITS -> "visits"
                MEDICINES -> "medicines"
                TASKS -> "tasks"
                REPORTS -> "reports"
                FOLLOW_UPS -> "follow_ups"
            }
    }

    enum class LegacyCareObjectType {
        PERSON,
        PATIENT,
        HOSPITAL_EPISODE,
        VISIT_SOURCE,
        MEDICINE_SOURCE,
        TASK_SOURCE,
        REPORT_SOURCE,
        FOLLOW_UP_SOURCE
    }

    enum class EncounterKind { ROUND, REFERRAL_REVIEW, ON_CALL_REVIEW, OTHER }
    enum class NoteKind { PROGRESS, HISTORY, HANDOVER_NOTE, OTHER }
    enum class ProblemCertainty { SUSPECTED, CONFIRMED }
    enum class ProblemStatus { ACTIVE, RESOLVED }
    enum class AllergyStatus { ACTIVE, INACTIVE }
    enum class AllergyAssessmentResult { UNKNOWN, NONE_KNOWN, KNOWN_ALLERGIES }
    enum class DecisionStatus { ACTIVE, SUPERSEDED, RETRACTED }
    enum class ObservationValueKind { NUMBER, TEXT, BOOLEAN }

    enum class MedicationUseStatus { REPORTED_CURRENT, PAST, UNKNOWN }
    enum class MedicationOrderStatus { ACTIVE, HELD, STOPPED, COMPLETED }
    enum class MedicationOrderAction { START, CHANGE, HOLD, RESUME, STOP, COMPLETE }
    enum class AdministrationOutcome { GIVEN, OMITTED, REFUSED }
    enum class ProcedureOrderStatus { REQUESTED, SCHEDULED, CANCELLED, COMPLETED }
    enum class ProcedureOutcome { PERFORMED, ABORTED }
    enum class InvestigationKind { LAB, IMAGING, OTHER }
    enum class InvestigationOrderStatus { REQUESTED, IN_PROGRESS, CANCELLED, RESULT_AVAILABLE }
    enum class DiagnosticReportKind { LAB, WRITTEN_IMAGING, OTHER }
    enum class DiagnosticReportStatus { PRELIMINARY, FINAL, AMENDED }
    enum class ReportReviewOutcome { NO_CHANGE, DECISION_RECORDED, FOLLOW_UP_REQUIRED }

    enum class ReferralDirection { INCOMING, OUTGOING }
    enum class ReferralPriority { ROUTINE, URGENT, UNSPECIFIED }
    enum class ReferralStatus { OPEN, CLOSED, CANCELLED }
    enum class ReferralMilestoneKind { ACCEPTED, PATIENT_SEEN, ADVICE_RECEIVED, CLOSED, CANCELLED }
    enum class AdviceDisposition { UNREVIEWED, ACCEPTED, REJECTED, DEFERRED }
    enum class QuestionStatus { OPEN, ANSWERED, CLOSED }
    enum class CommunicationChannel { VERBAL, CALL, MESSAGE, OTHER }
    enum class CommunicationState { CONVEYED, ACKNOWLEDGED }

    enum class CareTaskKind { REVIEW, CONSULT, INVESTIGATION, RESULT_REVIEW, COMMUNICATION, TREATMENT, OTHER }
    enum class CareTaskStatus { PENDING, IN_PROGRESS, COMPLETED, CANCELLED }
    enum class CareTaskPriority { ROUTINE, URGENT, UNSPECIFIED }
    enum class TaskResponseAction { NOTE, COMPLETE, COMPLETE_WITH_NOTE, RESCHEDULE, CANCEL, REOPEN }
    enum class ReminderState { ACTIVE, FIRED, CANCELLED }
    enum class ReminderPrecision { EXACT, INEXACT, UNAVAILABLE }
    enum class NotificationAttemptStatus { ATTEMPTED, ACCEPTED_BY_OS, BLOCKED, FAILED }
    enum class SchedulingOutboxAction { SCHEDULE, CANCEL }
    enum class SchedulingOutboxState { PENDING, APPLIED, FAILED }
}
