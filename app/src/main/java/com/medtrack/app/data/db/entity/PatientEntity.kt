package com.medtrack.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * PatientEntity: Represents a row in the "patients" table.
 * 
 * WHY: This is the "Source of Truth" for patient data.
 * We added an Index on 'name' to ensure search stays fast with 10-30 patients/day.
 */
@Entity(
    tableName = "patients",
    indices = [Index(value = ["name"])]
)
data class PatientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val age: Int,
    val sex: String, // "Male", "Female", "Other"
    val contact: String = "",
    val address: String = "",
    val medHistory: String = "",
    val photoPath: String = "",
    val createdAt: String = LocalDateTime.now().toString(),
    val isDischarged: Boolean = false,
    val dischargedAt: String? = null
)
