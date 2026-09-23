package com.medtrack.app.data.care.command

import androidx.room.withTransaction
import com.medtrack.app.data.care.dao.CareDao
import com.medtrack.app.data.care.dao.CareTherapyDao
import com.medtrack.app.data.care.entity.*
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.CareIds
import com.medtrack.app.data.care.model.ClinicalTime
import com.medtrack.app.data.care.model.Regimen
import com.medtrack.app.data.db.AppDatabase
import com.medtrack.app.data.db.converters.ClinicalTimeConverters
import com.medtrack.app.data.db.converters.RegimenConverters
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

data class ChangeMedicationOrderRequest(
    val admissionId: String,
    val medicationId: String? = null,
    val medicationDisplayName: String? = null,
    val orderId: String? = null,
    val action: CareEnums.MedicationOrderAction = CareEnums.MedicationOrderAction.START,
    val decisionId: String? = null,
    val prescriberId: String? = null,
    val orderedAt: ClinicalTime = ClinicalTime.instant(System.currentTimeMillis()),
    val doseValue: String? = null,
    val doseUnit: String? = null,
    val doseText: String? = null,
    val route: String? = null,
    val schedule: Regimen = Regimen.textOnly("as directed"),
    val startsAt: ClinicalTime = orderedAt,
    val endsAt: ClinicalTime? = null,
    val reason: String? = null,
    val expectedOrderVersion: Int? = null,
    val operationId: String = CareIds.newId()
)

data class ChangeMedicationOrderResult(
    val orderId: String,
    val medicationId: String,
    val eventId: String,
    val receipt: OperationReceipt
)

data class RecordAdministrationRequest(
    val admissionId: String,
    val medicationId: String,
    val orderId: String? = null,
    val outcome: CareEnums.AdministrationOutcome,
    val doseValue: String? = null,
    val doseUnit: String? = null,
    val route: String? = null,
    val administeredAt: ClinicalTime,
    val performerId: String? = null,
    val reason: String? = null,
    val reasonUnknown: Boolean = false,
    val operationId: String = CareIds.newId()
)

data class RecordAdministrationResult(
    val administrationId: String,
    val eventId: String,
    val receipt: OperationReceipt
)

data class AttachAndMatchReportRequest(
    val admissionId: String,
    val fileName: String,
    val filePath: String,
    val fileType: String,
    val kind: CareEnums.DiagnosticReportKind = CareEnums.DiagnosticReportKind.LAB,
    val reportedAt: ClinicalTime,
    val status: CareEnums.DiagnosticReportStatus = CareEnums.DiagnosticReportStatus.PRELIMINARY,
    val previousReportId: String? = null,
    val missingPredecessorNote: String? = null,
    val narrative: String? = null,
    val investigationOrderId: String? = null,
    val operationId: String = CareIds.newId()
)

data class AttachAndMatchReportResult(
    val documentId: String,
    val reportId: String,
    val eventId: String,
    val receipt: OperationReceipt
)

data class RecordReportReviewRequest(
    val reportId: String,
    val outcome: CareEnums.ReportReviewOutcome,
    val decisionId: String? = null,
    val note: String? = null,
    val reviewedAt: Long = System.currentTimeMillis(),
    val operationId: String = CareIds.newId()
)

