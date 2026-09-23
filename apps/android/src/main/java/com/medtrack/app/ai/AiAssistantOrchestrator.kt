package com.medtrack.app.ai

import com.medtrack.app.hybrid.contract.ProposalContract
import com.medtrack.app.hybrid.proposal.ProposalCommitService
import com.medtrack.app.hybrid.request.ContextSelector
import com.medtrack.app.hybrid.request.InferenceRequestRepository
import com.medtrack.app.hybrid.request.InferenceUiState
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

data class AiConversationMessage(
    val text: String,
    val fromUser: Boolean
)

data class StagedCareProposal(
    val toolCallId: String,
    val toolName: String,
    val argumentsJson: String,
    val summary: String,
    val bundleJson: String? = null,
    val payloadDigest: String? = null,
    val atomicGroupId: String = toolCallId,
    val operationIds: List<String> = emptyList(),
    val dependsOnGroupIds: List<String> = emptyList(),
    val patientId: String? = null,
    val admissionId: String? = null
)

data class AiParseResult(
    val narrative: String,
    val proposals: List<StagedCareProposal>,
    val uiState: InferenceUiState = InferenceUiState.AWAITING_REVIEW
)

@Singleton
class AiAssistantOrchestrator @Inject constructor(
    private val requests: InferenceRequestRepository,
    private val contextSelector: ContextSelector,
    private val commitService: ProposalCommitService
) {
    suspend fun parseUserMessage(
        message: String,
        conversation: List<AiConversationMessage> = emptyList(),
        admissionId: String? = null
    ): AiParseResult {
        val context = if (admissionId.isNullOrBlank()) {
            contextSelector.unresolved()
        } else {
            runCatching { contextSelector.forAdmission(admissionId) }
                .getOrElse { contextSelector.unresolved() }
        }
        val capture = requests.submit(message, context)
        val proposal = capture.proposalJson
        if (proposal != null) {
            commitService.persistUncommitted(proposal, capture.request.requestId, capture.jobId)
            val identity = proposal.optJSONObject("identity")
            val operations = proposal.optJSONArray("operations") ?: JSONArray()
            val groups = linkedMapOf<String, MutableList<JSONObject>>()
            val opToGroup = linkedMapOf<String, String>()
            for (i in 0 until operations.length()) {
                val op = operations.getJSONObject(i)
                val groupId = op.optString("atomicGroupId").ifBlank { op.optString("operationId") }
                groups.getOrPut(groupId) { mutableListOf() }.add(op)
                opToGroup[op.optString("operationId")] = groupId
            }
            val staged = groups.map { (groupId, ops) ->
                val depends = ops.flatMap { op ->
                    val deps = op.optJSONArray("dependsOn") ?: JSONArray()
                    buildList {
                        for (i in 0 until deps.length()) {
                            val depGroup = opToGroup[deps.getString(i)] ?: continue
                            if (depGroup != groupId) add(depGroup)
                        }
                    }
                }.distinct()
                StagedCareProposal(
                    toolCallId = groupId,
                    toolName = ops.joinToString(" + ") { it.optString("type") },
                    argumentsJson = JSONArray().also { array -> ops.forEach { array.put(it) } }.toString(),
                    summary = summarizeGroup(identity, ops),
                    bundleJson = proposal.toString(),
                    payloadDigest = ProposalContract.payloadDigest(proposal),
                    atomicGroupId = groupId,
                    operationIds = ops.map { it.optString("operationId") },
                    dependsOnGroupIds = depends,
                    patientId = identity?.optString("patientId")?.ifBlank { null },
                    admissionId = identity?.optString("admissionId")?.ifBlank { null }
                )
            }
            val narrative = capture.narrative.ifBlank { proposal.optString("summary") }
            return AiParseResult(narrative, staged, capture.uiState)
        }
        return AiParseResult(capture.narrative, emptyList(), capture.uiState)
    }

    suspend fun commitProposals(proposals: List<StagedCareProposal>): String {
        if (proposals.isEmpty()) return "Nothing to commit."
        val bundleJson = proposals.first().bundleJson
            ?: return "Proposal is not in the typed hybrid format."
        val original = JSONObject(bundleJson)
        val selectedGroups = proposals.map { it.atomicGroupId }.toSet()
        val selectedOps = proposals.flatMap { it.operationIds }.toSet()
        proposals.forEach { group ->
            if (group.dependsOnGroupIds.any { it !in selectedGroups }) {
                error("Cannot commit a dependent group without its prerequisite.")
            }
        }
        val operations = original.optJSONArray("operations") ?: JSONArray()
        val kept = JSONArray()
        val groupMembers = linkedMapOf<String, MutableList<String>>()
        for (i in 0 until operations.length()) {
            val op = operations.getJSONObject(i)
            val groupId = op.optString("atomicGroupId").ifBlank { op.optString("operationId") }
            groupMembers.getOrPut(groupId) { mutableListOf() }.add(op.optString("operationId"))
            if (groupId in selectedGroups) kept.put(op)
        }
        groupMembers.forEach { (groupId, members) ->
            if (groupId in selectedGroups && members.any { it !in selectedOps }) {
                error("Atomic groups must be selected as complete units.")
            }
        }
        val bundle = JSONObject(original.toString()).put("operations", kept)
        val digest = ProposalContract.payloadDigest(bundle)
        val results = commitService.commit(bundle, digest)
        val failed = results.filter { it.error != null }
        val saved = results.filter { it.error == null }.flatMap { it.receipts }
        if (failed.isNotEmpty() && saved.isEmpty()) {
            error(failed.first().error ?: "Commit failed. Nothing further was written.")
        }
        val scheduled = saved.any { it.resultReferencesJson.contains("taskId") }
        return buildString {
            append("Saved ${saved.size} local command(s).")
            if (scheduled) append(" Reminder scheduling was queued locally.")
            failed.forEach { append(" Group ${it.atomicGroupId} needs review: ${it.error}") }
        }
    }

    suspend fun discardProposal(proposals: List<StagedCareProposal>) {
        val proposalId = proposals.firstNotNullOfOrNull { staged ->
            staged.bundleJson?.let { JSONObject(it).optString("proposalId") }?.ifBlank { null }
        } ?: return
        commitService.discard(proposalId)
    }

    private fun summarizeGroup(identity: JSONObject?, ops: List<JSONObject>): String {
        val who = listOfNotNull(
            identity?.optString("patientId")?.ifBlank { null }?.let { "patient $it" },
            identity?.optString("admissionId")?.ifBlank { null }?.let { "admission $it" }
        ).joinToString(", ")
        val lines = ops.map { summarize(it) }
        return (listOfNotNull(who.ifBlank { null }) + lines).joinToString("\n")
    }

    private fun summarize(op: JSONObject): String {
        val type = op.optString("type")
        val target = op.optJSONObject("target") ?: JSONObject()
        val fields = op.optJSONObject("fields") ?: JSONObject()
        val time = op.optJSONObject("effectiveTime")
        val unresolved = op.optJSONArray("unresolvedFields")
        val parts = mutableListOf(type.replace('_', ' '))
        target.optString("displayHint").ifBlank { null }?.let { parts += it }
        target.optString("id").ifBlank { null }?.let { parts += "id $it" }
        fields.optString("medicationDisplayName").ifBlank { null }?.let { parts += it }
        fields.optString("doseText").ifBlank { null }?.let { parts += it }
        fields.optString("route").ifBlank { null }?.let { parts += "route $it" }
        fields.optString("regimen").ifBlank { fields.optString("schedule") }.ifBlank { null }?.let { parts += it }
        fields.optString("name").ifBlank { null }?.let { parts += it }
        fields.optString("valueText").ifBlank { fields.optString("textValue") }.ifBlank { null }?.let { parts += it }
        fields.optString("unit").ifBlank { null }?.let { parts += it }
        time?.optString("at")?.ifBlank { null }?.let { parts += "at $it" }
            ?: time?.optJSONObject("relative")?.optString("resolvedAt")?.ifBlank { null }?.let { parts += "at $it" }
            ?: parts.add("time unknown")
        if (unresolved != null && unresolved.length() > 0) {
            parts += "unresolved: $unresolved"
        }
        return parts.joinToString(" · ")
    }
}
