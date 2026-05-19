package com.medtrack.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * MedicineEntity: Represents a medication prescribed during a visit.
 */
@Entity(
    tableName = "medicines",
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
data class MedicineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val visitId: Int,
    val name: String,
    val dosage: String = "",
    val duration: String = "",
    val notes: String = ""
)
