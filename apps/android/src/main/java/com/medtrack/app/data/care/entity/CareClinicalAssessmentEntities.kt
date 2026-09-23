package com.medtrack.app.data.care.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.medtrack.app.data.care.model.ClinicalTime

@Entity(
    tableName = "care_encounters",
    indices = [
        Index(value = ["ownerAccountId", "admissionId"]),
        Index(value = ["eventId"])
    ]
)
data class CareEncounterEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val kind: String,
    val reviewedAt: ClinicalTime,
    val summary: String,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_clinical_notes",
    indices = [
        Index(value = ["ownerAccountId", "patientId"]),
        Index(value = ["encounterId"]),
        Index(value = ["eventId"])
    ]
)
data class CareClinicalNoteEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val patientId: String,
    val admissionId: String? = null,
    val encounterId: String? = null,
    val kind: String,
    val text: String,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_problems",
    indices = [
        Index(value = ["ownerAccountId", "patientId", "status"]),
        Index(value = ["eventId"])
    ]
)
data class CareProblemEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val patientId: String,
    val admissionId: String? = null,
    val description: String,
    val codeSystem: String? = null,
    val code: String? = null,
    val certainty: String,
    val status: String,
    val onset: ClinicalTime,
    val resolvedAt: ClinicalTime? = null,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_allergies",
    indices = [
        Index(value = ["ownerAccountId", "patientId", "status"]),
        Index(value = ["eventId"])
    ]
)
data class CareAllergyEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val patientId: String,
    val substance: String,
    val reaction: String? = null,
    val severity: String? = null,
    val status: String,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_allergy_assessments",
    indices = [
        Index(value = ["ownerAccountId", "patientId"]),
        Index(value = ["eventId"])
    ]
)
data class CareAllergyAssessmentEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val patientId: String,
    val result: String,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_observations",
    indices = [
        Index(value = ["ownerAccountId", "patientId"]),
        Index(value = ["encounterId"]),
        Index(value = ["eventId"])
    ]
)
data class CareObservationEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val patientId: String,
    val admissionId: String? = null,
    val reportId: String? = null,
    val specimenId: String? = null,
    val encounterId: String? = null,
    val name: String,
    val valueKind: String,
    val numericValue: String? = null,
    val textValue: String? = null,
    val booleanValue: Boolean? = null,
    val unit: String? = null,
    val referenceRangeText: String? = null,
    val observedAt: ClinicalTime,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_clinical_decisions",
    indices = [
        Index(value = ["ownerAccountId", "admissionId", "status"]),
        Index(value = ["encounterId"]),
        Index(value = ["eventId"])
    ]
)
data class CareClinicalDecisionEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val encounterId: String? = null,
    val description: String,
    val rationale: String? = null,
    val decidedAt: ClinicalTime,
    val status: String,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_decision_problem_links",
    indices = [Index(value = ["decisionId", "problemId"], unique = true)]
)
data class CareDecisionProblemLinkEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val decisionId: String,
    val problemId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_plan_revisions",
    indices = [
        Index(value = ["ownerAccountId", "admissionId"]),
        Index(value = ["decisionId"]),
        Index(value = ["eventId"])
    ]
)
data class CarePlanRevisionEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val decisionId: String? = null,
    val goals: String,
    val instructions: String,
    val effectiveAt: ClinicalTime,
    val previousRevisionId: String? = null,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)
