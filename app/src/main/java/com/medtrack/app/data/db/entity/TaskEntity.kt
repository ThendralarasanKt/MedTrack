package com.medtrack.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * TaskEntity: Represents a medical task assigned during a visit.
 * 
 * WHY: This allows the doctor to track what needs to be done (e.g., Blood Test) 
 * and by whom. Using a String for 'role' allows for custom roles later.
 */
@Entity(
    tableName = "tasks",
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
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val visitId: Int,
    val taskName: String,
    val assignedTo: String,
    val role: String, // Flexible: "Nurse", "Lab Tech", or any custom role
    val instructions: String = "",
    val status: String = "PENDING" // "PENDING" or "DONE"
)
