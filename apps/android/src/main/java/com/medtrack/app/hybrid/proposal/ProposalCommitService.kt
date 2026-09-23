package com.medtrack.app.hybrid.proposal

import androidx.room.withTransaction
import com.medtrack.app.data.care.command.AssignTaskRequest
import com.medtrack.app.data.care.command.CareWritePath
import com.medtrack.app.data.care.command.ChangeMedicationOrderRequest
import com.medtrack.app.data.care.command.OperationReceipt
import com.medtrack.app.data.care.command.RecordEncounterRequest
import com.medtrack.app.data.care.command.RecordObservationRequest
import com.medtrack.app.data.care.command.RecordProblemRequest
import com.medtrack.app.data.care.command.RecordTransferRequest
import com.medtrack.app.data.care.command.RespondToTaskRequest
import com.medtrack.app.data.care.command.StaleRecordException
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.CareIds
import com.medtrack.app.data.care.model.ClinicalTime
import com.medtrack.app.data.db.AppDatabase
import com.medtrack.app.hybrid.account.AccountSession
import com.medtrack.app.hybrid.contract.ProposalContract
import com.medtrack.app.hybrid.data.HybridDao
import com.medtrack.app.hybrid.data.HybridProposalEntity
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

data class GroupCommitResult(
    val atomicGroupId: String,
    val receipts: List<OperationReceipt>,
    val error: String? = null
)

class ProposalCommitException(message: String) : IllegalStateException(message)

