package com.medtrack.app.hybrid.assistant

import org.json.JSONArray
import org.json.JSONObject

class AssistantContractException(val code: String, message: String) : IllegalArgumentException(message)

/**
 * Closed registries for assistant-turn-v1. Unknown kinds fail closed.
 */
object AssistantRegistries {
    const val ASSISTANT_SCHEMA = "assistant-turn-v1"
    const val CLARIFICATION_SCHEMA = "clarification-card-v1"
    const val COMMAND_SCHEMA = "care-commands-1"
    const val MAX_CLARIFICATION_TURNS = 5

    val RESULT_KINDS = setOf(
        "CLARIFICATION",
        "INFORMATION",
        "PROPOSAL",
        "TRANSMISSION_PROPOSAL",
        "RECEIPT",
        "MANUAL_REQUIRED",
        "ERROR"
    )

    val RESPONSE_TYPES = setOf(
        "PATIENT_SELECTION",
        "ADMISSION_SELECTION",
        "LOCATION_SELECTION",
        "MEDICATION_ORDER_SELECTION",
        "MEDICATION_DETAILS",
        "PERSON_SELECTION",
        "DATE_TIME",
        "DURATION",
        "SINGLE_CHOICE",
        "MULTI_CHOICE",
        "QUANTITY_UNIT",
        "ATTRIBUTION",
        "SHORT_TEXT",
        "DOCUMENT_IDENTITY"
    )

    /** Operation type → local command handler name. Unknown types fail closed. */
    val OPERATION_COMMANDS = mapOf(
        "TRANSFER" to "AdmissionTransferCommand",
        "MEDICATION_START" to "MedicationOrderCommand",
        "MEDICATION_STOP" to "MedicationOrderCommand",
        "ASSIGN_TASK" to "WorkTaskCommand",
        "RESPOND_TASK" to "TaskResponseCommand",
        "RECORD_PROBLEM" to "ClinicalRecordCommand",
        "RECORD_ENCOUNTER" to "ClinicalRecordCommand",
        "RECORD_OBSERVATION" to "ClinicalRecordCommand"
    )

    val CARD_ACTIONS = mapOf(
        "CLARIFICATION" to setOf("SUBMIT_ANSWER", "CANCEL_WORKFLOW"),
        "INFORMATION" to setOf("ACKNOWLEDGE", "ASK_FOLLOW_UP"),
        "PROPOSAL_GROUP" to setOf("APPROVE_GROUP", "EDIT_GROUP", "DISCARD_GROUP"),
        "TRANSMISSION_PROPOSAL" to setOf("APPROVE_SEND", "EDIT_SEND", "DISCARD_SEND"),
        "RECEIPT" to setOf("OPEN_RECORD", "RETRY_EFFECT"),
        "MANUAL_REQUIRED" to setOf("OPEN_MANUAL_FLOW", "CANCEL_WORKFLOW"),
        "ERROR" to setOf("RETRY", "EDIT_INPUT", "OPEN_MANUAL_FLOW")
    )

    val CANDIDATE_QUERIES = setOf(
        "ACTIVE_PATIENTS",
        "ADMISSIONS_FOR_PATIENT",
        "CURRENT_OR_AVAILABLE_LOCATIONS",
        "ACTIVE_MEDICATION_ORDERS_FOR_ADMISSION",
        "PEOPLE_INVOLVED_IN_ADMISSION",
        "TASKS_FOR_ADMISSION",
        "DOCUMENT_PATIENT_MATCH_CANDIDATES"
    )

    fun commandFor(operationType: String): String =
        OPERATION_COMMANDS[operationType]
            ?: throw AssistantContractException("UNSUPPORTED_OPERATION", "No command binding for $operationType.")
}

data class AssistantCard(
    val cardId: String,
    val cardKind: String,
    val allowedActions: List<String>,
    val payload: JSONObject
)

data class AssistantTurn(
    val resultKind: String,
    val workflowId: String,
    val turnId: String,
    val requestId: String,
    val contextDigest: String,
    val planVersion: Int,
    val narrativeText: String,
    val cards: List<AssistantCard>,
    val raw: JSONObject
)

