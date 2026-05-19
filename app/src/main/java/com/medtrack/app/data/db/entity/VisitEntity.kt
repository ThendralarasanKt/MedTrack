package com.medtrack.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

/**
 * VisitEntity: Represents a single consultation session.
 * 
 * WHY: We added an index on 'patientId' and 'visitDate' because 
 * 10-30 patients/day means thousands of visits over time.
 */
@Entity(
    tableName = "visits",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["patientId"]),
        Index(value = ["visitDate"])
    ]
)
data class VisitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val patientId: Int,
    val visitDate: String = LocalDate.now().toString(),
    val visitTime: String = LocalTime.now().toString(),
    val roomNo: String = "",
    val symptoms: String = "",
    val diagnosis: String = "",
    val progressNotes: String = ""
)
