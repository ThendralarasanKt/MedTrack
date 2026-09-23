package com.medtrack.app.data.care.command

import com.medtrack.app.data.care.dao.CareDao
import com.medtrack.app.data.care.entity.CareAppliedOperationEntity
import com.medtrack.app.data.care.entity.CareAuditEntryEntity
import com.medtrack.app.data.care.entity.CareClinicalEventEntity
import com.medtrack.app.data.care.entity.CareEventParticipantEntity
import com.medtrack.app.data.care.entity.CareRecordRevisionEntity
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.CareIds
import com.medtrack.app.data.care.model.ClinicalTime
import java.security.MessageDigest

class OperationPayloadConflictException(
    val operationId: String
) : IllegalStateException(
    "Operation $operationId was already applied with a different request payload"
)

data class OperationReceipt(
    val operationId: String,
    val resultReferencesJson: String,
    val reused: Boolean
)

/**
 * Idempotent operation gate + provenance writers for care commands.
 * Must be called inside an open Room transaction.
 */
class CareProvenanceWriter(
    private val careDao: CareDao
) {
    suspend fun beginOrReuse(
        workspace: CareWorkspace,
        operationId: String,
        requestHash: String
    ): CareAppliedOperationEntity? {
        val existing = careDao.getAppliedOperation(workspace.ownerAccountId, operationId)
        if (existing != null) {
            if (existing.requestHash != requestHash) {
                throw OperationPayloadConflictException(operationId)
            }
            return existing
        }
        return null
    }

    suspend fun commitOperation(
        workspace: CareWorkspace,
        operationId: String,
        requestHash: String,
        resultReferencesJson: String,
        now: Long = System.currentTimeMillis()
    ): OperationReceipt {
        careDao.insertAppliedOperation(
            CareAppliedOperationEntity(
                id = CareIds.newId(),
                ownerAccountId = workspace.ownerAccountId,
                operationId = operationId,
                requestHash = requestHash,
                committedAt = now,
                resultReferences = resultReferencesJson,
                createdAt = now,
                createdBy = workspace.actorPersonId
            )
        )
        return OperationReceipt(
            operationId = operationId,
            resultReferencesJson = resultReferencesJson,
            reused = false
        )
    }

    suspend fun writeEvent(
        workspace: CareWorkspace,
        patientId: String,
        admissionId: String?,
        eventType: String,
        effectiveTime: ClinicalTime,
        operationId: String,
        verification: CareEnums.Verification = CareEnums.Verification.REPORTED,
        participantRole: String = "AUTHOR",
        now: Long = System.currentTimeMillis()
    ): String {
        val eventId = CareIds.newId()
        careDao.insertClinicalEvent(
            CareClinicalEventEntity(
                id = eventId,
                ownerAccountId = workspace.ownerAccountId,
                patientId = patientId,
                admissionId = admissionId,
                eventType = eventType,
                effectiveTime = effectiveTime,
                recordedAt = now,
                recorderPersonId = workspace.actorPersonId,
                verification = verification.name,
                recordState = "ACTIVE",
                operationId = operationId,
                createdAt = now,
                createdBy = workspace.actorPersonId
            )
        )
        careDao.insertEventParticipant(
            CareEventParticipantEntity(
                id = CareIds.newId(),
                ownerAccountId = workspace.ownerAccountId,
                eventId = eventId,
                personId = workspace.actorPersonId,
                role = participantRole,
                createdAt = now,
                createdBy = workspace.actorPersonId
            )
        )
        return eventId
    }

    suspend fun addParticipant(
        workspace: CareWorkspace,
        eventId: String,
        personId: String,
        role: String,
        now: Long = System.currentTimeMillis()
    ) {
        careDao.insertEventParticipant(
            CareEventParticipantEntity(
                id = CareIds.newId(),
                ownerAccountId = workspace.ownerAccountId,
                eventId = eventId,
                personId = personId,
                role = role,
                createdAt = now,
                createdBy = workspace.actorPersonId
            )
        )
    }

    suspend fun writeAudit(
        workspace: CareWorkspace,
        operationId: String,
        action: String,
        targetType: String,
        targetId: String,
        priorVersion: Int?,
        newVersion: Int,
        reason: String? = null,
        now: Long = System.currentTimeMillis()
    ) {
        careDao.insertAuditEntry(
            CareAuditEntryEntity(
                id = CareIds.newId(),
                ownerAccountId = workspace.ownerAccountId,
                operationId = operationId,
                actorPersonId = workspace.actorPersonId,
                action = action,
                targetType = targetType,
                targetId = targetId,
                priorVersion = priorVersion,
                newVersion = newVersion,
                recordedAt = now,
                reason = reason,
                createdAt = now,
                createdBy = workspace.actorPersonId
            )
        )
    }

    suspend fun writeRevision(
        workspace: CareWorkspace,
        recordType: String,
        recordId: String,
        recordVersion: Int,
        payloadSnapshot: String,
        eventId: String,
        schemaVersion: Int = 1,
        now: Long = System.currentTimeMillis()
    ) {
        careDao.insertRecordRevision(
            CareRecordRevisionEntity(
                id = CareIds.newId(),
                ownerAccountId = workspace.ownerAccountId,
                recordType = recordType,
                recordId = recordId,
                recordVersion = recordVersion,
                schemaVersion = schemaVersion,
                payloadSnapshot = payloadSnapshot,
                eventId = eventId,
                createdAt = now,
                createdBy = workspace.actorPersonId
            )
        )
    }

    companion object {
        fun sha256(payload: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(payload.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