object AssistantTurnDecoder {
    fun decode(raw: JSONObject): AssistantTurn {
        val schema = raw.optString("assistantSchemaVersion")
        if (schema != AssistantRegistries.ASSISTANT_SCHEMA) {
            throw AssistantContractException("UNSUPPORTED_SCHEMA", "Unsupported assistant schema $schema.")
        }
        val resultKind = raw.optString("resultKind")
        if (resultKind !in AssistantRegistries.RESULT_KINDS) {
            throw AssistantContractException("UNSUPPORTED_RESULT", "Unknown result kind $resultKind.")
        }
        requireBinding(raw, "workflowId")
        requireBinding(raw, "turnId")
        requireBinding(raw, "requestId")
        requireBinding(raw, "contextDigest")
        if (!raw.has("planVersion")) {
            throw AssistantContractException("VALIDATION", "planVersion is required.")
        }
        val cardsJson = raw.optJSONArray("cards") ?: JSONArray()
        val cards = buildList {
            for (i in 0 until cardsJson.length()) {
                add(decodeCard(cardsJson.getJSONObject(i), resultKind))
            }
        }
        if (cards.isEmpty() && resultKind in setOf("CLARIFICATION", "PROPOSAL", "TRANSMISSION_PROPOSAL", "RECEIPT")) {
            throw AssistantContractException("VALIDATION", "$resultKind requires at least one card.")
        }
        return AssistantTurn(
            resultKind = resultKind,
            workflowId = raw.getString("workflowId"),
            turnId = raw.getString("turnId"),
            requestId = raw.getString("requestId"),
            contextDigest = raw.getString("contextDigest"),
            planVersion = raw.getInt("planVersion"),
            narrativeText = raw.optJSONObject("narrative")?.optString("text").orEmpty(),
            cards = cards,
            raw = raw
        )
    }

    private fun decodeCard(card: JSONObject, resultKind: String): AssistantCard {
        val kind = card.optString("cardKind")
        val allowed = AssistantRegistries.CARD_ACTIONS[kind]
            ?: throw AssistantContractException("UNSUPPORTED_CARD", "Unknown card kind $kind.")
        if (resultKind == "CLARIFICATION" && kind != "CLARIFICATION" && kind != "ERROR") {
            throw AssistantContractException("VALIDATION", "Clarification turns cannot embed $kind cards.")
        }
        val actionsJson = card.optJSONArray("allowedActions") ?: JSONArray()
        val actions = buildList {
            for (i in 0 until actionsJson.length()) {
                val action = actionsJson.getString(i)
                if (action !in allowed) {
                    throw AssistantContractException(
                        "UNSUPPORTED_ACTION",
                        "Action $action is not valid for $kind."
                    )
                }
                add(action)
            }
        }
        if (kind == "CLARIFICATION") {
            val payload = card.optJSONObject("payload") ?: JSONObject()
            val responseType = payload.optString("responseType").ifBlank { card.optString("responseType") }
            if (responseType !in AssistantRegistries.RESPONSE_TYPES) {
                throw AssistantContractException(
                    "UNSUPPORTED_RESPONSE_TYPE",
                    "Unknown clarification response type $responseType."
                )
            }
            val query = payload.optJSONObject("candidateQuery")
            if (query != null) {
                val queryType = query.optString("queryType")
                if (queryType !in AssistantRegistries.CANDIDATE_QUERIES) {
                    throw AssistantContractException("UNSUPPORTED_QUERY", "Unknown candidate query $queryType.")
                }
                if (query.has("sql") || query.has("predicate") || query.has("url")) {
                    throw AssistantContractException("FORBIDDEN", "Candidate queries cannot carry executable filters.")
                }
            }
        }
        return AssistantCard(
            cardId = card.optString("cardId"),
            cardKind = kind,
            allowedActions = actions,
            payload = card.optJSONObject("payload") ?: JSONObject()
        )
    }

    private fun requireBinding(raw: JSONObject, field: String) {
        if (raw.optString(field).isBlank()) {
            throw AssistantContractException("VALIDATION", "$field is required.")
        }
    }
}
