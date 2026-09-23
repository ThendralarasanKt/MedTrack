package com.medtrack.app.data.care.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.medtrack.app.data.care.model.ClinicalTime

@Entity(tableName = "care_accounts")
data class CareAccountEntity(
    @PrimaryKey val id: String,
    val authSubject: String,
    val clinicianPersonId: String,
    val displayName: String,
    val ownerAccountId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_persons",
    indices = [Index(value = ["ownerAccountId", "displayName"])]
)
data class CarePersonEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val displayName: String,
    val identityState: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_patients",
    indices = [Index(value = ["ownerAccountId", "personId"], unique = true)]
)
data class CarePatientEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val personId: String,
    val birthDate: String? = null,
    val reportedAge: String? = null,
    val ageUnit: String? = null,
    val ageAsOf: String? = null,
    val sexConceptId: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(tableName = "care_professional_roles")
data class CareProfessionalRoleEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val personId: String,
    val professionCode: String,
    val validFrom: ClinicalTime,
    val validTo: ClinicalTime? = null,
    val specialtyConceptId: String? = null,
    val registrationIssuer: String? = null,
    val registrationNumber: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(tableName = "care_person_contacts")
data class CarePersonContactEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val personId: String,
    val kind: String,
    val value: String,
    val status: String,
    val label: String? = null,
    val verifiedAt: Long? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_hospitals",
    indices = [Index(value = ["ownerAccountId", "code"], unique = true)]
)
data class CareHospitalEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val code: String,
    val name: String,
    val timeZoneId: String,
    val status: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_departments",
    indices = [Index(value = ["hospitalId", "code"], unique = true)]
)
data class CareDepartmentEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val hospitalId: String,
    val code: String,
    val name: String,
    val status: String,
    val parentDepartmentId: String? = null,
    val specialtyConceptId: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_clinical_units",
    indices = [Index(value = ["departmentId", "code"], unique = true)]
)
data class CareClinicalUnitEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val departmentId: String,
    val code: String,
    val name: String,
    val status: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_clinical_teams",
    indices = [Index(value = ["hospitalId", "code"], unique = true)]
)
data class CareClinicalTeamEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val hospitalId: String,
    val code: String,
    val name: String,
    val status: String,
    val departmentId: String? = null,
    val clinicalUnitId: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(tableName = "care_team_memberships")
data class CareTeamMembershipEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val teamId: String,
    val professionalRoleId: String,
    val roleCode: String,
    val validFrom: ClinicalTime,
    val validTo: ClinicalTime? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_physical_locations",
    indices = [
        Index(value = ["hospitalId", "parentLocationId", "kind", "code"], unique = true)
    ]
)
data class CarePhysicalLocationEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val hospitalId: String,
    val parentLocationId: String? = null,
    val kind: String,
    val code: String,
    val label: String,
    val status: String,
    val structureState: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(tableName = "care_station_coverage")
data class CareStationCoverageEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val stationId: String,
    val coveredLocationId: String,
    val startsAt: ClinicalTime,
    val endsAt: ClinicalTime? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(tableName = "care_department_location_use")
data class CareDepartmentLocationUseEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val departmentId: String,
    val locationId: String,
    val validFrom: ClinicalTime,
    val validTo: ClinicalTime? = null,
    val purpose: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_reference_concepts",
    indices = [Index(value = ["domain", "system", "code", "terminologyVersion"], unique = true)]
)
data class CareReferenceConceptEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val domain: String,
    val system: String,
    val code: String,
    val display: String,
    val active: Boolean,
    val terminologyVersion: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(tableName = "care_concept_aliases")
data class CareConceptAliasEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val conceptId: String,
    val alias: String,
    val normalizedAlias: String,
    val language: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)
