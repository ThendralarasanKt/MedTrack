package com.medtrack.app.hybrid.assistant

import com.medtrack.app.hybrid.contract.ProposalContract
import org.json.JSONArray
import org.json.JSONObject

/**
 * Guards between typed results and clinical commits.
 * Clarification answers are recorded here and never invoke CareWritePath.
 */
object AssistantActionPolicy {
    private val OPERATION_WORDS = listOf(
        "TRANSFER" to listOf("transfer"),
        "MEDICATION_STOP" to listOf("stop ", "discontinue", "cease"),
        "MEDICATION_START" to listOf("start ", "prescribe", "commence"),
        "ASSIGN_TASK" to listOf("remind", "task", "follow up", "follow-up"),
        "RECORD_OBSERVATION" to listOf("record observation", "observation")
    )

    fun recordClarificationAnswer(
        turn: AssistantTurn,
        questionId: String,
        questionDigest: String,
        answerKind: String,
        selectedRefs: JSONArray,
        turnCountBefore: Int
    ): ClarificationOutcome {
        if (turn.resultKind != "CLARIFICATION") {
            throw AssistantContractException("VALIDATION", "Answers can only be recorded on clarification turns.")
        }
        val card = turn.cards.firstOrNull { it.cardKind == "CLARIFICATION" }
            ?: throw AssistantContractException("VALIDATION", "Clarification card is missing.")
        if ("SUBMIT_ANSWER" !in card.allowedActions) {
            throw AssistantContractException("UNSUPPORTED_ACTION", "This card cannot submit an answer.")
        }
        val expectedQuestion = card.payload.optString("questionId")
        if (expectedQuestion.isNotBlank() && expectedQuestion != questionId) {
            throw AssistantContractException("VALIDATION", "Answer is not bound to the active question.")
        }
        val expectedDigest = card.payload.optString("questionDigest")
        if (expectedDigest.isNotBlank() && expectedDigest != questionDigest) {
            throw AssistantContractException("STALE_QUESTION", "Question digest does not match.")
        }
        if (turnCountBefore >= AssistantRegistries.MAX_CLARIFICATION_TURNS) {
            return ClarificationOutcome(
                clinicalWrite = false,
                nextResultKind = "MANUAL_REQUIRED",
                message = "Clarification did not converge. Continue in the manual workflow."
            )
        }
        if (answerKind.isBlank()) {
            throw AssistantContractException("VALIDATION", "answerKind is required.")
        }
        return ClarificationOutcome(
            clinicalWrite = false,
            nextResultKind = "CLARIFICATION",
            message = "Answer recorded. Interpretation continues; no clinical record was written.",
            answer = JSONObject()
                .put("questionId", questionId)
                .put("questionDigest", questionDigest)
                .put("answerKind", answerKind)
                .put("selectedRefs", selectedRefs)
                .put("workflowId", turn.workflowId)
        )
    }

    /**
     * Narrative may explain typed operations. It must not introduce an action
     * that the operation list does not contain.
     */
    fun assertNarrativeMatchesOperations(narrative: String, operations: JSONArray) {
        val present = buildSet {
            for (i in 0 until operations.length()) {
                add(operations.getJSONObject(i).optString("type"))
            }
        }
        val lower = narrative.lowercase()
        OPERATION_WORDS.forEach { (type, words) ->
            if (type !in present && words.any { it in lower }) {
                throw AssistantContractException(
                    "NARRATIVE_MISMATCH",
                    "Narrative claims $type but the typed operations do not include it."
                )
            }
        }
    }

    fun prepareApproval(bundle: JSONObject, narrative: String): String {
        val operations = bundle.optJSONArray("operations") ?: JSONArray()
        for (i in 0 until operations.length()) {
            val type = operations.getJSONObject(i).optString("type")
            AssistantRegistries.commandFor(type)
        }
        assertNarrativeMatchesOperations(narrative, operations)
        ProposalContract.validateForMutation(bundle)
        if (!ProposalContract.isCommitReady(bundle)) {
            throw AssistantContractException("VALIDATION", "Proposal is not ready to approve.")
        }
        return ProposalContract.payloadDigest(bundle)
    }
}

data class ClarificationOutcome(
    val clinicalWrite: Boolean,
    val nextResultKind: String,
    val message: String,
    val answer: JSONObject? = null
)
