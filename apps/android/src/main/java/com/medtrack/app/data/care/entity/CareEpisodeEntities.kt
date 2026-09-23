package com.medtrack.app.data.care.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.medtrack.app.data.care.model.ClinicalTime

@Entity(
    tableName = "care_patient_identifiers",
    indices = [Index(value = ["ownerAccountId", "hospitalId", "system", "value"])]
)
data class CarePatientIdentifierEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val patientId: String,
    val hospitalId: String,
    val system: String,
    val value: String,
    val status: String,
    val sourceItemId: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_hospital_episodes",
    indices = [
        Index(value = ["ownerAccountId", "patientId", "status"]),
        Index(value = ["patientId", "kind", "status"])
    ]
)
data class CareHospitalEpisodeEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val patientId: String,
    val hospitalId: String,
    val kind: String,
    val externalId: String? = null,
    val status: String,
    val startedAt: ClinicalTime,
    val endedAt: ClinicalTime? = null,
    val eventId: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(tableName = "care_episode_links")
data class CareEpisodeLinkEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val fromEpisodeId: String,
    val toEpisodeId: String,
    val relationship: String,
    val eventId: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_involvements",
    indices = [Index(value = ["ownerAccountId", "personId", "status"])]
)
data class CareInvolvementEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val personId: String,
    val teamId: String? = null,
    val role: String,
    val reason: String? = null,
    val status: String,
    val startsAt: ClinicalTime,
    val endsAt: ClinicalTime? = null,
    val eventId: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(tableName = "care_unassigned_intakes")
data class CareUnassignedIntakeEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val summary: String,
    val identityHintsJson: String,
    val status: String,
    val resolvedPatientId: String? = null,
    val resolvedAdmissionId: String? = null,
    val resolutionNote: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_location_assignments",
    indices = [Index(value = ["admissionId", "endsAtJson"])]
)
data class CareLocationAssignmentEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val locationId: String,
    val stationId: String? = null,
    val startsAt: ClinicalTime,
    val endsAt: ClinicalTime? = null,
    /** Denormalized for indexing current rows (null means current). */
    val endsAtJson: String? = null,
    val verification: String,
    val eventId: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(tableName = "care_department_assignments")
data class CareDepartmentAssignmentEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val departmentId: String,
    val role: String,
    val startsAt: ClinicalTime,
    val endsAt: ClinicalTime? = null,
    val endsAtJson: String? = null,
    val eventId: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(tableName = "care_admission_team_assignments")
data class CareAdmissionTeamAssignmentEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val teamId: String,
    val consultantPersonId: String? = null,
    val role: String,
    val startsAt: ClinicalTime,
    val endsAt: ClinicalTime? = null,
    val endsAtJson: String? = null,
    val eventId: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(tableName = "care_nursing_assignments")
data class CareNursingAssignmentEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val personId: String? = null,
    val professionalRoleId: String? = null,
    val stationId: String? = null,
    val role: String,
    val startsAt: ClinicalTime,
    val endsAt: ClinicalTime? = null,
    val endsAtJson: String? = null,
    val eventId: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)
