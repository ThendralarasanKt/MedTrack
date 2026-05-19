package com.medtrack.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * FollowUpEntity: Represents a scheduled follow-up appointment.
 * 
 * WHY: We updated this to include 'scheduledTime' because the doctor 
 * needs to know the specific hour of the appointment.
 */
@Entity(
    tableName = "follow_ups",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["id"],
            childColumns = ["visitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["patientId"]),
        Index(value = ["visitId"])
    ]
)
data class FollowUpEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val patientId: Int,
    val visitId: Int,
    val scheduledDate: String, // "2026-05-18"
    val scheduledTime: String, // "10:00"
    val reason: String = "",
    val isNotified: Boolean = false,
    val status: String = "PENDING",
    val notifiedAt: String? = null,
    val completedAt: String? = null,
    val workManagerId: String? = null
)
