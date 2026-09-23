package com.medtrack.app.data.care.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.medtrack.app.data.care.entity.*

@Dao
interface CareDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccount(row: CareAccountEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPerson(row: CarePersonEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPersonContact(row: CarePersonContactEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPatient(row: CarePatientEntity)

    @Update
    suspend fun updatePatient(row: CarePatientEntity)

    @Update
    suspend fun updateUnassignedIntake(row: CareUnassignedIntakeEntity)

    @Update
    suspend fun updatePerson(row: CarePersonEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProfessionalRole(row: CareProfessionalRoleEntity)

    @Update
    suspend fun updateProfessionalRole(row: CareProfessionalRoleEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReferenceConcept(row: CareReferenceConceptEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHospital(row: CareHospitalEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDepartment(row: CareDepartmentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertClinicalTeam(row: CareClinicalTeamEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPhysicalLocation(row: CarePhysicalLocationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEpisode(row: CareHospitalEpisodeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInvolvement(row: CareInvolvementEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUnassignedIntake(row: CareUnassignedIntakeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPatientIdentifier(row: CarePatientIdentifierEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLocationAssignment(row: CareLocationAssignmentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDepartmentAssignment(row: CareDepartmentAssignmentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTeamAssignment(row: CareAdmissionTeamAssignmentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNursingAssignment(row: CareNursingAssignmentEntity)

    @Update
    suspend fun updateLocationAssignment(row: CareLocationAssignmentEntity)

    @Update
    suspend fun updateDepartmentAssignment(row: CareDepartmentAssignmentEntity)

    @Update
    suspend fun updateTeamAssignment(row: CareAdmissionTeamAssignmentEntity)

    @Update
    suspend fun updateNursingAssignment(row: CareNursingAssignmentEntity)

    @Update
    suspend fun updateInvolvement(row: CareInvolvementEntity)

    @Update
    suspend fun updateEpisode(row: CareHospitalEpisodeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLegacyLink(row: CareLegacyLinkEntity)

    @Query("SELECT * FROM care_persons WHERE id = :id LIMIT 1")
    suspend fun getPerson(id: String): CarePersonEntity?

    @Query("SELECT * FROM care_patients WHERE id = :id LIMIT 1")
    suspend fun getPatient(id: String): CarePatientEntity?

    @Query("SELECT * FROM care_hospital_episodes WHERE id = :id LIMIT 1")
    suspend fun getEpisode(id: String): CareHospitalEpisodeEntity?

    @Query(
        """
        SELECT * FROM care_hospital_episodes
        WHERE patientId = :patientId AND kind = 'INPATIENT' AND status = 'ACTIVE'
        LIMIT 1
        """
    )
    suspend fun getActiveInpatientEpisode(patientId: String): CareHospitalEpisodeEntity?

    @Query(
        """
        SELECT * FROM care_physical_locations
        WHERE hospitalId = :hospitalId AND kind = :kind AND code = :code
          AND ((:parentId IS NULL AND parentLocationId IS NULL) OR parentLocationId = :parentId)
        LIMIT 1
        """
    )
    suspend fun findLocation(
        hospitalId: String,
        parentId: String?,
        kind: String,
        code: String
    ): CarePhysicalLocationEntity?

    @Query(
        """
        SELECT * FROM care_location_assignments
        WHERE admissionId = :admissionId AND endsAtJson IS NULL
        LIMIT 1
        """
    )
    suspend fun currentLocationAssignment(admissionId: String): CareLocationAssignmentEntity?

    @Query(
        """
        SELECT * FROM care_department_assignments
        WHERE admissionId = :admissionId AND role = 'PRIMARY' AND endsAtJson IS NULL
        LIMIT 1
        """
    )
    suspend fun currentPrimaryDepartment(admissionId: String): CareDepartmentAssignmentEntity?

    @Query(
        """
        SELECT * FROM care_admission_team_assignments
        WHERE admissionId = :admissionId AND role = 'PRIMARY' AND endsAtJson IS NULL
        LIMIT 1
        """
    )
    suspend fun currentPrimaryTeam(admissionId: String): CareAdmissionTeamAssignmentEntity?

    @Query(
        """
        SELECT * FROM care_nursing_assignments
        WHERE admissionId = :admissionId AND endsAtJson IS NULL
        """
    )
    suspend fun currentNursingAssignments(admissionId: String): List<CareNursingAssignmentEntity>

    @Query(
        """
        SELECT la.* FROM care_location_assignments la
        INNER JOIN care_physical_locations loc ON loc.id = la.locationId
        WHERE la.ownerAccountId = :ownerAccountId
          AND la.endsAtJson IS NULL
          AND la.verification = 'VERIFIED'
          AND loc.kind = 'BED'
          AND la.locationId = :bedLocationId
          AND la.admissionId != :exceptAdmissionId
        LIMIT 1
        """
    )
    suspend fun conflictingVerifiedBedOccupant(
        ownerAccountId: String,
        bedLocationId: String,
        exceptAdmissionId: String
    ): CareLocationAssignmentEntity?

    @Query("SELECT * FROM care_involvements WHERE admissionId = :admissionId")
    suspend fun involvementsForAdmission(admissionId: String): List<CareInvolvementEntity>

    @Query("SELECT * FROM care_unassigned_intakes WHERE id = :id LIMIT 1")
    suspend fun getIntake(id: String): CareUnassignedIntakeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertClinicalEvent(row: CareClinicalEventEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEventParticipant(row: CareEventParticipantEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecordRevision(row: CareRecordRevisionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuditEntry(row: CareAuditEntryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAppliedOperation(row: CareAppliedOperationEntity)

    @Query(
        """
        SELECT * FROM care_applied_operations
        WHERE ownerAccountId = :ownerAccountId AND operationId = :operationId
        LIMIT 1
        """
    )
    suspend fun getAppliedOperation(ownerAccountId: String, operationId: String): CareAppliedOperationEntity?

    @Query("SELECT * FROM care_clinical_events WHERE operationId = :operationId")
    suspend fun eventsForOperation(operationId: String): List<CareClinicalEventEntity>

    @Query("SELECT * FROM care_audit_entries WHERE operationId = :operationId")
    suspend fun auditForOperation(operationId: String): List<CareAuditEntryEntity>

    @Query(
        """
        SELECT * FROM care_hospitals
        WHERE ownerAccountId = :ownerAccountId AND code = :code
        LIMIT 1
        """
    )
    suspend fun findHospitalByCode(ownerAccountId: String, code: String): CareHospitalEntity?

    @Query(
        """
        SELECT * FROM care_legacy_links
        WHERE ownerAccountId = :ownerAccountId
          AND legacyTable = :legacyTable
          AND legacyId = :legacyId
          AND careObjectType = :careObjectType
        LIMIT 1
        """
    )
    suspend fun findLegacyLink(
        ownerAccountId: String,
        legacyTable: String,
        legacyId: Int,
        careObjectType: String
    ): CareLegacyLinkEntity?

    @Query(
        """
        SELECT * FROM care_legacy_links
        WHERE ownerAccountId = :ownerAccountId
        ORDER BY legacyTable ASC, legacyId ASC, careObjectType ASC
        """
    )
    suspend fun legacyLinksForOwner(ownerAccountId: String): List<CareLegacyLinkEntity>

    @Query(
        """
        SELECT * FROM care_hospital_episodes
        WHERE ownerAccountId = :ownerAccountId AND externalId = :externalId
        LIMIT 1
        """
    )
    suspend fun findEpisodeByExternalId(
        ownerAccountId: String,
        externalId: String
    ): CareHospitalEpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEncounter(row: CareEncounterEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertClinicalNote(row: CareClinicalNoteEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProblem(row: CareProblemEntity)

    @Update
    suspend fun updateProblem(row: CareProblemEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllergy(row: CareAllergyEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllergyAssessment(row: CareAllergyAssessmentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertObservation(row: CareObservationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertClinicalDecision(row: CareClinicalDecisionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDecisionProblemLink(row: CareDecisionProblemLinkEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCarePlanRevision(row: CarePlanRevisionEntity)

    @Query("SELECT * FROM care_encounters WHERE id = :id LIMIT 1")
    suspend fun getEncounter(id: String): CareEncounterEntity?

    @Query("SELECT * FROM care_encounters WHERE admissionId = :admissionId ORDER BY createdAt ASC")
    suspend fun encountersForAdmission(admissionId: String): List<CareEncounterEntity>

    @Query("SELECT * FROM care_clinical_notes WHERE encounterId = :encounterId")
    suspend fun notesForEncounter(encounterId: String): List<CareClinicalNoteEntity>

    @Query("SELECT * FROM care_problems WHERE id = :id LIMIT 1")
    suspend fun getProblem(id: String): CareProblemEntity?

    @Query(
        """
        SELECT * FROM care_problems
        WHERE ownerAccountId = :ownerAccountId AND patientId = :patientId
        ORDER BY createdAt ASC
        """
    )
    suspend fun problemsForPatient(ownerAccountId: String, patientId: String): List<CareProblemEntity>

    @Query(
        """
        SELECT * FROM care_allergies
        WHERE ownerAccountId = :ownerAccountId AND patientId = :patientId
        ORDER BY createdAt ASC
        """
    )
    suspend fun allergiesForPatient(ownerAccountId: String, patientId: String): List<CareAllergyEntity>

    @Query(
        """
        SELECT * FROM care_allergy_assessments
        WHERE ownerAccountId = :ownerAccountId AND patientId = :patientId
        ORDER BY createdAt ASC
        """
    )
    suspend fun allergyAssessmentsForPatient(
        ownerAccountId: String,
        patientId: String
    ): List<CareAllergyAssessmentEntity>

    @Query("SELECT * FROM care_observations WHERE id = :id LIMIT 1")
    suspend fun getObservation(id: String): CareObservationEntity?

    @Query(
        """
        SELECT * FROM care_observations
        WHERE patientId = :patientId
        ORDER BY createdAt DESC
        """
    )
    suspend fun observationsForPatient(patientId: String): List<CareObservationEntity>

    @Query("SELECT * FROM care_clinical_decisions WHERE id = :id LIMIT 1")
    suspend fun getClinicalDecision(id: String): CareClinicalDecisionEntity?

    @Query("SELECT * FROM care_decision_problem_links WHERE decisionId = :decisionId")
    suspend fun linksForDecision(decisionId: String): List<CareDecisionProblemLinkEntity>

    @Query(
        """
        SELECT * FROM care_plan_revisions
        WHERE admissionId = :admissionId
        ORDER BY createdAt ASC
        """
    )
    suspend fun planRevisionsForAdmission(admissionId: String): List<CarePlanRevisionEntity>

    @Query(
        """
        SELECT * FROM care_event_participants
        WHERE eventId = :eventId
        ORDER BY role ASC
        """
    )
    suspend fun participantsForEvent(eventId: String): List<CareEventParticipantEntity>

    @Query(
        """
        SELECT * FROM care_record_revisions
        WHERE recordType = :recordType AND recordId = :recordId
        ORDER BY recordVersion ASC
        """
    )
    suspend fun revisionsForRecord(
        recordType: String,
        recordId: String
    ): List<CareRecordRevisionEntity>

    @Query("SELECT * FROM care_physical_locations WHERE id = :id LIMIT 1")
    suspend fun getLocation(id: String): CarePhysicalLocationEntity?

    @Query(
        """
        SELECT * FROM care_physical_locations
        WHERE ownerAccountId = :ownerAccountId AND hospitalId = :hospitalId
        ORDER BY kind ASC, code ASC
        """
    )
    suspend fun locationsForHospital(
        ownerAccountId: String,
        hospitalId: String
    ): List<CarePhysicalLocationEntity>

    @Query(
        """
        SELECT * FROM care_hospital_episodes
        WHERE ownerAccountId = :ownerAccountId AND status = :status
        ORDER BY createdAt ASC
        """
    )
    suspend fun episodesByStatus(
        ownerAccountId: String,
        status: String
    ): List<CareHospitalEpisodeEntity>

    @Query(
        """
        SELECT * FROM care_unassigned_intakes
        WHERE ownerAccountId = :ownerAccountId AND status = 'UNRESOLVED'
        ORDER BY createdAt DESC
        """
    )
    suspend fun unresolvedIntakes(ownerAccountId: String): List<CareUnassignedIntakeEntity>

    @Query(
        """
        SELECT * FROM care_persons
        WHERE ownerAccountId = :ownerAccountId
            AND displayName LIKE '%' || :query || '%'
        ORDER BY displayName ASC
        """
    )
    suspend fun searchPersons(ownerAccountId: String, query: String): List<CarePersonEntity>

    @Query("SELECT * FROM care_accounts WHERE ownerAccountId = :ownerAccountId LIMIT 1")
    suspend fun accountForOwner(ownerAccountId: String): CareAccountEntity?

    @Query("SELECT * FROM care_professional_roles WHERE personId = :personId ORDER BY createdAt DESC")
    suspend fun rolesForPerson(personId: String): List<CareProfessionalRoleEntity>

    @Query(
        """
        SELECT * FROM care_reference_concepts
        WHERE ownerAccountId = :ownerAccountId AND domain = :domain AND code = :code
        LIMIT 1
        """
    )
    suspend fun findReferenceConcept(
        ownerAccountId: String,
        domain: String,
        code: String
    ): CareReferenceConceptEntity?

    @Query("SELECT * FROM care_event_participants WHERE personId = :personId")
    suspend fun participantsForPerson(personId: String): List<CareEventParticipantEntity>

    @Query("SELECT * FROM care_hospital_episodes WHERE ownerAccountId = :ownerAccountId")
    suspend fun episodesForOwner(ownerAccountId: String): List<CareHospitalEpisodeEntity>

    @Query("SELECT * FROM care_reminder_schedules WHERE ownerAccountId = :ownerAccountId")
    suspend fun remindersForOwner(ownerAccountId: String): List<CareReminderScheduleEntity>

    @Query(
        """
        SELECT * FROM care_clinical_events
        WHERE admissionId = :admissionId
        ORDER BY createdAt DESC
        """
    )
    suspend fun eventsForAdmission(admissionId: String): List<CareClinicalEventEntity>
}
