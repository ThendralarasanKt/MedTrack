package com.medtrack.app.data.care.command

import androidx.room.withTransaction
import com.medtrack.app.data.care.dao.CareDao
import com.medtrack.app.data.care.entity.CareAllergyAssessmentEntity
import com.medtrack.app.data.care.entity.CareAllergyEntity
import com.medtrack.app.data.care.entity.CareClinicalDecisionEntity
import com.medtrack.app.data.care.entity.CareClinicalNoteEntity
import com.medtrack.app.data.care.entity.CareDecisionProblemLinkEntity
import com.medtrack.app.data.care.entity.CareEncounterEntity
import com.medtrack.app.data.care.entity.CareObservationEntity
import com.medtrack.app.data.care.entity.CarePlanRevisionEntity
import com.medtrack.app.data.care.entity.CareProblemEntity
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.CareIds
import com.medtrack.app.data.care.model.ClinicalTime
import com.medtrack.app.data.db.AppDatabase
import com.medtrack.app.data.db.converters.ClinicalTimeConverters
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

data class RecordEncounterRequest(
    val admissionId: String,
    val kind: CareEnums.EncounterKind = CareEnums.EncounterKind.ROUND,
    val reviewedAt: ClinicalTime,
    val summary: String,
    val noteText: String? = null,
    val noteKind: CareEnums.NoteKind = CareEnums.NoteKind.PROGRESS,
    val operationId: String = CareIds.newId()
)

data class RecordEncounterResult(
    val encounterId: String,
    val noteId: String?,
    val eventId: String,
    val receipt: OperationReceipt
)

data class RecordProblemRequest(
    val patientId: String,
    val admissionId: String? = null,
    val description: String,
    val codeSystem: String? = null,
    val code: String? = null,
    val certainty: CareEnums.ProblemCertainty = CareEnums.ProblemCertainty.SUSPECTED,
    val status: CareEnums.ProblemStatus = CareEnums.ProblemStatus.ACTIVE,
    val onset: ClinicalTime,
    val resolvedAt: ClinicalTime? = null,
    val operationId: String = CareIds.newId()
)

data class RecordProblemResult(
    val problemId: String,
    val eventId: String,
    val receipt: OperationReceipt
)

data class UpdateProblemRequest(
    val problemId: String,
    val certainty: CareEnums.ProblemCertainty? = null,
    val status: CareEnums.ProblemStatus? = null,
    val resolvedAt: ClinicalTime? = null,
    val description: String? = null,
    val operationId: String = CareIds.newId()
)

data class RecordAllergyRequest(
    val patientId: String,
    val substance: String,
    val reaction: String? = null,
    val severity: String? = null,
    val status: CareEnums.AllergyStatus = CareEnums.AllergyStatus.ACTIVE,
    val operationId: String = CareIds.newId()
)

data class RecordAllergyResult(
    val allergyId: String,
    val eventId: String,
    val receipt: OperationReceipt
)

data class RecordAllergyAssessmentRequest(
    val patientId: String,
    val result: CareEnums.AllergyAssessmentResult,
    val operationId: String = CareIds.newId()
)

data class RecordAllergyAssessmentResult(
    val assessmentId: String,
    val eventId: String,
    val receipt: OperationReceipt
)

data class RecordObservationRequest(
    val patientId: String,
    val admissionId: String? = null,
    val encounterId: String? = null,
    val name: String,
    val valueKind: CareEnums.ObservationValueKind,
    val numericValue: String? = null,
    val textValue: String? = null,
    val booleanValue: Boolean? = null,
    val unit: String? = null,
    val referenceRangeText: String? = null,
    val observedAt: ClinicalTime,
    val operationId: String = CareIds.newId()
)

data class RecordObservationResult(
    val observationId: String,
    val eventId: String,
    val receipt: OperationReceipt
)

