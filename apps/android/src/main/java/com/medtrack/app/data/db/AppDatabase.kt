package com.medtrack.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.medtrack.app.data.care.dao.CareDao
import com.medtrack.app.data.care.dao.CareTherapyDao
import com.medtrack.app.data.care.dao.CareWorkDao
import com.medtrack.app.data.care.entity.*
import com.medtrack.app.data.db.converters.ClinicalTimeConverters
import com.medtrack.app.data.db.converters.RegimenConverters
import com.medtrack.app.hybrid.data.HybridDao
import com.medtrack.app.hybrid.data.HybridInferenceRequestEntity
import com.medtrack.app.hybrid.data.HybridProposalEntity

/**
 * Care-model database. Legacy patients/visits tables were dropped in v12.
 */
@Database(
    entities = [
        CareAccountEntity::class,
        CarePersonEntity::class,
        CarePatientEntity::class,
        CareProfessionalRoleEntity::class,
        CarePersonContactEntity::class,
        CareHospitalEntity::class,
        CareDepartmentEntity::class,
        CareClinicalUnitEntity::class,
        CareClinicalTeamEntity::class,
        CareTeamMembershipEntity::class,
        CarePhysicalLocationEntity::class,
        CareStationCoverageEntity::class,
        CareDepartmentLocationUseEntity::class,
        CareReferenceConceptEntity::class,
        CareConceptAliasEntity::class,
        CarePatientIdentifierEntity::class,
        CareHospitalEpisodeEntity::class,
        CareEpisodeLinkEntity::class,
        CareInvolvementEntity::class,
        CareUnassignedIntakeEntity::class,
        CareLocationAssignmentEntity::class,
        CareDepartmentAssignmentEntity::class,
        CareAdmissionTeamAssignmentEntity::class,
        CareNursingAssignmentEntity::class,
        CareClinicalEventEntity::class,
        CareEventParticipantEntity::class,
        CareRecordRevisionEntity::class,
        CareAuditEntryEntity::class,
        CareAppliedOperationEntity::class,
        CareLegacyLinkEntity::class,
        CareEncounterEntity::class,
        CareClinicalNoteEntity::class,
        CareProblemEntity::class,
        CareAllergyEntity::class,
        CareAllergyAssessmentEntity::class,
        CareObservationEntity::class,
        CareClinicalDecisionEntity::class,
        CareDecisionProblemLinkEntity::class,
        CarePlanRevisionEntity::class,
        CareMedicationDefinitionEntity::class,
        CareMedicationHistoryItemEntity::class,
        CareMedicationOrderEntity::class,
        CareMedicationOrderEventEntity::class,
        CareMedicationAdministrationEntity::class,
        CareProcedureOrderEntity::class,
        CareProcedureEventEntity::class,
        CareInvestigationOrderEntity::class,
        CareSpecimenEntity::class,
        CareOrderSpecimenLinkEntity::class,
        CareImagingStudyEntity::class,
        CareDocumentEntity::class,
        CareDiagnosticReportEntity::class,
        CareReportOrderLinkEntity::class,
        CareReportDocumentLinkEntity::class,
        CareReportReviewEntity::class,
        CareReferralEntity::class,
        CareReferralMilestoneEntity::class,
        CareConsultationAdviceEntity::class,
        CareAdviceDecisionLinkEntity::class,
        CareClinicalQuestionEntity::class,
        CareQuestionResponseEntity::class,
        CareCommunicationEventEntity::class,
        CareCommunicationRecipientEntity::class,
        CareCommunicationSubjectEntity::class,
        CareTaskEntity::class,
        CareTaskAssignmentEntity::class,
        CareTaskClinicalLinkEntity::class,
        CareTaskResponseEntity::class,
        CareReminderScheduleEntity::class,
        CareNotificationAttemptEntity::class,
        CareSchedulingOutboxEntity::class,
        HybridInferenceRequestEntity::class,
        HybridProposalEntity::class
    ],
    version = 13,
    exportSchema = true
)
@TypeConverters(ClinicalTimeConverters::class, RegimenConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun careDao(): CareDao
    abstract fun careTherapyDao(): CareTherapyDao
    abstract fun careWorkDao(): CareWorkDao
    abstract fun hybridDao(): HybridDao
}
