package com.medtrack.app.hybrid.assistant

import com.medtrack.app.data.care.dao.CareWorkDao
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.hybrid.proposal.GroupCommitResult
import com.medtrack.app.hybrid.proposal.ProposalCommitService
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

data class TransmissionDraft(
    val channel: String,
    val status: String,
    val sent: Boolean,
    val message: String
)

/**
 * LLM result → typed action → validated command → receipt.
 * Transmission never reports Sent.
 */
@Singleton
class AssistantTurnPipeline @Inject constructor(
    private val commitService: ProposalCommitService,
    private val workDao: CareWorkDao
) {
    fun decode(raw: JSONObject): AssistantTurn = AssistantTurnDecoder.decode(raw)

    fun submitClarification(
        turn: AssistantTurn,
        questionId: String,
        questionDigest: String,
        answerKind: String,
        selectedRefs: JSONArray = JSONArray(),
        turnCountBefore: Int = 0
    ): ClarificationOutcome = AssistantActionPolicy.recordClarificationAnswer(
        turn, questionId, questionDigest, answerKind, selectedRefs, turnCountBefore
    )

    suspend fun approveProposal(
        bundle: JSONObject,
        narrative: String
    ): JSONObject {
        val digest = AssistantActionPolicy.prepareApproval(bundle, narrative)
        val results = commitService.commit(bundle, digest)
        return receiptCard(bundle.optString("proposalId"), digest, results)
    }

    suspend fun receiptCard(
        proposalId: String,
        approvedDigest: String,
        results: List<GroupCommitResult>
    ): JSONObject {
        val groups = JSONArray()
        results.forEach { result ->
            val effects = JSONArray()
            result.receipts.forEach { receipt ->
                val refs = runCatching { JSONObject(receipt.resultReferencesJson) }.getOrNull()
                val scheduleId = refs?.optString("scheduleId").orEmpty()
                if (scheduleId.isNotBlank()) {
                    val rows = workDao.outboxForSchedule(scheduleId)
                    val latest = rows.maxByOrNull { it.createdAt }
                    val status = when (latest?.state) {
                        CareEnums.SchedulingOutboxState.APPLIED.name -> "SCHEDULED"
                        CareEnums.SchedulingOutboxState.FAILED.name -> "FAILED"
                        CareEnums.SchedulingOutboxState.PENDING.name -> "PENDING"
                        else -> if (rows.isEmpty()) "PENDING" else "PENDING"
                    }
                    effects.put(
                        JSONObject()
                            .put("type", "REMINDER_SCHEDULE")
                            .put("status", status)
                            .put("ref", scheduleId)
                            .put("label", reminderLabel(status))
                    )
                }
            }
            groups.put(
                JSONObject()
                    .put("atomicGroupId", result.atomicGroupId)
                    .put("commitStatus", if (result.error == null) "SAVED" else "FAILED")
                    .put("error", result.error)
                    .put("effects", effects)
            )
        }
        return JSONObject()
            .put("cardKind", "RECEIPT")
            .put("proposalId", proposalId)
            .put("approvedDigest", approvedDigest)
            .put("derivedFromModel", false)
            .put("groupResults", groups)
    }

    fun draftTransmission(channel: String, content: String): TransmissionDraft {
        if (channel.isBlank() || content.isBlank()) {
            throw AssistantContractException("VALIDATION", "Transmission draft needs a channel and content.")
        }
        return TransmissionDraft(
            channel = channel,
            status = "DRAFT",
            sent = false,
            message = "Draft only. No channel worker is enabled, so nothing was sent."
        )
    }

    private fun reminderLabel(status: String): String = when (status) {
        "SCHEDULED" -> "Reminder scheduled"
        "FAILED" -> "Reminder scheduling failed"
        else -> "Saved; scheduling pending"
    }
}