data class CommitClinicalDecisionRequest(
    val admissionId: String,
    val encounterId: String? = null,
    val description: String,
    val rationale: String? = null,
    val decidedAt: ClinicalTime,
    val decisionMakerPersonId: String? = null,
    val linkedProblemIds: List<String> = emptyList(),
    val carePlanGoals: String? = null,
    val carePlanInstructions: String? = null,
    val carePlanEffectiveAt: ClinicalTime? = null,
    val operationId: String = CareIds.newId()
)

data class CommitClinicalDecisionResult(
    val decisionId: String,
    val planRevisionId: String?,
    val eventId: String,
    val receipt: OperationReceipt
)

/**
 * Clinical review / problem / allergy / observation / decision write path (PM-007).
 */
@Singleton
class CareClinicalAssessmentService @Inject constructor(
    private val database: AppDatabase,
    private val careDao: CareDao
) {
    private val clinicalTimeConverters = ClinicalTimeConverters()
    private val provenance = CareProvenanceWriter(careDao)

    suspend fun recordEncounter(
        workspace: CareWorkspace,
        request: RecordEncounterRequest
    ): RecordEncounterResult {
        val requestHash = CareProvenanceWriter.sha256(
            listOf(
                request.admissionId,
                request.kind.name,
                clinicalTimeConverters.fromClinicalTime(request.reviewedAt).orEmpty(),
                request.summary,
                request.noteText.orEmpty(),
                request.noteKind.name
            ).joinToString("|")
        )
        return database.withTransaction {
            provenance.beginOrReuse(workspace, request.operationId, requestHash)?.let { existing ->
                val refs = JSONObject(existing.resultReferences)
                return@withTransaction RecordEncounterResult(
                    encounterId = refs.getString("encounterId"),
                    noteId = refs.nullableString("noteId"),
                    eventId = refs.getString("eventId"),
                    receipt = reusedReceipt(request.operationId, existing.resultReferences)
                )
            }

            val episode = careDao.getEpisode(request.admissionId)
                ?: error("Admission not found")
            require(episode.ownerAccountId == workspace.ownerAccountId) {
                "Cross-account admission link rejected"
            }
            val now = System.currentTimeMillis()
            val encounterId = CareIds.newId()
            val eventId = provenance.writeEvent(
                workspace = workspace,
                patientId = episode.patientId,
                admissionId = request.admissionId,
                eventType = "ENCOUNTER_RECORDED",
                effectiveTime = request.reviewedAt,
                operationId = request.operationId,
                now = now
            )
            careDao.insertEncounter(
                CareEncounterEntity(
                    id = encounterId,
                    ownerAccountId = workspace.ownerAccountId,
                    admissionId = request.admissionId,
                    kind = request.kind.name,
                    reviewedAt = request.reviewedAt,
                    summary = request.summary,
                    eventId = eventId,
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )
            val noteId = request.noteText?.takeIf { it.isNotBlank() }?.let { text ->
                val id = CareIds.newId()
                careDao.insertClinicalNote(
                    CareClinicalNoteEntity(
                        id = id,
                        ownerAccountId = workspace.ownerAccountId,
                        patientId = episode.patientId,
                        admissionId = request.admissionId,
                        encounterId = encounterId,
                        kind = request.noteKind.name,
                        text = text,
                        eventId = eventId,
                        createdAt = now,
                        createdBy = workspace.actorPersonId
                    )
                )
                id
            }
            provenance.writeAudit(
                workspace = workspace,
                operationId = request.operationId,
                action = "CREATE",
                targetType = "Encounter",
                targetId = encounterId,
                priorVersion = null,
                newVersion = 1,
                now = now
            )
            val refs = JSONObject()
                .put("encounterId", encounterId)
                .put("noteId", noteId)
                .put("eventId", eventId)
                .toString()
            val receipt = provenance.commitOperation(
                workspace = workspace,
                operationId = request.operationId,
                requestHash = requestHash,
                resultReferencesJson = refs,
                now = now
            )
            RecordEncounterResult(encounterId, noteId, eventId, receipt)
        }
    }

    suspend fun recordProblem(
        workspace: CareWorkspace,
        request: RecordProblemRequest
    ): RecordProblemResult {
        require(request.code == null || !request.codeSystem.isNullOrBlank()) {
            "Problem code requires codeSystem"
        }
        if (request.status == CareEnums.ProblemStatus.RESOLVED) {
            require(request.resolvedAt != null) { "Resolved problem requires resolvedAt" }
        }
        val requestHash = CareProvenanceWriter.sha256(
            listOf(
                request.patientId,
                request.admissionId.orEmpty(),
                request.description,
                request.codeSystem.orEmpty(),
                request.code.orEmpty(),
                request.certainty.name,
                request.status.name,
                clinicalTimeConverters.fromClinicalTime(request.onset).orEmpty(),
                clinicalTimeConverters.fromClinicalTime(request.resolvedAt).orEmpty()
            ).joinToString("|")
        )
        return database.withTransaction {
            provenance.beginOrReuse(workspace, request.operationId, requestHash)?.let { existing ->
                val refs = JSONObject(existing.resultReferences)
                return@withTransaction RecordProblemResult(
                    problemId = refs.getString("problemId"),
                    eventId = refs.getString("eventId"),
                    receipt = reusedReceipt(request.operationId, existing.resultReferences)
                )
            }
            val patient = careDao.getPatient(request.patientId) ?: error("Patient not found")
            require(patient.ownerAccountId == workspace.ownerAccountId)
            request.admissionId?.let { admissionId ->
                val episode = careDao.getEpisode(admissionId) ?: error("Admission not found")
                require(episode.patientId == request.patientId)
                require(episode.ownerAccountId == workspace.ownerAccountId)
            }
            val now = System.currentTimeMillis()
            val problemId = CareIds.newId()
            val eventId = provenance.writeEvent(
                workspace = workspace,
                patientId = request.patientId,
                admissionId = request.admissionId,
                eventType = "PROBLEM_RECORDED",
                effectiveTime = request.onset,
                operationId = request.operationId,
                now = now
            )
            careDao.insertProblem(
                CareProblemEntity(
                    id = problemId,
                    ownerAccountId = workspace.ownerAccountId,
                    patientId = request.patientId,
                    admissionId = request.admissionId,
                    description = request.description,
                    codeSystem = request.codeSystem,
                    code = request.code,
                    certainty = request.certainty.name,
                    status = request.status.name,
                    onset = request.onset,
                    resolvedAt = request.resolvedAt,
                    eventId = eventId,
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )
            provenance.writeAudit(
                workspace = workspace,
                operationId = request.operationId,
                action = "CREATE",
                targetType = "Problem",
                targetId = problemId,
                priorVersion = null,
                newVersion = 1,
                now = now
            )
            provenance.writeRevision(
                workspace = workspace,
                recordType = "Problem",
                recordId = problemId,
                recordVersion = 1,
                payloadSnapshot = problemSnapshot(
                    description = request.description,
                    certainty = request.certainty.name,
                    status = request.status.name
                ),
                eventId = eventId,
                now = now
            )
            val refs = JSONObject()
                .put("problemId", problemId)
                .put("eventId", eventId)
                .toString()
            val receipt = provenance.commitOperation(
                workspace, request.operationId, requestHash, refs, now
            )
            RecordProblemResult(problemId, eventId, receipt)
        }
    }

    suspend fun updateProblem(
        workspace: CareWorkspace,
        request: UpdateProblemRequest
    ): RecordProblemResult {
        val requestHash = CareProvenanceWriter.sha256(
            listOf(
                request.problemId,
                request.certainty?.name.orEmpty(),
                request.status?.name.orEmpty(),
                request.description.orEmpty(),
                clinicalTimeConverters.fromClinicalTime(request.resolvedAt).orEmpty()
            ).joinToString("|")
        )
        return database.withTransaction {
            provenance.beginOrReuse(workspace, request.operationId, requestHash)?.let { existing ->
                val refs = JSONObject(existing.resultReferences)
                return@withTransaction RecordProblemResult(
                    problemId = refs.getString("problemId"),
                    eventId = refs.getString("eventId"),
                    receipt = reusedReceipt(request.operationId, existing.resultReferences)
                )
            }
            val existing = careDao.getProblem(request.problemId) ?: error("Problem not found")
            require(existing.ownerAccountId == workspace.ownerAccountId)
            val status = request.status?.name ?: existing.status
            val resolvedAt = when {
                status == CareEnums.ProblemStatus.RESOLVED.name ->
                    request.resolvedAt ?: existing.resolvedAt
                        ?: error("Resolved problem requires resolvedAt")
                else -> request.resolvedAt ?: existing.resolvedAt
            }
            val now = System.currentTimeMillis()
            val nextVersion = existing.version + 1
            val eventId = provenance.writeEvent(
                workspace = workspace,
                patientId = existing.patientId,
                admissionId = existing.admissionId,
                eventType = "PROBLEM_UPDATED",
                effectiveTime = resolvedAt ?: ClinicalTime.instant(now),
                operationId = request.operationId,
                now = now
            )
            val updated = existing.copy(
                certainty = request.certainty?.name ?: existing.certainty,
                status = status,
                resolvedAt = if (status == CareEnums.ProblemStatus.ACTIVE.name) null else resolvedAt,
                description = request.description ?: existing.description,
                eventId = eventId,
                version = nextVersion
            )
            careDao.updateProblem(updated)
            provenance.writeAudit(
                workspace = workspace,
                operationId = request.operationId,
                action = "UPDATE",
                targetType = "Problem",
                targetId = existing.id,
                priorVersion = existing.version,
                newVersion = nextVersion,
                now = now
            )
            provenance.writeRevision(
                workspace = workspace,
                recordType = "Problem",
                recordId = existing.id,
                recordVersion = nextVersion,
                payloadSnapshot = problemSnapshot(
                    description = updated.description,
                    certainty = updated.certainty,
                    status = updated.status
                ),
                eventId = eventId,
                now = now
            )
            val refs = JSONObject()
                .put("problemId", existing.id)
                .put("eventId", eventId)
                .toString()
            val receipt = provenance.commitOperation(
                workspace, request.operationId, requestHash, refs, now
            )
            RecordProblemResult(existing.id, eventId, receipt)
        }
    }

    suspend fun recordAllergy(
        workspace: CareWorkspace,
        request: RecordAllergyRequest
    ): RecordAllergyResult {
        require(request.substance.isNotBlank()) { "Allergy substance required" }
        val requestHash = CareProvenanceWriter.sha256(
            listOf(
                request.patientId,
                request.substance,
                request.reaction.orEmpty(),
                request.severity.orEmpty(),
                request.status.name
            ).joinToString("|")
        )
        return database.withTransaction {
            provenance.beginOrReuse(workspace, request.operationId, requestHash)?.let { existing ->
                val refs = JSONObject(existing.resultReferences)
                return@withTransaction RecordAllergyResult(
                    allergyId = refs.getString("allergyId"),
                    eventId = refs.getString("eventId"),
                    receipt = reusedReceipt(request.operationId, existing.resultReferences)
                )
            }
            val patient = careDao.getPatient(request.patientId) ?: error("Patient not found")
            require(patient.ownerAccountId == workspace.ownerAccountId)
            val now = System.currentTimeMillis()
            val allergyId = CareIds.newId()
            val eventId = provenance.writeEvent(
                workspace = workspace,
                patientId = request.patientId,
                admissionId = null,
                eventType = "ALLERGY_RECORDED",
                effectiveTime = ClinicalTime.instant(now),
                operationId = request.operationId,
                now = now
            )
            careDao.insertAllergy(
                CareAllergyEntity(
                    id = allergyId,
                    ownerAccountId = workspace.ownerAccountId,
                    patientId = request.patientId,
                    substance = request.substance,
                    reaction = request.reaction,
                    severity = request.severity,
                    status = request.status.name,
                    eventId = eventId,
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )
            provenance.writeAudit(
                workspace = workspace,
                operationId = request.operationId,
                action = "CREATE",
                targetType = "Allergy",
                targetId = allergyId,
                priorVersion = null,
                newVersion = 1,
                now = now
            )
            val refs = JSONObject()
                .put("allergyId", allergyId)
                .put("eventId", eventId)
                .toString()
            val receipt = provenance.commitOperation(
                workspace, request.operationId, requestHash, refs, now
            )
            RecordAllergyResult(allergyId, eventId, receipt)
        }
    }

    suspend fun recordAllergyAssessment(
        workspace: CareWorkspace,
        request: RecordAllergyAssessmentRequest
    ): RecordAllergyAssessmentResult {
        val requestHash = CareProvenanceWriter.sha256(
            "${request.patientId}|${request.result.name}"
        )
        return database.withTransaction {
            provenance.beginOrReuse(workspace, request.operationId, requestHash)?.let { existing ->
                val refs = JSONObject(existing.resultReferences)
                return@withTransaction RecordAllergyAssessmentResult(
                    assessmentId = refs.getString("assessmentId"),
                    eventId = refs.getString("eventId"),
                    receipt = reusedReceipt(request.operationId, existing.resultReferences)
                )
            }
            val patient = careDao.getPatient(request.patientId) ?: error("Patient not found")
            require(patient.ownerAccountId == workspace.ownerAccountId)
            val now = System.currentTimeMillis()
            val assessmentId = CareIds.newId()
            val eventId = provenance.writeEvent(
                workspace = workspace,
                patientId = request.patientId,
                admissionId = null,
                eventType = "ALLERGY_ASSESSMENT_RECORDED",
                effectiveTime = ClinicalTime.instant(now),
                operationId = request.operationId,
                now = now
            )
            careDao.insertAllergyAssessment(
                CareAllergyAssessmentEntity(
                    id = assessmentId,
                    ownerAccountId = workspace.ownerAccountId,
                    patientId = request.patientId,
                    result = request.result.name,
                    eventId = eventId,
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )
            provenance.writeAudit(
                workspace = workspace,
                operationId = request.operationId,
                action = "CREATE",
                targetType = "AllergyAssessment",
                targetId = assessmentId,
                priorVersion = null,
                newVersion = 1,
                now = now
            )
            val refs = JSONObject()
                .put("assessmentId", assessmentId)
                .put("eventId", eventId)
                .toString()
            val receipt = provenance.commitOperation(
                workspace, request.operationId, requestHash, refs, now
            )
            RecordAllergyAssessmentResult(assessmentId, eventId, receipt)
        }
    }

    suspend fun recordObservation(
        workspace: CareWorkspace,
        request: RecordObservationRequest
    ): RecordObservationResult {
        validateObservationValue(request)
        val requestHash = CareProvenanceWriter.sha256(
            listOf(
                request.patientId,
                request.admissionId.orEmpty(),
                request.encounterId.orEmpty(),
                request.name,
                request.valueKind.name,
                request.numericValue.orEmpty(),
                request.textValue.orEmpty(),
                request.booleanValue?.toString().orEmpty(),
                request.unit.orEmpty(),
                request.referenceRangeText.orEmpty(),
                clinicalTimeConverters.fromClinicalTime(request.observedAt).orEmpty()
            ).joinToString("|")
        )
        return database.withTransaction {
            provenance.beginOrReuse(workspace, request.operationId, requestHash)?.let { existing ->
                val refs = JSONObject(existing.resultReferences)
                return@withTransaction RecordObservationResult(
                    observationId = refs.getString("observationId"),
                    eventId = refs.getString("eventId"),
                    receipt = reusedReceipt(request.operationId, existing.resultReferences)
                )
            }
            val patient = careDao.getPatient(request.patientId) ?: error("Patient not found")
            require(patient.ownerAccountId == workspace.ownerAccountId)
            request.encounterId?.let { encounterId ->
                val encounter = careDao.getEncounter(encounterId) ?: error("Encounter not found")
                require(encounter.ownerAccountId == workspace.ownerAccountId)
            }
            val now = System.currentTimeMillis()
            val observationId = CareIds.newId()
            val eventId = provenance.writeEvent(
                workspace = workspace,
                patientId = request.patientId,
                admissionId = request.admissionId,
                eventType = "OBSERVATION_RECORDED",
                effectiveTime = request.observedAt,
                operationId = request.operationId,
                now = now
            )
            careDao.insertObservation(
                CareObservationEntity(
                    id = observationId,
                    ownerAccountId = workspace.ownerAccountId,
                    patientId = request.patientId,
                    admissionId = request.admissionId,
                    encounterId = request.encounterId,
                    name = request.name,
                    valueKind = request.valueKind.name,
                    numericValue = request.numericValue,
                    textValue = request.textValue,
                    booleanValue = request.booleanValue,
                    unit = request.unit,
                    referenceRangeText = request.referenceRangeText,
                    observedAt = request.observedAt,
                    eventId = eventId,
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )
            provenance.writeAudit(
                workspace = workspace,
                operationId = request.operationId,
                action = "CREATE",
                targetType = "Observation",
                targetId = observationId,
                priorVersion = null,
                newVersion = 1,
                now = now
            )
            val refs = JSONObject()
                .put("observationId", observationId)
                .put("eventId", eventId)
                .toString()
            val receipt = provenance.commitOperation(
                workspace, request.operationId, requestHash, refs, now
            )
            RecordObservationResult(observationId, eventId, receipt)
        }
    }

    suspend fun commitClinicalDecision(
        workspace: CareWorkspace,
        request: CommitClinicalDecisionRequest
    ): CommitClinicalDecisionResult {
        val requestHash = CareProvenanceWriter.sha256(
            listOf(
                request.admissionId,
                request.encounterId.orEmpty(),
                request.description,
                request.rationale.orEmpty(),
                clinicalTimeConverters.fromClinicalTime(request.decidedAt).orEmpty(),
                request.decisionMakerPersonId.orEmpty(),
                request.linkedProblemIds.sorted().joinToString(","),
                request.carePlanGoals.orEmpty(),
                request.carePlanInstructions.orEmpty(),
                clinicalTimeConverters.fromClinicalTime(request.carePlanEffectiveAt).orEmpty()
            ).joinToString("|")
        )
        return database.withTransaction {
            provenance.beginOrReuse(workspace, request.operationId, requestHash)?.let { existing ->
                val refs = JSONObject(existing.resultReferences)
                return@withTransaction CommitClinicalDecisionResult(
                    decisionId = refs.getString("decisionId"),
                    planRevisionId = refs.nullableString("planRevisionId"),
                    eventId = refs.getString("eventId"),
                    receipt = reusedReceipt(request.operationId, existing.resultReferences)
                )
            }
            val episode = careDao.getEpisode(request.admissionId) ?: error("Admission not found")
            require(episode.ownerAccountId == workspace.ownerAccountId)
            request.encounterId?.let { encounterId ->
                val encounter = careDao.getEncounter(encounterId) ?: error("Encounter not found")
                require(encounter.admissionId == request.admissionId)
            }
            val now = System.currentTimeMillis()
            val decisionId = CareIds.newId()
            val eventId = provenance.writeEvent(
                workspace = workspace,
                patientId = episode.patientId,
                admissionId = request.admissionId,
                eventType = "CLINICAL_DECISION_COMMITTED",
                effectiveTime = request.decidedAt,
                operationId = request.operationId,
                now = now
            )
            // Decision-maker is explicit when known; never defaulted to recorder.
            request.decisionMakerPersonId?.let { makerId ->
                provenance.addParticipant(
                    workspace = workspace,
                    eventId = eventId,
                    personId = makerId,
                    role = "DECISION_MAKER",
                    now = now
                )
            }
            careDao.insertClinicalDecision(
                CareClinicalDecisionEntity(
                    id = decisionId,
                    ownerAccountId = workspace.ownerAccountId,
                    admissionId = request.admissionId,
                    encounterId = request.encounterId,
                    description = request.description,
                    rationale = request.rationale,
                    decidedAt = request.decidedAt,
                    status = CareEnums.DecisionStatus.ACTIVE.name,
                    eventId = eventId,
                    createdAt = now,
                    createdBy = workspace.actorPersonId
                )
            )
            for (problemId in request.linkedProblemIds.distinct()) {
                val problem = careDao.getProblem(problemId) ?: error("Problem not found: $problemId")
                require(problem.patientId == episode.patientId) {
                    "DecisionProblemLink requires same patient"
                }
                careDao.insertDecisionProblemLink(
                    CareDecisionProblemLinkEntity(
                        id = CareIds.newId(),
                        ownerAccountId = workspace.ownerAccountId,
                        decisionId = decisionId,
                        problemId = problemId,
                        createdAt = now,
                        createdBy = workspace.actorPersonId
                    )
                )
            }
            val planRevisionId = if (
                !request.carePlanGoals.isNullOrBlank() ||
                !request.carePlanInstructions.isNullOrBlank()
            ) {
                val goals = request.carePlanGoals.orEmpty()
                val instructions = request.carePlanInstructions.orEmpty()
                val effectiveAt = request.carePlanEffectiveAt ?: request.decidedAt
                val previous = careDao.planRevisionsForAdmission(request.admissionId).lastOrNull()
                val revisionId = CareIds.newId()
                careDao.insertCarePlanRevision(
                    CarePlanRevisionEntity(
                        id = revisionId,
                        ownerAccountId = workspace.ownerAccountId,
                        admissionId = request.admissionId,
                        decisionId = decisionId,
                        goals = goals,
                        instructions = instructions,
                        effectiveAt = effectiveAt,
                        previousRevisionId = previous?.id,
                        eventId = eventId,
                        createdAt = now,
                        createdBy = workspace.actorPersonId
                    )
                )
                revisionId
            } else {
                null
            }
            provenance.writeAudit(
                workspace = workspace,
                operationId = request.operationId,
                action = "CREATE",
                targetType = "ClinicalDecision",
                targetId = decisionId,
                priorVersion = null,
                newVersion = 1,
                now = now
            )
            val refs = JSONObject()
                .put("decisionId", decisionId)
                .put("planRevisionId", planRevisionId)
                .put("eventId", eventId)
                .put("linkedProblemIds", JSONArray(request.linkedProblemIds))
                .toString()
            val receipt = provenance.commitOperation(
                workspace, request.operationId, requestHash, refs, now
            )
            CommitClinicalDecisionResult(decisionId, planRevisionId, eventId, receipt)
        }
    }

    private fun validateObservationValue(request: RecordObservationRequest) {
        val hasNumeric = request.numericValue != null
        val hasText = request.textValue != null
        val hasBoolean = request.booleanValue != null
        val count = listOf(hasNumeric, hasText, hasBoolean).count { it }
        require(count == 1) { "Observation requires exactly one typed value" }
        when (request.valueKind) {
            CareEnums.ObservationValueKind.NUMBER ->
                require(hasNumeric) { "NUMBER observation requires numericValue" }
            CareEnums.ObservationValueKind.TEXT ->
                require(hasText) { "TEXT observation requires textValue" }
            CareEnums.ObservationValueKind.BOOLEAN ->
                require(hasBoolean) { "BOOLEAN observation requires booleanValue" }
        }
    }

    private fun problemSnapshot(description: String, certainty: String, status: String): String =
        JSONObject()
            .put("description", description)
            .put("certainty", certainty)
            .put("status", status)
            .toString()

    private fun reusedReceipt(operationId: String, refs: String) =
        OperationReceipt(
            operationId = operationId,
            resultReferencesJson = refs,
            reused = true
        )

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key).takeIf { it.isNotBlank() }
}
