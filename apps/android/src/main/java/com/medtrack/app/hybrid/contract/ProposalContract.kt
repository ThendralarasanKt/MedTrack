package com.medtrack.app.hybrid.contract

import org.json.JSONArray
import org.json.JSONObject

class ProposalValidationException(val code: String, message: String) : IllegalArgumentException(message)

object ProposalContract {
    const val COMMAND_SCHEMA = "care-commands-1"

    fun payloadDigest(bundle: JSONObject): String {
        val canonical = JSONObject()
            .put("proposalId", bundle.optString("proposalId"))
            .put("commandSchemaVersion", bundle.optString("commandSchemaVersion"))
            .put("contextDigest", bundle.optString("contextDigest"))
            .put("identity", bundle.optJSONObject("identity"))
            .put("operations", bundle.optJSONArray("operations"))
            .put("summary", bundle.optString("summary"))
        return sha256(canonical.toString())
    }

    fun validateForMutation(bundle: JSONObject) {
        if (bundle.optString("commandSchemaVersion") != COMMAND_SCHEMA) {
            throw ProposalValidationException("UNSUPPORTED_SCHEMA", "Unsupported command schema.")
        }
        val operations = bundle.optJSONArray("operations") ?: JSONArray()
        for (i in 0 until operations.length()) {
            validateOperation(operations.getJSONObject(i))
        }
        rejectStatVersusDelay(operations)
    }

    fun isCommitReady(bundle: JSONObject): Boolean {
        val identity = bundle.optJSONObject("identity") ?: return false
        if (identity.optString("state") != "RESOLVED") return false
        val operations = bundle.optJSONArray("operations") ?: return false
        if (operations.length() == 0) return false
        for (i in 0 until operations.length()) {
            val op = operations.getJSONObject(i)
            if ((op.optJSONArray("unresolvedFields")?.length() ?: 0) > 0) return false
        }
        validateForMutation(bundle)
        return true
    }

    private fun validateOperation(op: JSONObject) {
        val type = op.optString("type")
        val target = op.optJSONObject("target") ?: JSONObject()
        val rawId = if (target.has("id") && !target.isNull("id")) target.optString("id") else ""
        val targetId = rawId.takeUnless { it.isBlank() || it == "null" }
        val hint = if (target.has("displayHint") && !target.isNull("displayHint")) {
            target.optString("displayHint").takeUnless { it.isBlank() || it == "null" }
        } else {
            null
        }
        val unresolved = mutableSetOf<String>()
        val unresolvedJson = op.optJSONArray("unresolvedFields")
        if (unresolvedJson != null) {
            for (i in 0 until unresolvedJson.length()) unresolved += unresolvedJson.getString(i)
        }
        when (type) {
            "TRANSFER" -> {
                if (target.optString("kind") != "LOCATION" || targetId == null) {
                    if (hint != null) {
                        throw ProposalValidationException(
                            "VALIDATION",
                            "Transfer requires locationId; a bed label is not sufficient authority."
                        )
                    }
                    throw ProposalValidationException("VALIDATION", "Transfer requires a location target id.")
                }
                if (!target.has("expectedVersion") || target.isNull("expectedVersion")) {
                    throw ProposalValidationException("VALIDATION", "Transfer requires expectedVersion.")
                }
            }
            "MEDICATION_STOP" -> {
                if (target.optString("kind") != "MEDICATION_ORDER" || targetId == null) {
                    if (hint != null) {
                        throw ProposalValidationException(
                            "VALIDATION",
                            "Medication stop requires medicationOrderId; a drug name is not sufficient authority."
                        )
                    }
                    throw ProposalValidationException("VALIDATION", "Medication stop requires a medication order id.")
                }
                if (!target.has("expectedVersion") || target.isNull("expectedVersion")) {
                    throw ProposalValidationException("VALIDATION", "Medication stop requires expectedVersion.")
                }
            }
            "RESPOND_TASK" -> {
                if (!target.has("expectedVersion") || target.isNull("expectedVersion")) {
                    throw ProposalValidationException("VALIDATION", "Task response requires expectedVersion.")
                }
            }
        }
        val relative = op.optJSONObject("effectiveTime")?.optJSONObject("relative")
        if (relative != null) {
            listOf("anchor", "resolvedAt", "zoneId").forEach { field ->
                if (relative.optString(field).isBlank()) {
                    throw ProposalValidationException("VALIDATION", "Relative reminder time requires $field.")
                }
            }
        }
    }

    private fun rejectStatVersusDelay(operations: JSONArray) {
        val byFocus = linkedMapOf<String, MutableList<JSONObject>>()
        for (i in 0 until operations.length()) {
            val op = operations.getJSONObject(i)
            val fields = op.optJSONObject("fields") ?: JSONObject()
            val focus = fields.optString("clinicalFocus").ifBlank { fields.optString("title") }.lowercase()
            if (focus.isNotBlank()) {
                byFocus.getOrPut(focus) { mutableListOf() }.add(op)
            }
        }
        byFocus.forEach { (_, ops) ->
            val hasStat = ops.any { it.optJSONObject("fields")?.optString("priority") == "STAT" }
            val delayed = ops.filter { op ->
                val amount = op.optJSONObject("effectiveTime")
                    ?.optJSONObject("relative")
                    ?.optInt("amount", 0) ?: 0
                amount >= 1
            }
            if (hasStat && delayed.isNotEmpty()) {
                val statIds = ops.filter {
                    it.optJSONObject("fields")?.optString("priority") == "STAT"
                }.map { it.optString("operationId") }.toSet()
                delayed.forEach { op ->
                    val deps = op.optJSONArray("dependsOn")
                    val depIds = buildSet {
                        if (deps != null) for (i in 0 until deps.length()) add(deps.getString(i))
                    }
                    if (depIds.intersect(statIds).isEmpty()) {
                        throw ProposalValidationException(
                            "VALIDATION",
                            "STAT test versus a delayed follow-up for the same focus fails consistency review."
                        )
                    }
                }
            }
        }
    }

    fun sha256(value: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return "sha256:" + digest.joinToString("") { "%02x".format(it) }
    }
}
