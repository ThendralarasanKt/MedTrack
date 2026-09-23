package com.medtrack.app.data.care.command

import androidx.room.withTransaction
import com.medtrack.app.data.care.dao.CareDao
import com.medtrack.app.data.care.entity.*
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.CareIds
import com.medtrack.app.data.care.model.ClinicalTime
import com.medtrack.app.data.db.AppDatabase
import com.medtrack.app.data.db.converters.ClinicalTimeConverters
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

data class CareWorkspace(
    val ownerAccountId: String,
    val actorPersonId: String
)

data class CreatePatientAndAdmissionRequest(
    val patientDisplayName: String,
    val hospitalId: String,
    val episodeKind: CareEnums.EpisodeKind = CareEnums.EpisodeKind.INPATIENT,
    val startedAt: ClinicalTime = ClinicalTime.unknown("admission start unknown"),
    val involvementRole: CareEnums.InvolvementRole = CareEnums.InvolvementRole.PRIMARY_TEAM,
    val involverPersonId: String? = null,
    val teamId: String? = null,
    val reportedAge: String? = null,
    val sexConceptId: String? = null,
    val operationId: String = CareIds.newId()
)

data class CreatePatientAndAdmissionResult(
    val personId: String,
    val patientId: String,
    val admissionId: String,
    val involvementId: String,
    val eventId: String? = null,
    val receipt: OperationReceipt? = null
)

data class RecordTransferRequest(
    val admissionId: String,
    val effectiveAt: ClinicalTime,
    val locationId: String? = null,
    val stationId: String? = null,
    val locationVerification: CareEnums.Verification = CareEnums.Verification.VERIFIED,
    val primaryDepartmentId: String? = null,
    val primaryTeamId: String? = null,
    val consultantPersonId: String? = null,
    val nursingPersonId: String? = null,
    val nursingProfessionalRoleId: String? = null,
    val nursingStationId: String? = null,
    val nursingRole: CareEnums.NursingRole = CareEnums.NursingRole.BEDSIDE,
    val expectedLocationAssignmentVersion: Int? = null,
    val operationId: String = CareIds.newId()
)

data class RecordTransferResult(
    val admissionId: String,
    val eventId: String?,
    val receipt: OperationReceipt
)