@Singleton
class CareTherapyCommandService @Inject constructor(
    private val database: AppDatabase,
    private val careDao: CareDao,
    private val therapyDao: CareTherapyDao
) {
    private val clinicalTimeConverters = ClinicalTimeConverters()
    private val regimenConverters = RegimenConverters()
    private val provenance = CareProvenanceWriter(careDao)

    suspend fun changeMedicationOrder(
        workspace: CareWorkspace,
        request: ChangeMedicationOrderRequest
    ): ChangeMedicationOrderResult {
        val requestHash = CareProvenanceWriter.sha256(
            listOf(
                request.admissionId,
                request.medicationId.orEmpty(),
                request.medicationDisplayName.orEmpty(),
                request.orderId.orEmpty(),
                request.action.name,
                request.decisionId.orEmpty(),
                request.prescriberId.orEmpty(),
                clinicalTimeConverters.fromClinicalTime(request.orderedAt).orEmpty(),
                request.doseValue.orEmpty(),
                request.doseUnit.orEmpty(),
                request.doseText.orEmpty(),
                request.route.orEmpty(),
                regimenConverters.fromRegimen(request.schedule).orEmpty(),
                clinicalTimeConverters.fromClinicalTime(request.startsAt).orEmpty(),
                clinicalTimeConverters.fromClinicalTime(request.endsAt).orEmpty(),
                request.reason.orEmpty()
            ).joinToString("|")
        )
        return database.withTransaction {
            provenance.beginOrReuse(workspace, request.operationId, requestHash)?.let { existing ->
                val refs = JSONObject(existing.resultReferences)
                return@withTransaction ChangeMedicationOrderResult(
                    orderId = refs.getString("orderId"),
                    medicationId = refs.getString("medicationId"),
                    eventId = refs.getString("eventId"),
                    receipt = OperationReceipt(request.operationId, existing.resultReferences, true)
                )
            }
            val episode = careDao.getEpisode(request.admissionId) ?: error("Admission not found")
            require(episode.ownerAccountId == workspace.ownerAccountId)
            val now = System.currentTimeMillis()
            val eventId = provenance.writeEvent(
                workspace = workspace,
                patientId = episode.patientId,
                admissionId = request.admissionId,
                eventType = "MEDICATION_ORDER_CHANGED",
                effectiveTime = request.orderedAt,
                operationId = request.operationId,
                now = now
            )

            val orderId: String
            val medicationId: String
            if (request.action == CareEnums.MedicationOrderAction.START) {
                require(request.orderId == null) { "START creates a new order" }
                medicationId = resolveMedicationId(workspace, request, now)
                orderId = CareIds.newId()
                therapyDao.insertMedicationOrder(
                    CareMedicationOrderEntity(
                        id = orderId,
                        ownerAccountId = workspace.ownerAccountId,
                        admissionId = request.admissionId,
                        medicationId = medicationId,
                        decisionId = request.decisionId,
                        prescriberId = request.prescriberId,
                        orderedAt = request.orderedAt,
                        doseValue = request.doseValue,
                        doseUnit = request.doseUnit,
                        doseText = request.doseText,
                        route = request.route,
                        schedule = request.schedule,
                        startsAt = request.startsAt,
                        endsAt = request.endsAt,
                        status = CareEnums.MedicationOrderStatus.ACTIVE.name,
                        eventId = eventId,
                        createdAt = now,
                        createdBy = workspace.actorPersonId
                    )
                )
                therapyDao.insertMedicationOrderEvent(
                    CareMedicationOrderEventEntity(
                        id = CareIds.newId(),
                        ownerAccountId = workspace.ownerAccountId,
                        orderId = orderId,
                        action = request.action.name,
                        reason = request.reason,
                        previousOrderVersion = null,
                        newOrderVersion = 1,
                        eventId = eventId,
                        createdAt = now,
                        createdBy = workspace.actorPersonId
                    )
                )
            } else {
                val existing = therapyDao.getMedicationOrder(request.orderId ?: error("orderId required"))
                    ?: error("Order not found")
                require(existing.ownerAccountId == workspace.ownerAccountId)
                require(existing.admissionId == request.admissionId)
                request.expectedOrderVersion?.let { expected ->
                    if (existing.version != expected) {
                        throw StaleRecordException("MedicationOrder", existing.id, expected, existing.version)
                    }
                }
                medicationId = existing.medicationId
                val nextStatus = when (request.action) {
                    CareEnums.MedicationOrderAction.HOLD -> CareEnums.MedicationOrderStatus.HELD
                    CareEnums.MedicationOrderAction.RESUME -> CareEnums.MedicationOrderStatus.ACTIVE
                    CareEnums.MedicationOrderAction.STOP -> CareEnums.MedicationOrderStatus.STOPPED
                    CareEnums.MedicationOrderAction.COMPLETE -> CareEnums.MedicationOrderStatus.COMPLETED
                    CareEnums.MedicationOrderAction.CHANGE -> CareEnums.MedicationOrderStatus.ACTIVE
                    CareEnums.MedicationOrderAction.START -> error("unreachable")
                }
                validateOrderTransition(existing.status, request.action)
                val nextVersion = existing.version + 1
                orderId = existing.id
                therapyDao.updateMedicationOrder(
                    existing.copy(
                        doseValue = request.doseValue ?: existing.doseValue,
                        doseUnit = request.doseUnit ?: existing.doseUnit,
                        doseText = request.doseText ?: existing.doseText,
                        route = request.route ?: existing.route,
                        schedule = request.schedule,
                        endsAt = request.endsAt ?: existing.endsAt,
                        status = nextStatus.name,
                        eventId = eventId,
                        version = nextVersion
                    )
                )
                therapyDao.insertMedicationOrderEvent(
                    CareMedicationOrderEventEntity(
                        id = CareIds.newId(),
                        ownerAccountId = workspace.ownerAccountId,
                        orderId = orderId,
                        action = request.action.name,
                        reason = request.reason,
                        previousOrderVersion = existing.version,
                        newOrderVersion = nextVersion,
                        eventId = eventId,
                        createdAt = now,
                        createdBy = workspace.actorPersonId
                    )
                )
            }

            provenance.writeAudit(
                workspace, request.operationId, "MEDICATION_ORDER", "MedicationOrder",
                orderId, null, 1, now = now
            )
            val refs = JSONObject()
                .put("orderId", orderId)
                .put("medicationId", medicationId)
                .put("eventId", eventId)
                .toString()
            val receipt = provenance.commitOperation(
                workspace, request.operationId, requestHash, refs, now
            )
            ChangeMedicationOrderResult(orderId, medicationId, eventId, receipt)
        }
    }

    suspend fun recordAdministration(
        workspace: CareWorkspace,
        request: RecordAdministrationRequest
    ): RecordAdministrationResult {
        if (request.outcome != CareEnums.AdministrationOutcome.GIVEN) {
            require(!request.reason.isNullOrBlank() || request.reasonUnknown) {
                "OMITTED/REFUSED requires reason or explicitly unknown reason"
            }
        }
        val requestHash = CareProvenanceWriter.sha256(
            listOf(
                request.admissionId,
                request.medicationId,
                request.orderId.orEmpty(),
                request.outcome.name,
                request.doseValue.orEmpty(),
                request.doseUnit.orEmpty(),
                request.route.orEmpty(),
                clinicalTimeConverters.fromClinicalTime(request.administeredAt).orEmpty(),
                request.performerId.orEmpty(),
                request.reason.orEmpty(),
                request.reasonUnknown.toString()
            ).joinToString("|")
        )
        return database.withTransaction {
            provenance.beginOrReuse(workspace, request.operationId, requestHash)?.let { existing ->
                val refs = JSONObject(existing.resultReferences)
                return@withTransaction RecordAdministrationResult(
                    administrationId = refs.getString("administrationId"),
                    eventId = refs.getString("eventId"),
                    receipt = OperationReceipt(request.operationId, existing.resultReferences, true)
                )
            }
            val episode = careDao.getEpisode(request.admissionId) ?: error("Admission not found")
            require(episode.ownerAccountId == workspace.ownerAccountId)
            request.orderId?.let { orderId ->
                val order = therapyDao.getMedicationOrder(orderId) ?: error("Order not found")
                require(order.admissionId == request.admissionId) {
                    "Administration order must match admission"
                }
                require(order.medicationId == request.medicationId) {
                    "Administration medication must match order"
                }
            }
            val now = System.currentTimeMillis()
            val administrationId = CareIds.newId()
            val eventId = provenance.writeEvent(
                workspace, episode.patientId, request.admissionId,
                "MEDICATION_ADMINISTRATION_RECORDED", request.administeredAt,
                request.operationId, now = now
            )
            therapyDao.insertMedicationAdministration(
                CareMedicationAdministrationEntity(
                    id = administrationId,
                    ownerAccountId = workspace.ownerAccountId,
                    admissionId = request.admissionId,
                    orderId = request.orderId,
                    medicationId = request.medicationId,
                    outcome = request.outcome.name,
                    doseValue = request.doseValue,
                    doseUnit = request.doseUnit,
                    route = request.route,
                    administeredAt = request.administeredAt,
                    performerId = request.performerId,
                    reason = if (request.reasonUnknown && request.reason.isNullOrBlank()) {
                        "UNKNOWN"
                    } else {
                        request.reason
                    },
                    eventId = eventId,
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )
            provenance.writeAudit(
                workspace, request.operationId, "CREATE", "MedicationAdministration",
                administrationId, null, 1, now = now
            )
            val refs = JSONObject()
                .put("administrationId", administrationId)
                .put("eventId", eventId)
                .toString()
            val receipt = provenance.commitOperation(
                workspace, request.operationId, requestHash, refs, now
            )
            RecordAdministrationResult(administrationId, eventId, receipt)
        }
    }

    suspend fun attachAndMatchReport(
        workspace: CareWorkspace,
        request: AttachAndMatchReportRequest
    ): AttachAndMatchReportResult {
        if (request.status == CareEnums.DiagnosticReportStatus.AMENDED) {
            require(
                request.previousReportId != null ||
                    !request.missingPredecessorNote.isNullOrBlank()
            ) {
                "AMENDED requires predecessor or missing-predecessor note"
            }
        }
        val requestHash = CareProvenanceWriter.sha256(
            listOf(
                request.admissionId,
                request.fileName,
                request.filePath,
                request.fileType,
                request.kind.name,
                clinicalTimeConverters.fromClinicalTime(request.reportedAt).orEmpty(),
                request.status.name,
                request.previousReportId.orEmpty(),
                request.missingPredecessorNote.orEmpty(),
                request.narrative.orEmpty(),
                request.investigationOrderId.orEmpty()
            ).joinToString("|")
        )
        return database.withTransaction {
            provenance.beginOrReuse(workspace, request.operationId, requestHash)?.let { existing ->
                val refs = JSONObject(existing.resultReferences)
                return@withTransaction AttachAndMatchReportResult(
                    documentId = refs.getString("documentId"),
                    reportId = refs.getString("reportId"),
                    eventId = refs.getString("eventId"),
                    receipt = OperationReceipt(request.operationId, existing.resultReferences, true)
                )
            }
            val episode = careDao.getEpisode(request.admissionId) ?: error("Admission not found")
            require(episode.ownerAccountId == workspace.ownerAccountId)
            request.investigationOrderId?.let { orderId ->
                val order = therapyDao.getInvestigationOrder(orderId)
                    ?: error("Investigation order not found")
                require(order.admissionId == request.admissionId)
            }
            val now = System.currentTimeMillis()
            val documentId = CareIds.newId()
            val reportId = CareIds.newId()
            val eventId = provenance.writeEvent(
                workspace, episode.patientId, request.admissionId,
                "REPORT_ATTACHED", request.reportedAt, request.operationId, now = now
            )
            therapyDao.insertDocument(
                CareDocumentEntity(
                    id = documentId,
                    ownerAccountId = workspace.ownerAccountId,
                    admissionId = request.admissionId,
                    patientId = episode.patientId,
                    fileName = request.fileName,
                    filePath = request.filePath,
                    fileType = request.fileType,
                    attachedAt = now,
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )
            therapyDao.insertDiagnosticReport(
                CareDiagnosticReportEntity(
                    id = reportId,
                    ownerAccountId = workspace.ownerAccountId,
                    admissionId = request.admissionId,
                    kind = request.kind.name,
                    reportedAt = request.reportedAt,
                    status = request.status.name,
                    previousReportId = request.previousReportId,
                    missingPredecessorNote = request.missingPredecessorNote,
                    narrative = request.narrative,
                    eventId = eventId,
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )
            therapyDao.insertReportDocumentLink(
                CareReportDocumentLinkEntity(
                    id = CareIds.newId(),
                    ownerAccountId = workspace.ownerAccountId,
                    reportId = reportId,
                    documentId = documentId,
                    sequence = 1,
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )
            request.investigationOrderId?.let { orderId ->
                therapyDao.insertReportOrderLink(
                    CareReportOrderLinkEntity(
                        id = CareIds.newId(),
                        ownerAccountId = workspace.ownerAccountId,
                        reportId = reportId,
                        orderId = orderId,
                        createdAt = now,
                        createdBy = workspace.actorPersonId
                    )
                )
                val order = therapyDao.getInvestigationOrder(orderId)!!
                therapyDao.updateInvestigationOrder(
                    order.copy(
                        status = CareEnums.InvestigationOrderStatus.RESULT_AVAILABLE.name,
                        version = order.version + 1
                    )
                )
            }
            provenance.writeAudit(
                workspace, request.operationId, "CREATE", "DiagnosticReport",
                reportId, null, 1, now = now
            )
            val refs = JSONObject()
                .put("documentId", documentId)
                .put("reportId", reportId)
                .put("eventId", eventId)
                .toString()
            val receipt = provenance.commitOperation(
                workspace, request.operationId, requestHash, refs, now
            )
            AttachAndMatchReportResult(documentId, reportId, eventId, receipt)
        }
    }

    suspend fun recordReportReview(
        workspace: CareWorkspace,
        request: RecordReportReviewRequest
    ): String {
        val report = therapyDao.getDiagnosticReport(request.reportId) ?: error("Report not found")
        require(report.ownerAccountId == workspace.ownerAccountId)
        val episode = careDao.getEpisode(report.admissionId) ?: error("Admission not found")
        val now = System.currentTimeMillis()
        val requestHash = CareProvenanceWriter.sha256(
            listOf(
                request.reportId,
                request.outcome.name,
                request.decisionId.orEmpty(),
                request.note.orEmpty(),
                request.reviewedAt.toString()
            ).joinToString("|")
        )
        return database.withTransaction {
            provenance.beginOrReuse(workspace, request.operationId, requestHash)?.let { existing ->
                return@withTransaction JSONObject(existing.resultReferences).getString("reviewId")
            }
            val reviewId = CareIds.newId()
            val eventId = provenance.writeEvent(
                workspace, episode.patientId, report.admissionId,
                "REPORT_REVIEWED", ClinicalTime.instant(request.reviewedAt),
                request.operationId, now = now
            )
            therapyDao.insertReportReview(
                CareReportReviewEntity(
                    id = reviewId,
                    ownerAccountId = workspace.ownerAccountId,
                    reportId = request.reportId,
                    reviewerPersonId = workspace.actorPersonId,
                    reviewedAt = request.reviewedAt,
                    outcome = request.outcome.name,
                    decisionId = request.decisionId,
                    note = request.note,
                    eventId = eventId,
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )
            val refs = JSONObject().put("reviewId", reviewId).put("eventId", eventId).toString()
            provenance.commitOperation(workspace, request.operationId, requestHash, refs, now)
            reviewId
        }
    }

    private suspend fun resolveMedicationId(
        workspace: CareWorkspace,
        request: ChangeMedicationOrderRequest,
        now: Long
    ): String {
        request.medicationId?.let { id ->
            therapyDao.getMedicationDefinition(id)
                ?: error("Medication definition not found")
            return id
        }
        val name = request.medicationDisplayName?.takeIf { it.isNotBlank() }
            ?: error("medicationId or medicationDisplayName required")
        val id = CareIds.newId()
        therapyDao.insertMedicationDefinition(
            CareMedicationDefinitionEntity(
                id = id,
                ownerAccountId = workspace.ownerAccountId,
                displayName = name,
                createdAt = now,
                createdBy = workspace.actorPersonId
            )
        )
        return id
    }

    private fun validateOrderTransition(currentStatus: String, action: CareEnums.MedicationOrderAction) {
        when (action) {
            CareEnums.MedicationOrderAction.HOLD ->
                require(currentStatus == CareEnums.MedicationOrderStatus.ACTIVE.name) {
                    "Only ACTIVE orders can be held"
                }
            CareEnums.MedicationOrderAction.RESUME ->
                require(currentStatus == CareEnums.MedicationOrderStatus.HELD.name) {
                    "Only HELD orders can be resumed"
                }
            CareEnums.MedicationOrderAction.STOP,
            CareEnums.MedicationOrderAction.COMPLETE,
            CareEnums.MedicationOrderAction.CHANGE ->
                require(
                    currentStatus == CareEnums.MedicationOrderStatus.ACTIVE.name ||
                        currentStatus == CareEnums.MedicationOrderStatus.HELD.name
                ) {
                    "Illegal order transition"
                }
            CareEnums.MedicationOrderAction.START -> Unit
        }
    }
}
