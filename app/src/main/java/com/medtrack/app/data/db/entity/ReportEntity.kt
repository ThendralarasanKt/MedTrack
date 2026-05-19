package com.medtrack.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * ReportEntity: Represents a medical report (image or PDF) linked to a visit.
 * 
 * WHY: Storing file paths instead of actual files in the database keeps 
 * the database small and fast.
 */
@Entity(
    tableName = "reports",
    foreignKeys = [
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["id"],
            childColumns = ["visitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["visitId"])]
)
data class ReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val visitId: Int,
    val taskId: Int? = null, // Optional link to a specific task
    val fileName: String,
    val filePath: String, // Internal storage path
    val fileType: String, // "IMAGE" | "PDF"
    val uploadedAt: String = LocalDateTime.now().toString()
)
