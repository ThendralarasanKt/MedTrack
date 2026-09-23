package com.medtrack.app.data.care.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.medtrack.app.data.care.model.ClinicalTime

@Entity(
    tableName = "care_clinical_events",
    indices = [
        Index(value = ["ownerAccountId", "patientId", "recordedAt"]),
        Index(value = ["operationId"])
    ]
)
data class CareClinicalEventEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val patientId: String,
    val admissionId: String? = null,
    val eventType: String,
    val effectiveTime: ClinicalTime,
    val recordedAt: Long,
    val recorderPersonId: String,
    val verification: String,
    val recordState: String,
    val operationId: String,
    val supersedesEventId: String? = null,
    val correctionReason: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_event_participants",
    indices = [Index(value = ["eventId", "personId", "role"], unique = true)]
)
data class CareEventParticipantEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val eventId: String,
    val personId: String,
    val role: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_record_revisions",
    indices = [Index(value = ["recordType", "recordId", "recordVersion"], unique = true)]
)
data class CareRecordRevisionEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val recordType: String,
    val recordId: String,
    val recordVersion: Int,
    val schemaVersion: Int,
    val payloadSnapshot: String,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_audit_entries",
    indices = [Index(value = ["operationId"]), Index(value = ["targetType", "targetId"])]
)
data class CareAuditEntryEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val operationId: String,
    val actorPersonId: String,
    val action: String,
    val targetType: String,
    val targetId: String,
    val priorVersion: Int? = null,
    val newVersion: Int,
    val recordedAt: Long,
    val reason: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_applied_operations",
    indices = [Index(value = ["ownerAccountId", "operationId"], unique = true)]
)
data class CareAppliedOperationEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val operationId: String,
    val requestHash: String,
    val committedAt: Long,
    val resultReferences: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)