@Singleton
class CareCommandService @Inject constructor(
    private val database: AppDatabase,
    private val careDao: CareDao
) {
    private val clinicalTimeConverters = ClinicalTimeConverters()
    private val provenance = CareProvenanceWriter(careDao)

    suspend fun createPatientAndAdmission(
        workspace: CareWorkspace,
        request: CreatePatientAndAdmissionRequest
    ): CreatePatientAndAdmissionResult {
        val requestHash = CareProvenanceWriter.sha256(
            listOf(
                request.patientDisplayName,
                request.hospitalId,
                request.episodeKind.name,
                request.involvementRole.name,
                request.involverPersonId.orEmpty(),
                request.teamId.orEmpty(),
                request.reportedAge.orEmpty(),
                request.sexConceptId.orEmpty(),
                clinicalTimeConverters.fromClinicalTime(request.startedAt).orEmpty()
            ).joinToString("|")
        )

        return database.withTransaction {
            provenance.beginOrReuse(workspace, request.operationId, requestHash)?.let { existing ->
                val refs = JSONObject(existing.resultReferences)
                return@withTransaction CreatePatientAndAdmissionResult(
                    personId = refs.getString("personId"),
                    patientId = refs.getString("patientId"),
                    admissionId = refs.getString("admissionId"),
                    involvementId = refs.getString("involvementId"),
                    eventId = refs.nullableString("eventId"),
                    receipt = OperationReceipt(
                        operationId = request.operationId,
                        resultReferencesJson = existing.resultReferences,
                        reused = true
                    )
                )
            }

            val now = System.currentTimeMillis()
            val personId = CareIds.newId()
            val patientId = CareIds.newId()
            val admissionId = CareIds.newId()
            val involvementId = CareIds.newId()
            val involver = request.involverPersonId ?: workspace.actorPersonId

            careDao.insertPerson(
                CarePersonEntity(
                    id = personId,
                    ownerAccountId = workspace.ownerAccountId,
                    displayName = request.patientDisplayName,
                    identityState = CareEnums.IdentityState.REPORTED.name,
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )
            careDao.insertPatient(
                CarePatientEntity(
                    id = patientId,
                    ownerAccountId = workspace.ownerAccountId,
                    personId = personId,
                    reportedAge = request.reportedAge?.takeIf { it.isNotBlank() },
                    ageUnit = request.reportedAge?.takeIf { it.isNotBlank() }?.let {
                        CareEnums.AgeUnit.YEARS.name
                    },
                    sexConceptId = request.sexConceptId?.takeIf { it.isNotBlank() },
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )
            if (request.episodeKind == CareEnums.EpisodeKind.INPATIENT &&
                careDao.getActiveInpatientEpisode(patientId) != null
            ) {
                error("Active inpatient episode already exists")
            }
            careDao.insertEpisode(
                CareHospitalEpisodeEntity(
                    id = admissionId,
                    ownerAccountId = workspace.ownerAccountId,
                    patientId = patientId,
                    hospitalId = request.hospitalId,
                    kind = request.episodeKind.name,
                    status = CareEnums.EpisodeStatus.ACTIVE.name,
                    startedAt = request.startedAt,
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )
            careDao.insertInvolvement(
                CareInvolvementEntity(
                    id = involvementId,
                    ownerAccountId = workspace.ownerAccountId,
                    admissionId = admissionId,
                    personId = involver,
                    teamId = request.teamId,
                    role = request.involvementRole.name,
                    status = CareEnums.InvolvementStatus.ACTIVE.name,
                    startsAt = request.startedAt,
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )

            val eventId = provenance.writeEvent(
                workspace = workspace,
                patientId = patientId,
                admissionId = admissionId,
                eventType = "ADMISSION_CREATED",
                effectiveTime = request.startedAt,
                operationId = request.operationId,
                now = now
            )
            provenance.writeAudit(
                workspace = workspace,
                operationId = request.operationId,
                action = "CREATE",
                targetType = "HospitalEpisode",
                targetId = admissionId,
                priorVersion = null,
                newVersion = 1,
                now = now
            )
            provenance.writeRevision(
                workspace = workspace,
                recordType = "HospitalEpisode",
                recordId = admissionId,
                recordVersion = 1,
                payloadSnapshot = JSONObject()
                    .put("patientId", patientId)
                    .put("hospitalId", request.hospitalId)
                    .put("kind", request.episodeKind.name)
                    .toString(),
                eventId = eventId,
                now = now
            )

            val refs = JSONObject()
                .put("personId", personId)
                .put("patientId", patientId)
                .put("admissionId", admissionId)
                .put("involvementId", involvementId)
                .put("eventId", eventId)
                .toString()
            val receipt = provenance.commitOperation(
                workspace = workspace,
                operationId = request.operationId,
                requestHash = requestHash,
                resultReferencesJson = refs,
                now = now
            )
            CreatePatientAndAdmissionResult(
                personId = personId,
                patientId = patientId,
                admissionId = admissionId,
                involvementId = involvementId,
                eventId = eventId,
                receipt = receipt
            )
        }
    }

    suspend fun endInvolvement(
        workspace: CareWorkspace,
        admissionId: String,
        involvementId: String,
        endedAt: ClinicalTime
    ) {
        val involvement = careDao.involvementsForAdmission(admissionId)
            .firstOrNull { it.id == involvementId }
            ?: error("Involvement not found")
        require(involvement.ownerAccountId == workspace.ownerAccountId)
        careDao.updateInvolvement(
            involvement.copy(
                status = CareEnums.InvolvementStatus.ENDED.name,
                endsAt = endedAt,
                version = involvement.version + 1
            )
        )
    }

    suspend fun addInvolvement(
        workspace: CareWorkspace,
        admissionId: String,
        personId: String,
        role: CareEnums.InvolvementRole,
        startsAt: ClinicalTime,
        teamId: String? = null,
        reason: String? = null
    ): String {
        val episode = careDao.getEpisode(admissionId) ?: error("Admission not found")
        require(episode.ownerAccountId == workspace.ownerAccountId)
        val id = CareIds.newId()
        careDao.insertInvolvement(
            CareInvolvementEntity(
                id = id,
                ownerAccountId = workspace.ownerAccountId,
                admissionId = admissionId,
                personId = personId,
                teamId = teamId,
                role = role.name,
                reason = reason,
                status = CareEnums.InvolvementStatus.ACTIVE.name,
                startsAt = startsAt,
                createdAt = System.currentTimeMillis(),
                createdBy = workspace.actorPersonId
            )
        )
        return id
    }

    suspend fun createUnassignedIntake(
        workspace: CareWorkspace,
        summary: String,
        identityHintsJson: String
    ): String {
        val id = CareIds.newId()
        careDao.insertUnassignedIntake(
            CareUnassignedIntakeEntity(
                id = id,
                ownerAccountId = workspace.ownerAccountId,
                summary = summary,
                identityHintsJson = identityHintsJson,
                status = CareEnums.IntakeStatus.UNRESOLVED.name,
                createdAt = System.currentTimeMillis(),
                createdBy = workspace.actorPersonId
            )
        )
        return id
    }

    suspend fun resolveIntake(
        workspace: CareWorkspace,
        intakeId: String,
        admissionId: String,
        note: String? = null
    ) {
        val intake = careDao.getIntake(intakeId) ?: error("Intake not found")
        require(intake.ownerAccountId == workspace.ownerAccountId)
        require(intake.status == CareEnums.IntakeStatus.UNRESOLVED.name) {
            "Intake is already ${intake.status}"
        }
        val episode = careDao.getEpisode(admissionId) ?: error("Admission not found")
        require(episode.ownerAccountId == workspace.ownerAccountId)
        careDao.updateUnassignedIntake(
            intake.copy(
                status = CareEnums.IntakeStatus.RESOLVED.name,
                resolvedPatientId = episode.patientId,
                resolvedAdmissionId = admissionId,
                resolutionNote = note,
                version = intake.version + 1
            )
        )
    }

    suspend fun dismissIntake(
        workspace: CareWorkspace,
        intakeId: String,
        note: String? = null
    ) {
        val intake = careDao.getIntake(intakeId) ?: error("Intake not found")
        require(intake.ownerAccountId == workspace.ownerAccountId)
        require(intake.status == CareEnums.IntakeStatus.UNRESOLVED.name) {
            "Intake is already ${intake.status}"
        }
        careDao.updateUnassignedIntake(
            intake.copy(
                status = CareEnums.IntakeStatus.DISMISSED.name,
                resolutionNote = note,
                version = intake.version + 1
            )
        )
    }

    /**
     * Atomically apply any combination of location / department / team / nursing changes.
     * Omitted fields leave existing relationships unchanged.
     */
    suspend fun recordTransfer(
        workspace: CareWorkspace,
        request: RecordTransferRequest
    ): RecordTransferResult {
        val requestHash = CareProvenanceWriter.sha256(
            listOf(
                request.admissionId,
                clinicalTimeConverters.fromClinicalTime(request.effectiveAt).orEmpty(),
                request.locationId.orEmpty(),
                request.stationId.orEmpty(),
                request.locationVerification.name,
                request.primaryDepartmentId.orEmpty(),
                request.primaryTeamId.orEmpty(),
                request.consultantPersonId.orEmpty(),
                request.nursingPersonId.orEmpty(),
                request.nursingProfessionalRoleId.orEmpty(),
                request.nursingStationId.orEmpty(),
                request.nursingRole.name
            ).joinToString("|")
        )

        return database.withTransaction {
            provenance.beginOrReuse(workspace, request.operationId, requestHash)?.let { existing ->
                val refs = JSONObject(existing.resultReferences)
                return@withTransaction RecordTransferResult(
                    admissionId = refs.getString("admissionId"),
                    eventId = refs.nullableString("eventId"),
                    receipt = OperationReceipt(
                        operationId = request.operationId,
                        resultReferencesJson = existing.resultReferences,
                        reused = true
                    )
                )
            }

            val episode = careDao.getEpisode(request.admissionId)
                ?: error("Admission not found")
            require(episode.ownerAccountId == workspace.ownerAccountId) {
                "Cross-account admission link rejected"
            }
            require(episode.status == CareEnums.EpisodeStatus.ACTIVE.name) {
                "Cannot transfer a non-active episode"
            }
            request.expectedLocationAssignmentVersion?.let { expected ->
                val current = careDao.currentLocationAssignment(request.admissionId)
                val actual = current?.version ?: 0
                if (actual != expected) {
                    throw StaleRecordException("LocationAssignment", current?.id ?: request.admissionId, expected, actual)
                }
            }

            val effectiveJson = clinicalTimeConverters.fromClinicalTime(request.effectiveAt)
            val now = System.currentTimeMillis()

            request.locationId?.let { locationId ->
                if (request.locationVerification == CareEnums.Verification.VERIFIED) {
                    val conflict = careDao.conflictingVerifiedBedOccupant(
                        ownerAccountId = workspace.ownerAccountId,
                        bedLocationId = locationId,
                        exceptAdmissionId = request.admissionId
                    )
                    if (conflict != null) {
                        error("Confirmed bed already occupied by another active assignment")
                    }
                }
                careDao.currentLocationAssignment(request.admissionId)?.let { current ->
                    careDao.updateLocationAssignment(
                        current.copy(
                            endsAt = request.effectiveAt,
                            endsAtJson = effectiveJson,
                            version = current.version + 1
                        )
                    )
                }
                careDao.insertLocationAssignment(
                    CareLocationAssignmentEntity(
                        id = CareIds.newId(),
                        ownerAccountId = workspace.ownerAccountId,
                        admissionId = request.admissionId,
                        locationId = locationId,
                        stationId = request.stationId,
                        startsAt = request.effectiveAt,
                        endsAt = null,
                        endsAtJson = null,
                        verification = request.locationVerification.name,
                        createdAt = now,
                        createdBy = workspace.actorPersonId
                    )
                )
            }

            request.primaryDepartmentId?.let { departmentId ->
                careDao.currentPrimaryDepartment(request.admissionId)?.let { current ->
                    careDao.updateDepartmentAssignment(
                        current.copy(
                            endsAt = request.effectiveAt,
                            endsAtJson = effectiveJson,
                            version = current.version + 1
                        )
                    )
                }
                careDao.insertDepartmentAssignment(
                    CareDepartmentAssignmentEntity(
                        id = CareIds.newId(),
                        ownerAccountId = workspace.ownerAccountId,
                        admissionId = request.admissionId,
                        departmentId = departmentId,
                        role = CareEnums.DepartmentRole.PRIMARY.name,
                        startsAt = request.effectiveAt,
                        createdAt = now,
                        createdBy = workspace.actorPersonId
                    )
                )
            }

            request.primaryTeamId?.let { teamId ->
                careDao.currentPrimaryTeam(request.admissionId)?.let { current ->
                    careDao.updateTeamAssignment(
                        current.copy(
                            endsAt = request.effectiveAt,
                            endsAtJson = effectiveJson,
                            version = current.version + 1
                        )
                    )
                }
                careDao.insertTeamAssignment(
                    CareAdmissionTeamAssignmentEntity(
                        id = CareIds.newId(),
                        ownerAccountId = workspace.ownerAccountId,
                        admissionId = request.admissionId,
                        teamId = teamId,
                        consultantPersonId = request.consultantPersonId,
                        role = CareEnums.TeamAssignmentRole.PRIMARY.name,
                        startsAt = request.effectiveAt,
                        createdAt = now,
                        createdBy = workspace.actorPersonId
                    )
                )
            }

            val nursingChange = request.nursingPersonId != null ||
                request.nursingStationId != null ||
                request.nursingProfessionalRoleId != null
            if (nursingChange) {
                require(request.nursingPersonId != null || request.nursingStationId != null) {
                    "Nursing assignment requires person or station"
                }
                careDao.currentNursingAssignments(request.admissionId).forEach { current ->
                    careDao.updateNursingAssignment(
                        current.copy(
                            endsAt = request.effectiveAt,
                            endsAtJson = effectiveJson,
                            version = current.version + 1
                        )
                    )
                }
                careDao.insertNursingAssignment(
                    CareNursingAssignmentEntity(
                        id = CareIds.newId(),
                        ownerAccountId = workspace.ownerAccountId,
                        admissionId = request.admissionId,
                        personId = request.nursingPersonId,
                        professionalRoleId = request.nursingProfessionalRoleId,
                        stationId = request.nursingStationId,
                        role = request.nursingRole.name,
                        startsAt = request.effectiveAt,
                        createdAt = now,
                        createdBy = workspace.actorPersonId
                    )
                )
            }

            val eventId = provenance.writeEvent(
                workspace = workspace,
                patientId = episode.patientId,
                admissionId = request.admissionId,
                eventType = "TRANSFER_RECORDED",
                effectiveTime = request.effectiveAt,
                operationId = request.operationId,
                now = now
            )
            val nextVersion = episode.version + 1
            careDao.updateEpisode(episode.copy(version = nextVersion))
            provenance.writeAudit(
                workspace = workspace,
                operationId = request.operationId,
                action = "TRANSFER",
                targetType = "HospitalEpisode",
                targetId = request.admissionId,
                priorVersion = episode.version,
                newVersion = nextVersion,
                now = now
            )

            val refs = JSONObject()
                .put("admissionId", request.admissionId)
                .put("eventId", eventId)
                .toString()
            val receipt = provenance.commitOperation(
                workspace = workspace,
                operationId = request.operationId,
                requestHash = requestHash,
                resultReferencesJson = refs,
                now = now
            )
            RecordTransferResult(
                admissionId = request.admissionId,
                eventId = eventId,
                receipt = receipt
            )
        }
    }

    suspend fun dischargeAdmission(
        workspace: CareWorkspace,
        admissionId: String,
        endedAt: ClinicalTime = ClinicalTime.instant(System.currentTimeMillis()),
        operationId: String = CareIds.newId()
    ): OperationReceipt {
        val requestHash = CareProvenanceWriter.sha256(
            listOf(admissionId, clinicalTimeConverters.fromClinicalTime(endedAt).orEmpty())
                .joinToString("|")
        )
        return database.withTransaction {
            provenance.beginOrReuse(workspace, operationId, requestHash)?.let { existing ->
                return@withTransaction OperationReceipt(
                    operationId = operationId,
                    resultReferencesJson = existing.resultReferences,
                    reused = true
                )
            }
            val episode = careDao.getEpisode(admissionId) ?: error("Admission not found")
            require(episode.ownerAccountId == workspace.ownerAccountId)
            require(episode.status == CareEnums.EpisodeStatus.ACTIVE.name) { "Admission is not active" }
            val now = System.currentTimeMillis()
            val nextVersion = episode.version + 1
            careDao.updateEpisode(
                episode.copy(
                    status = CareEnums.EpisodeStatus.DISCHARGED.name,
                    endedAt = endedAt,
                    version = nextVersion
                )
            )
            careDao.involvementsForAdmission(admissionId)
                .filter { it.status == CareEnums.InvolvementStatus.ACTIVE.name }
                .forEach { involvement ->
                    careDao.updateInvolvement(
                        involvement.copy(
                            status = CareEnums.InvolvementStatus.ENDED.name,
                            endsAt = endedAt,
                            version = involvement.version + 1
                        )
                    )
                }
            val eventId = provenance.writeEvent(
                workspace = workspace,
                patientId = episode.patientId,
                admissionId = admissionId,
                eventType = "ADMISSION_DISCHARGED",
                effectiveTime = endedAt,
                operationId = operationId,
                now = now
            )
            provenance.writeAudit(
                workspace = workspace,
                operationId = operationId,
                action = "DISCHARGE",
                targetType = "HospitalEpisode",
                targetId = admissionId,
                priorVersion = episode.version,
                newVersion = nextVersion,
                now = now
            )
            val refs = JSONObject()
                .put("admissionId", admissionId)
                .put("eventId", eventId)
                .toString()
            provenance.commitOperation(workspace, operationId, requestHash, refs, now)
        }
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key).takeIf { it.isNotBlank() }
}