@Singleton
class ProposalCommitService @Inject constructor(
    private val database: AppDatabase,
    private val writePath: CareWritePath,
    private val hybridDao: HybridDao,
    private val accountSession: AccountSession
) {
    suspend fun persistUncommitted(bundle: JSONObject, requestId: String, jobId: String?) {
        val owner = accountSession.ownerAccountId()
        val bound = accountSession.active()
        val now = System.currentTimeMillis()
        hybridDao.upsertProposal(
            HybridProposalEntity(
                id = bundle.optString("proposalId").ifBlank { CareIds.newId() },
                ownerAccountId = owner,
                accountId = bound?.accountId ?: owner,
                deviceId = accountSession.deviceId(),
                proposalId = bundle.optString("proposalId"),
                jobId = jobId,
                requestId = requestId,
                status = "PENDING_REVIEW",
                contextDigest = bundle.optString("contextDigest"),
                payloadDigest = ProposalContract.payloadDigest(bundle),
                bundleJson = bundle.toString(),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun discard(proposalId: String) {
        val row = hybridDao.getProposal(proposalId) ?: return
        hybridDao.upsertProposal(
            row.copy(status = "DISCARDED", updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun commit(bundle: JSONObject, approvedDigest: String): List<GroupCommitResult> {
        if (ProposalContract.payloadDigest(bundle) != approvedDigest) {
            throw ProposalCommitException("Approval is bound to a different payload digest.")
        }
        if (!ProposalContract.isCommitReady(bundle)) {
            throw ProposalCommitException("Proposal is not ready to commit.")
        }
        val identity = bundle.getJSONObject("identity")
        val admissionId = identity.optString("admissionId")
        val patientId = identity.optString("patientId")
        val owner = accountSession.ownerAccountId()
        val stored = hybridDao.getProposal(bundle.optString("proposalId"))
        if (stored != null && stored.ownerAccountId != owner) {
            throw ProposalCommitException("Proposal belongs to a different local account.")
        }
        if (stored?.status == "COMMITTED") {
            return storedGroupResults(JSONObject(stored.bundleJson))
        }
        if (stored?.status == "DISCARDED") {
            throw ProposalCommitException("Discarded proposals cannot be committed.")
        }
        val operations = bundle.getJSONArray("operations")
        val groups = linkedMapOf<String, MutableList<JSONObject>>()
        val opToGroup = linkedMapOf<String, String>()
        for (i in 0 until operations.length()) {
            val op = operations.getJSONObject(i)
            val groupId = op.optString("atomicGroupId").ifBlank { op.getString("operationId") }
            groups.getOrPut(groupId) { mutableListOf() }.add(op)
            opToGroup[op.getString("operationId")] = groupId
        }
        val groupDeps = groups.mapValues { (_, ops) ->
            ops.flatMap { op ->
                val deps = op.optJSONArray("dependsOn") ?: JSONArray()
                buildList {
                    for (i in 0 until deps.length()) {
                        val depOp = deps.getString(i)
                        val depGroup = opToGroup[depOp] ?: depOp
                        if (depGroup != opToGroup[op.getString("operationId")]) add(depGroup)
                    }
                }
            }.toSet()
        }
        detectCycles(groupDeps)
        val prior = stored?.let { storedGroupResults(JSONObject(it.bundleJson)) }.orEmpty()
            .associateBy { it.atomicGroupId }
        val ordered = topo(groups.keys.toList(), groupDeps)
        val results = mutableListOf<GroupCommitResult>()
        val failed = mutableSetOf<String>()
        ordered.forEach { groupId ->
            val previous = prior[groupId]
            if (previous != null && previous.error == null && previous.receipts.isNotEmpty()) {
                results += previous
                return@forEach
            }
            val blockedBy = groupDeps[groupId].orEmpty().intersect(failed)
            if (blockedBy.isNotEmpty()) {
                val skipped = GroupCommitResult(
                    groupId,
                    emptyList(),
                    "Prerequisite group ${blockedBy.first()} failed."
                )
                results += skipped
                failed += groupId
                return@forEach
            }
            val result = commitGroup(groupId, groups.getValue(groupId), admissionId, patientId)
            results += result
            if (result.error != null) failed += groupId
        }
        val anySuccess = results.any { it.error == null }
        val anyFail = results.any { it.error != null }
        val status = when {
            results.isEmpty() -> "FAILED"
            !anyFail -> "COMMITTED"
            anySuccess -> "PARTIALLY_COMMITTED"
            else -> "NEEDS_REVIEW"
        }
        val updatedBundle = JSONObject(bundle.toString()).put("groupResults", encodeGroupResults(results))
        stored?.let {
            hybridDao.upsertProposal(
                it.copy(
                    status = status,
                    bundleJson = updatedBundle.toString(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        return results
    }

    private fun storedGroupResults(bundle: JSONObject): List<GroupCommitResult> {
        val array = bundle.optJSONArray("groupResults") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val row = array.getJSONObject(i)
                val receiptsJson = row.optJSONArray("receipts") ?: JSONArray()
                val receipts = buildList {
                    for (j in 0 until receiptsJson.length()) {
                        val item = receiptsJson.getJSONObject(j)
                        add(
                            OperationReceipt(
                                item.optString("operationId"),
                                item.optString("resultReferencesJson"),
                                item.optBoolean("reused")
                            )
                        )
                    }
                }
                add(
                    GroupCommitResult(
                        atomicGroupId = row.getString("atomicGroupId"),
                        receipts = receipts,
                        error = row.optString("error").ifBlank { null }
                    )
                )
            }
        }
    }

    private fun encodeGroupResults(results: List<GroupCommitResult>): JSONArray {
        val array = JSONArray()
        results.forEach { result ->
            val receipts = JSONArray()
            result.receipts.forEach { receipt ->
                receipts.put(
                    JSONObject()
                        .put("operationId", receipt.operationId)
                        .put("resultReferencesJson", receipt.resultReferencesJson)
                        .put("reused", receipt.reused)
                )
            }
            array.put(
                JSONObject()
                    .put("atomicGroupId", result.atomicGroupId)
                    .put("error", result.error)
                    .put("receipts", receipts)
            )
        }
        return array
    }

    private fun detectCycles(deps: Map<String, Set<String>>) {
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun walk(node: String) {
            if (node in visited) return
            if (node in visiting) {
                throw ProposalCommitException("Proposal has cyclic group dependencies.")
            }
            visiting += node
            deps[node].orEmpty().forEach(::walk)
            visiting -= node
            visited += node
        }
        deps.keys.forEach(::walk)
    }

    private fun topo(nodes: List<String>, deps: Map<String, Set<String>>): List<String> {
        val remaining = nodes.toMutableList()
        val ordered = mutableListOf<String>()
        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { node -> deps[node].orEmpty().all { it in ordered || it !in remaining } }
            if (ready.isEmpty()) {
                throw ProposalCommitException("Proposal has cyclic group dependencies.")
            }
            ordered += ready
            remaining.removeAll(ready)
        }
        return ordered
    }

    private suspend fun commitGroup(
        groupId: String,
        ops: List<JSONObject>,
        defaultAdmissionId: String,
        patientId: String
    ): GroupCommitResult {
        val workspace = accountSession.workspace()
        return try {
            val receipts = database.withTransaction {
                ops.map { op -> dispatch(op, defaultAdmissionId, patientId, workspace) }
            }
            GroupCommitResult(groupId, receipts)
        } catch (stale: StaleRecordException) {
            GroupCommitResult(groupId, emptyList(), stale.message)
        } catch (error: Exception) {
            GroupCommitResult(groupId, emptyList(), error.message ?: "Commit failed")
        }
    }

    private suspend fun dispatch(
        op: JSONObject,
        defaultAdmissionId: String,
        patientId: String,
        workspace: com.medtrack.app.data.care.command.CareWorkspace
    ): OperationReceipt {
        val type = op.getString("type")
        val target = op.getJSONObject("target")
        val fields = op.optJSONObject("fields") ?: JSONObject()
        val admissionId = fields.optString("admissionId").ifBlank { defaultAdmissionId }
        if (identityMismatch(fields, patientId, admissionId, defaultAdmissionId)) {
            throw ProposalCommitException("Operation target does not match the reviewed patient or admission.")
        }
        val operationId = op.optString("operationId").ifBlank { CareIds.newId() }
        val effective = parseTime(op.optJSONObject("effectiveTime"))
        val expectedVersion = requiredExpectedVersion(type, target)
        return when (type) {
            "TRANSFER" -> writePath.admissions.recordTransfer(
                workspace,
                RecordTransferRequest(
                    admissionId = admissionId,
                    effectiveAt = effective,
                    locationId = target.getString("id"),
                    expectedLocationAssignmentVersion = expectedVersion,
                    operationId = operationId
                )
            ).receipt
            "MEDICATION_STOP" -> writePath.therapy.changeMedicationOrder(
                workspace,
                ChangeMedicationOrderRequest(
                    admissionId = admissionId,
                    orderId = target.getString("id"),
                    action = CareEnums.MedicationOrderAction.STOP,
                    expectedOrderVersion = expectedVersion,
                    operationId = operationId
                )
            ).receipt
            "MEDICATION_START" -> writePath.therapy.changeMedicationOrder(
                workspace,
                ChangeMedicationOrderRequest(
                    admissionId = admissionId,
                    medicationDisplayName = fields.optString("medicationDisplayName").ifBlank { null },
                    action = CareEnums.MedicationOrderAction.START,
                    orderedAt = effective,
                    doseValue = fields.optString("doseValue").ifBlank { null },
                    doseUnit = fields.optString("doseUnit").ifBlank { null },
                    doseText = fields.optString("doseText").ifBlank { null },
                    route = fields.optString("route").ifBlank { null },
                    schedule = com.medtrack.app.data.care.model.Regimen.textOnly(
                        fields.optString("regimen").ifBlank {
                            fields.optString("schedule").ifBlank { "as directed" }
                        }
                    ),
                    startsAt = effective,
                    reason = fields.optString("reason").ifBlank { null },
                    operationId = operationId
                )
            ).receipt
            "ASSIGN_TASK" -> {
                val due = relativeMillis(op.optJSONObject("effectiveTime"))
                val taskId = writePath.work.assignTask(
                    workspace,
                    AssignTaskRequest(
                        admissionId = admissionId,
                        kind = runCatching {
                            CareEnums.CareTaskKind.valueOf(fields.optString("kind", "OTHER"))
                        }.getOrDefault(CareEnums.CareTaskKind.OTHER),
                        title = fields.optString("title", "Follow-up"),
                        followUpOwnerPersonId = workspace.actorPersonId,
                        dueAt = due,
                        dueZoneId = due?.let {
                            op.optJSONObject("effectiveTime")?.optJSONObject("relative")
                                ?.optString("zoneId")
                                ?: op.optJSONObject("effectiveTime")?.optString("zoneId")
                        },
                        scheduleReminder = due != null,
                        operationId = operationId
                    )
                )
                val refs = JSONObject().put("taskId", taskId)
                val scheduleId = writePath.work.scheduleIdForTask(taskId)
                if (scheduleId != null) refs.put("scheduleId", scheduleId)
                OperationReceipt(operationId, refs.toString(), false)
            }
            "RESPOND_TASK" -> {
                val responseId = writePath.work.respondToTask(
                    workspace,
                    RespondToTaskRequest(
                        taskId = target.getString("id"),
                        action = CareEnums.TaskResponseAction.valueOf(fields.optString("action", "COMPLETE")),
                        note = fields.optString("note").ifBlank { null },
                        expectedTaskVersion = expectedVersion,
                        operationId = operationId
                    )
                )
                OperationReceipt(operationId, JSONObject().put("responseId", responseId).toString(), false)
            }
            "RECORD_PROBLEM" -> writePath.clinical.recordProblem(
                workspace,
                RecordProblemRequest(
                    patientId = fields.optString("patientId").ifBlank { patientId },
                    admissionId = admissionId,
                    description = fields.optString("displayName", fields.optString("description", "Problem")),
                    onset = effective,
                    operationId = operationId
                )
            ).receipt
            "RECORD_ENCOUNTER" -> writePath.clinical.recordEncounter(
                workspace,
                RecordEncounterRequest(
                    admissionId = admissionId,
                    reviewedAt = effective,
                    summary = fields.optString("noteText", "Review"),
                    noteText = fields.optString("noteText").ifBlank { null },
                    operationId = operationId
                )
            ).receipt
            "RECORD_OBSERVATION" -> writePath.clinical.recordObservation(
                workspace,
                RecordObservationRequest(
                    patientId = fields.optString("patientId").ifBlank { patientId },
                    admissionId = admissionId,
                    name = fields.optString("name", "observation"),
                    valueKind = CareEnums.ObservationValueKind.TEXT,
                    numericValue = fields.optString("numericValue").ifBlank { null },
                    textValue = fields.optString("valueText").ifBlank { fields.optString("textValue") },
                    unit = fields.optString("unit").ifBlank { null },
                    observedAt = effective,
                    operationId = operationId
                )
            ).receipt
            else -> throw ProposalCommitException("Unsupported command $type")
        }
    }

    private fun requiredExpectedVersion(type: String, target: JSONObject): Int? {
        val required = type in setOf("TRANSFER", "MEDICATION_STOP", "RESPOND_TASK")
        if (!required) return null
        if (!target.has("expectedVersion") || target.isNull("expectedVersion")) {
            throw ProposalCommitException("$type requires expectedVersion for the existing record.")
        }
        return target.getInt("expectedVersion")
    }

    private fun identityMismatch(
        fields: JSONObject,
        reviewedPatientId: String,
        admissionId: String,
        reviewedAdmissionId: String
    ): Boolean {
        val fieldPatient = fields.optString("patientId")
        if (fieldPatient.isNotBlank() && reviewedPatientId.isNotBlank() && fieldPatient != reviewedPatientId) {
            return true
        }
        return reviewedAdmissionId.isNotBlank() &&
            admissionId.isNotBlank() &&
            admissionId != reviewedAdmissionId
    }

    private fun parseTime(json: JSONObject?): ClinicalTime {
        if (json == null) {
            return ClinicalTime.unknown(note = "effective time was not supplied")
        }
        if (json.optBoolean("now") || json.optString("precision").equals("NOW", ignoreCase = true)) {
            val captured = json.optLong("capturedAt")
            val zone = json.optString("zoneId").ifBlank { "Asia/Kolkata" }
            return if (captured > 0) {
                ClinicalTime.instant(captured, zone)
            } else {
                ClinicalTime.unknown(note = "now was requested without an anchored capture time")
            }
        }
        val relative = json.optJSONObject("relative")
        val iso = relative?.optString("resolvedAt")?.ifBlank { null } ?: json.optString("at").ifBlank { null }
        val zone = relative?.optString("zoneId")?.ifBlank { null } ?: json.optString("zoneId").ifBlank { "Asia/Kolkata" }
        if (iso == null) {
            return ClinicalTime.unknown(
                originalText = json.optString("originalText").ifBlank { null },
                note = "effective time was not supplied"
            )
        }
        val millis = runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
        return if (millis == null) {
            ClinicalTime.unknown(originalText = iso, note = "effective time could not be parsed")
        } else {
            ClinicalTime.instant(millis, zone)
        }
    }

    private fun relativeMillis(json: JSONObject?): Long? {
        val iso = json?.optJSONObject("relative")?.optString("resolvedAt")?.ifBlank { null }
            ?: json?.optString("at")?.ifBlank { null }
            ?: return null
        return runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
    }
}
