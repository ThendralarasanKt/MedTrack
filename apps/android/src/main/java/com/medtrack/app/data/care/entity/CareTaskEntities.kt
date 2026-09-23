package com.medtrack.app.data.care.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.medtrack.app.data.care.model.ClinicalTime

@Entity(
    tableName = "care_tasks",
    indices = [
        Index(value = ["ownerAccountId", "admissionId", "status"]),
        Index(value = ["followUpOwnerPersonId", "status"]),
        Index(value = ["eventId"])
    ]
)
data class CareTaskEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val kind: String,
    val title: String,
    val instructions: String? = null,
    val status: String,
    val priority: String,
    val followUpOwnerPersonId: String,
    val dueAt: Long? = null,
    val dueZoneId: String? = null,
    val waitingReason: String? = null,
    val completedAt: ClinicalTime? = null,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_task_assignments",
    indices = [Index(value = ["taskId"]), Index(value = ["eventId"])]
)
data class CareTaskAssignmentEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val taskId: String,
    val personId: String? = null,
    val teamId: String? = null,
    val stationId: String? = null,
    val startsAt: ClinicalTime,
    val endsAt: ClinicalTime? = null,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_task_clinical_links",
    indices = [Index(value = ["taskId"])]
)
data class CareTaskClinicalLinkEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val taskId: String,
    val referralId: String? = null,
    val questionId: String? = null,
    val decisionId: String? = null,
    val medicationOrderId: String? = null,
    val procedureOrderId: String? = null,
    val investigationOrderId: String? = null,
    val reportId: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_task_responses",
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["operationId"], unique = true),
        Index(value = ["eventId"])
    ]
)
data class CareTaskResponseEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val taskId: String,
    val action: String,
    val actorPersonId: String,
    val note: String? = null,
    val performedAt: ClinicalTime,
    val oldDueAt: Long? = null,
    val newDueAt: Long? = null,
    val eventId: String,
    val operationId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_reminder_schedules",
    indices = [
        Index(value = ["careTaskId"]),
        Index(value = ["platformRequestKey"], unique = true)
    ]
)
data class CareReminderScheduleEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val careTaskId: String? = null,
    val intakeFollowUpId: String? = null,
    val triggerAt: Long,
    val zoneId: String,
    val revision: Int,
    val state: String,
    val precision: String,
    val platformRequestKey: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_notification_attempts",
    indices = [Index(value = ["scheduleId"])]
)
data class CareNotificationAttemptEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val scheduleId: String,
    val scheduleRevision: Int,
    val firedAt: Long? = null,
    val postAttemptedAt: Long? = null,
    val status: String,
    val reasonCode: String? = null,
    val interactedAt: Long? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_scheduling_outbox",
    indices = [Index(value = ["scheduleId", "scheduleRevision"])]
)
data class CareSchedulingOutboxEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val scheduleId: String,
    val scheduleRevision: Int,
    val action: String,
    val state: String,
    val attemptCount: Int = 0,
    val lastErrorCode: String? = null,
    val nextRetryAt: Long? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)
