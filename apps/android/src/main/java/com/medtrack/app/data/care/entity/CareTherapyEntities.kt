package com.medtrack.app.data.care.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.medtrack.app.data.care.model.ClinicalTime
import com.medtrack.app.data.care.model.Regimen

@Entity(
    tableName = "care_medication_definitions",
    indices = [Index(value = ["ownerAccountId", "displayName"])]
)
data class CareMedicationDefinitionEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val displayName: String,
    val ingredient: String? = null,
    val strengthValue: String? = null,
    val strengthUnit: String? = null,
    val form: String? = null,
    val codeSystem: String? = null,
    val code: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_medication_history_items",
    indices = [
        Index(value = ["ownerAccountId", "patientId"]),
        Index(value = ["eventId"])
    ]
)
data class CareMedicationHistoryItemEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val patientId: String,
    val admissionId: String? = null,
    val drugText: String,
    val regimenText: String? = null,
    val useStatus: String,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_medication_orders",
    indices = [
        Index(value = ["ownerAccountId", "admissionId", "status"]),
        Index(value = ["medicationId"]),
        Index(value = ["eventId"])
    ]
)
data class CareMedicationOrderEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val medicationId: String,
    val decisionId: String? = null,
    val prescriberId: String? = null,
    val orderedAt: ClinicalTime,
    val doseValue: String? = null,
    val doseUnit: String? = null,
    val doseText: String? = null,
    val route: String? = null,
    val schedule: Regimen,
    val startsAt: ClinicalTime,
    val endsAt: ClinicalTime? = null,
    val status: String,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_medication_order_events",
    indices = [Index(value = ["orderId"]), Index(value = ["eventId"])]
)
data class CareMedicationOrderEventEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val orderId: String,
    val action: String,
    val reason: String? = null,
    val previousOrderVersion: Int? = null,
    val newOrderVersion: Int,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_medication_administrations",
    indices = [
        Index(value = ["ownerAccountId", "admissionId"]),
        Index(value = ["orderId"]),
        Index(value = ["eventId"])
    ]
)
data class CareMedicationAdministrationEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val orderId: String? = null,
    val medicationId: String,
    val outcome: String,
    val doseValue: String? = null,
    val doseUnit: String? = null,
    val route: String? = null,
    val administeredAt: ClinicalTime,
    val performerId: String? = null,
    val reason: String? = null,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_procedure_orders",
    indices = [
        Index(value = ["ownerAccountId", "admissionId", "status"]),
        Index(value = ["eventId"])
    ]
)
data class CareProcedureOrderEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val decisionId: String? = null,
    val procedureName: String,
    val requesterId: String? = null,
    val requestedAt: ClinicalTime,
    val plannedAt: ClinicalTime? = null,
    val status: String,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_procedure_events",
    indices = [
        Index(value = ["ownerAccountId", "admissionId"]),
        Index(value = ["orderId"]),
        Index(value = ["eventId"])
    ]
)
data class CareProcedureEventEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val orderId: String? = null,
    val procedureName: String,
    val outcome: String,
    val performedAt: ClinicalTime,
    val findings: String? = null,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_investigation_orders",
    indices = [
        Index(value = ["ownerAccountId", "admissionId", "status"]),
        Index(value = ["eventId"])
    ]
)
data class CareInvestigationOrderEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val decisionId: String? = null,
    val kind: String,
    val testName: String,
    val requesterId: String? = null,
    val requestedAt: ClinicalTime,
    val requestGroupId: String? = null,
    val status: String,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_specimens",
    indices = [
        Index(value = ["ownerAccountId", "admissionId"]),
        Index(value = ["eventId"])
    ]
)
data class CareSpecimenEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val type: String,
    val accession: String? = null,
    val collectedAt: ClinicalTime,
    val collectorId: String? = null,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_order_specimen_links",
    indices = [Index(value = ["orderId", "specimenId"], unique = true)]
)
data class CareOrderSpecimenLinkEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val orderId: String,
    val specimenId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_imaging_studies",
    indices = [
        Index(value = ["ownerAccountId", "admissionId"]),
        Index(value = ["orderId"]),
        Index(value = ["eventId"])
    ]
)
data class CareImagingStudyEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val orderId: String? = null,
    val modality: String,
    val bodySite: String? = null,
    val accession: String? = null,
    val performedAt: ClinicalTime,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_documents",
    indices = [Index(value = ["ownerAccountId", "admissionId"])]
)
data class CareDocumentEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String? = null,
    val patientId: String? = null,
    val fileName: String,
    val filePath: String,
    val fileType: String,
    val attachedAt: Long,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_diagnostic_reports",
    indices = [
        Index(value = ["ownerAccountId", "admissionId", "status"]),
        Index(value = ["eventId"])
    ]
)
data class CareDiagnosticReportEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val admissionId: String,
    val kind: String,
    val issuer: String? = null,
    val reportedAt: ClinicalTime,
    val status: String,
    val previousReportId: String? = null,
    val missingPredecessorNote: String? = null,
    val imagingStudyId: String? = null,
    val narrative: String? = null,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_report_order_links",
    indices = [Index(value = ["reportId", "orderId"], unique = true)]
)
data class CareReportOrderLinkEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val reportId: String,
    val orderId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_report_document_links",
    indices = [Index(value = ["reportId", "sequence"], unique = true)]
)
data class CareReportDocumentLinkEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val reportId: String,
    val documentId: String,
    val sequence: Int,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)

@Entity(
    tableName = "care_report_reviews",
    indices = [
        Index(value = ["reportId"]),
        Index(value = ["eventId"])
    ]
)
data class CareReportReviewEntity(
    @PrimaryKey val id: String,
    val ownerAccountId: String,
    val reportId: String,
    val reviewerPersonId: String,
    val reviewedAt: Long,
    val outcome: String,
    val decisionId: String? = null,
    val note: String? = null,
    val eventId: String,
    val createdAt: Long,
    val createdBy: String,
    val version: Int = 1
)
