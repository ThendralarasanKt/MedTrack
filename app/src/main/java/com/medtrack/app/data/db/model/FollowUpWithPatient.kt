package com.medtrack.app.data.db.model

data class FollowUpWithPatient(
    val id: Int,
    val patientId: Int,
    val visitId: Int,
    val patientName: String,
    val roomNo: String,
    val scheduledDate: String,
    val scheduledTime: String,
    val reason: String,
    val isNotified: Boolean,
    val status: String,
    val notifiedAt: String?,
    val completedAt: String?
) {
    fun toEntity() = com.medtrack.app.data.db.entity.FollowUpEntity(
        id = id,
        patientId = patientId,
        visitId = visitId,
        scheduledDate = scheduledDate,
        scheduledTime = scheduledTime,
        reason = reason,
        isNotified = isNotified,
        status = status,
        notifiedAt = notifiedAt,
        completedAt = completedAt
    )
}
