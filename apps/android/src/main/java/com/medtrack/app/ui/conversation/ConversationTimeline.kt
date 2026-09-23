package com.medtrack.app.ui.conversation

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class TimelineKind {
    DOCTOR,
    ASSISTANT,
    CLARIFICATION,
    ANSWER,
    REVISION,
    PROPOSAL,
    RECEIPT,
    JOB
}

data class PatientScope(
    val patientLabel: String?,
    val admissionId: String?,
    val locationLabel: String?
) {
    val chip: String
        get() = when {
            patientLabel != null && locationLabel != null -> "$patientLabel · $locationLabel"
            patientLabel != null -> patientLabel
            else -> "All patients"
        }

    companion object {
        val Global = PatientScope(null, null, null)
        fun selected(admissionId: String) = PatientScope(
            patientLabel = "Selected patient",
            admissionId = admissionId,
            locationLabel = "this admission"
        )
    }
}

data class TimelineItem(
    val id: String,
    val kind: TimelineKind,
    val text: String,
    val scope: PatientScope,
    val workflowId: String? = null,
    val expanded: Boolean = false,
    val invalidated: Boolean = false,
    val dependsOnItemId: String? = null,
    val options: List<String> = emptyList(),
    val selectedLabel: String? = null,
    val largeSelector: Boolean = false,
    val offlineSaved: Boolean = false,
    val waitingForConnection: Boolean = false,
    val receiptLabel: String? = null,
    val questionKey: String? = null
)

enum class ComposerIntent { NONE, CHOOSE }

data class ConversationState(
    val activeScope: PatientScope = PatientScope.Global,
    val items: List<TimelineItem> = emptyList(),
    val composer: String = "",
    val composerIntent: ComposerIntent = ComposerIntent.NONE,
    val pendingComposerText: String? = null,
    val selectorItemId: String? = null,
    val nearBottom: Boolean = true,
    val showNewResponse: Boolean = false,
    val online: Boolean = true,
    val activeWorkflowId: String? = null
) {
    val activeClarification: TimelineItem?
        get() = items.lastOrNull {
            it.kind == TimelineKind.CLARIFICATION && it.expanded && !it.invalidated
        }

    val composerEnabled: Boolean = true

    fun toJson(): String {
        val itemsJson = JSONArray()
        items.forEach { item ->
            itemsJson.put(
                JSONObject()
                    .put("id", item.id)
                    .put("kind", item.kind.name)
                    .put("text", item.text)
                    .put("patientLabel", item.scope.patientLabel ?: JSONObject.NULL)
                    .put("admissionId", item.scope.admissionId ?: JSONObject.NULL)
                    .put("locationLabel", item.scope.locationLabel ?: JSONObject.NULL)
                    .put("workflowId", item.workflowId ?: JSONObject.NULL)
                    .put("expanded", item.expanded)
                    .put("invalidated", item.invalidated)
                    .put("dependsOnItemId", item.dependsOnItemId ?: JSONObject.NULL)
                    .put("options", JSONArray(item.options))
                    .put("selectedLabel", item.selectedLabel ?: JSONObject.NULL)
                    .put("largeSelector", item.largeSelector)
                    .put("offlineSaved", item.offlineSaved)
                    .put("waitingForConnection", item.waitingForConnection)
                    .put("receiptLabel", item.receiptLabel ?: JSONObject.NULL)
                    .put("questionKey", item.questionKey ?: JSONObject.NULL)
            )
        }
        return JSONObject()
            .put("patientLabel", activeScope.patientLabel ?: JSONObject.NULL)
            .put("admissionId", activeScope.admissionId ?: JSONObject.NULL)
            .put("locationLabel", activeScope.locationLabel ?: JSONObject.NULL)
            .put("composer", composer)
            .put("online", online)
            .put("activeWorkflowId", activeWorkflowId ?: JSONObject.NULL)
            .put("items", itemsJson)
            .toString()
    }

    companion object {
        fun fromJson(raw: String): ConversationState {
            val json = JSONObject(raw)
            val itemsJson = json.optJSONArray("items") ?: JSONArray()
            val items = buildList {
                for (i in 0 until itemsJson.length()) {
                    val row = itemsJson.getJSONObject(i)
                    val optionsJson = row.optJSONArray("options") ?: JSONArray()
                    add(
                        TimelineItem(
                            id = row.getString("id"),
                            kind = TimelineKind.valueOf(row.getString("kind")),
                            text = row.optString("text"),
                            scope = PatientScope(
                                row.optString("patientLabel").ifBlank { null },
                                row.optString("admissionId").ifBlank { null },
                                row.optString("locationLabel").ifBlank { null }
                            ),
                            workflowId = row.optString("workflowId").ifBlank { null },
                            expanded = row.optBoolean("expanded"),
                            invalidated = row.optBoolean("invalidated"),
                            dependsOnItemId = row.optString("dependsOnItemId").ifBlank { null },
                            options = buildList {
                                for (j in 0 until optionsJson.length()) add(optionsJson.getString(j))
                            },
                            selectedLabel = row.optString("selectedLabel").ifBlank { null },
                            largeSelector = row.optBoolean("largeSelector"),
                            offlineSaved = row.optBoolean("offlineSaved"),
                            waitingForConnection = row.optBoolean("waitingForConnection"),
                            receiptLabel = row.optString("receiptLabel").ifBlank { null },
                            questionKey = row.optString("questionKey").ifBlank { null }
                        )
                    )
                }
            }
            return ConversationState(
                activeScope = PatientScope(
                    json.optString("patientLabel").ifBlank { null },
                    json.optString("admissionId").ifBlank { null },
                    json.optString("locationLabel").ifBlank { null }
                ),
                items = items,
                composer = json.optString("composer"),
                online = json.optBoolean("online", true),
                activeWorkflowId = json.optString("activeWorkflowId").ifBlank { null }
            )
        }
    }
}

class ConversationTimeline(
    initial: ConversationState = ConversationState()
) {
    var state: ConversationState = initial
        private set

    fun restore(snapshot: ConversationState) {
        state = snapshot
    }

    fun snapshot(): ConversationState = state

    fun setOnline(online: Boolean) {
        state = state.copy(online = online)
    }

    fun setNearBottom(nearBottom: Boolean) {
        state = state.copy(
            nearBottom = nearBottom,
            showNewResponse = if (nearBottom) false else state.showNewResponse
        )
    }

    fun jumpToLatest() {
        state = state.copy(nearBottom = true, showNewResponse = false)
    }

    fun onComposerChange(value: String) {
        state = state.copy(composer = value)
    }

    fun clearScope() {
        state = state.copy(activeScope = PatientScope.Global)
    }

    fun setScope(scope: PatientScope) {
        state = state.copy(activeScope = scope)
    }

    fun applyStarter(label: String) {
        submitNewMessage(label)
    }

    /** Send from the main composer. Never silently answers an open clarification. */
    fun sendFromComposer() {
        val text = state.composer.trim()
        if (text.isBlank()) return
        if (state.activeClarification != null) {
            state = state.copy(
                composerIntent = ComposerIntent.CHOOSE,
                pendingComposerText = text,
                composer = ""
            )
            return
        }
        state = state.copy(composer = "")
        submitNewMessage(text)
    }

    fun useComposerAsAnswer() {
        val text = state.pendingComposerText ?: return
        val card = state.activeClarification ?: return
        state = state.copy(composerIntent = ComposerIntent.NONE, pendingComposerText = null)
        answerCard(card.id, text)
    }

    fun sendComposerAsNewMessage() {
        val text = state.pendingComposerText ?: return
        state = state.copy(composerIntent = ComposerIntent.NONE, pendingComposerText = null, composer = "")
        submitNewMessage(text)
    }

    fun openSelector(itemId: String) {
        val item = state.items.firstOrNull { it.id == itemId } ?: return
        if (!item.largeSelector || item.invalidated || !item.expanded) return
        state = state.copy(selectorItemId = itemId)
    }

    fun closeSelector(selection: String?) {
        val itemId = state.selectorItemId
        state = state.copy(selectorItemId = null)
        if (itemId != null && selection != null) {
            answerCard(itemId, selection)
        }
    }

    fun answerCard(itemId: String, selection: String) {
        val card = state.items.firstOrNull { it.id == itemId } ?: return
        if (card.kind != TimelineKind.CLARIFICATION || !card.expanded || card.invalidated) return
        val answer = item(
            kind = TimelineKind.ANSWER,
            text = selection,
            scope = card.scope,
            workflowId = card.workflowId,
            dependsOnItemId = card.id,
            selectedLabel = selection
        )
        val collapsed = card.copy(expanded = false, selectedLabel = selection)
        state = state.copy(
            items = state.items.map { if (it.id == card.id) collapsed else it } + answer
        )
        appendFollowUp(card, selection)
        noteAppend()
    }

    fun changeAnswer(answerId: String) {
        val answer = state.items.firstOrNull { it.id == answerId && it.kind == TimelineKind.ANSWER } ?: return
        if (answer.invalidated) return
        val workflow = answer.workflowId ?: return
        val revision = item(
            kind = TimelineKind.REVISION,
            text = "Changed earlier answer. Previous: ${answer.selectedLabel ?: answer.text}",
            scope = answer.scope,
            workflowId = workflow,
            dependsOnItemId = answer.id
        )
        val invalidated = state.items.map { item ->
            val after = state.items.indexOfFirst { it.id == answer.id }
            val index = state.items.indexOfFirst { it.id == item.id }
            if (item.workflowId == workflow && index > after) item.copy(invalidated = true, expanded = false) else item
        }
        val source = state.items.firstOrNull { it.id == answer.dependsOnItemId }
        val reopened = item(
            kind = TimelineKind.CLARIFICATION,
            text = source?.text ?: "Please choose again.",
            scope = answer.scope,
            workflowId = workflow,
            expanded = true,
            options = source?.options ?: emptyList(),
            largeSelector = source?.largeSelector == true,
            questionKey = source?.questionKey
        )
        state = state.copy(
            items = invalidated + revision + reopened,
            activeWorkflowId = workflow
        )
        noteAppend()
    }

    fun approveProposal(itemId: String) {
        val proposal = state.items.firstOrNull { it.id == itemId && it.kind == TimelineKind.PROPOSAL } ?: return
        if (proposal.invalidated || !proposal.expanded) return
        val receipt = item(
            kind = TimelineKind.RECEIPT,
            text = "Saved locally",
            scope = proposal.scope,
            workflowId = proposal.workflowId,
            receiptLabel = "Saved; scheduling pending",
            dependsOnItemId = proposal.id
        )
        state = state.copy(
            items = state.items.map {
                if (it.id == proposal.id) it.copy(expanded = false, selectedLabel = "Approved") else it
            } + receipt,
            activeWorkflowId = null
        )
        noteAppend()
    }

    fun markReminderScheduled(receiptId: String) {
        state = state.copy(
            items = state.items.map {
                if (it.id == receiptId && it.kind == TimelineKind.RECEIPT) {
                    it.copy(receiptLabel = "Reminder scheduled")
                } else {
                    it
                }
            }
        )
    }

    fun discardProposal(itemId: String) {
        state = state.copy(
            items = state.items.map {
                if (it.id == itemId && it.kind == TimelineKind.PROPOSAL) {
                    it.copy(expanded = false, selectedLabel = "Discarded", invalidated = true)
                } else {
                    it
                }
            }
        )
    }

    private fun submitNewMessage(text: String) {
        val scope = state.activeScope
        val doctor = item(
            kind = TimelineKind.DOCTOR,
            text = text,
            scope = scope,
            offlineSaved = !state.online,
            waitingForConnection = !state.online
        )
        state = state.copy(items = state.items + doctor)
        when {
            text.contains("report", ignoreCase = true) -> appendJob(doctor.id, scope)
            isCrossPatient(text) -> appendCrossPatient(scope)
            scope.admissionId == null && needsPatient(text) -> openPatientQuestion(scope)
            text.contains("antibiotic", ignoreCase = true) || text.contains("stop", ignoreCase = true) ->
                openMedicationQuestion(scope)
            else -> {
                appendAssistant("Noted for ${scope.chip}.", scope, null)
                appendProposal("Record this update for review.", scope, workflowId())
            }
        }
        noteAppend()
    }

    private fun appendFollowUp(card: TimelineItem, selection: String) {
        val scope = if (card.questionKey == "patient") {
            PatientScope(selection, admissionId = "resolved", locationLabel = null)
        } else {
            card.scope
        }
        if (card.questionKey == "patient") {
            state = state.copy(activeScope = scope)
        }
        when (card.questionKey) {
            "patient" -> openMedicationQuestion(scope, card.workflowId)
            "medication" -> openTimeQuestion(scope, card.workflowId)
            "time" -> appendProposal(
                "Stop $selection and remind you at the chosen time.",
                scope,
                card.workflowId
            )
            else -> appendAssistant("Answer recorded.", scope, card.workflowId)
        }
    }

    private fun openPatientQuestion(scope: PatientScope) {
        val workflow = workflowId()
        appendAssistant("Which patient do you mean?", scope, workflow)
        appendClarification(
            prompt = "Which patient do you mean?",
            options = listOf("Mrs Anjali Rao — ICU, Bed 4", "Mrs Asha Rao — Ward 3B, Bed 12"),
            scope = scope,
            workflowId = workflow,
            large = true,
            questionKey = "patient"
        )
    }

    private fun openMedicationQuestion(scope: PatientScope, workflowId: String? = null) {
        val workflow = workflowId ?: state.activeWorkflowId ?: workflowId()
        appendAssistant("I found two active antibiotics.", scope, workflow)
        appendClarification(
            prompt = "Which medicine should be stopped?",
            options = listOf("Ceftriaxone — 1 g IV twice daily", "Metronidazole — 500 mg IV three times daily"),
            scope = scope,
            workflowId = workflow,
            large = false,
            questionKey = "medication"
        )
    }

    private fun openTimeQuestion(scope: PatientScope, workflowId: String?) {
        appendClarification(
            prompt = "When should I remind you?",
            options = listOf("30 min", "1 hour", "2 hours", "4 hours"),
            scope = scope,
            workflowId = workflowId,
            large = false,
            questionKey = "time"
        )
    }

    private fun appendCrossPatient(origin: PatientScope) {
        val rao = PatientScope("Mr Rao", "adm-rao", "ICU")
        val sharma = PatientScope("Mr Sharma", "adm-sharma", "Ward B")
        appendAssistant("These are two patients. Review each separately.", origin, null)
        appendProposal("Repeat potassium for Mr Rao.", rao, workflowId())
        appendProposal("Move Mr Sharma to ward B.", sharma, workflowId())
    }

    private fun appendJob(anchorId: String, scope: PatientScope) {
        val job = item(
            kind = TimelineKind.JOB,
            text = "Reading the report…",
            scope = scope,
            dependsOnItemId = anchorId,
            expanded = true
        )
        state = state.copy(items = state.items + job)
    }

    private fun appendProposal(text: String, scope: PatientScope, workflowId: String?) {
        val proposal = item(
            kind = TimelineKind.PROPOSAL,
            text = text,
            scope = scope,
            workflowId = workflowId,
            expanded = true
        )
        state = state.copy(items = state.items + proposal, activeWorkflowId = workflowId)
    }

    private fun appendAssistant(text: String, scope: PatientScope, workflowId: String?) {
        state = state.copy(
            items = state.items + item(TimelineKind.ASSISTANT, text, scope, workflowId),
            activeWorkflowId = workflowId ?: state.activeWorkflowId
        )
    }

    private fun appendClarification(
        prompt: String,
        options: List<String>,
        scope: PatientScope,
        workflowId: String?,
        large: Boolean,
        questionKey: String
    ) {
        val card = item(
            kind = TimelineKind.CLARIFICATION,
            text = prompt,
            scope = scope,
            workflowId = workflowId,
            expanded = true,
            options = options,
            largeSelector = large,
            questionKey = questionKey
        )
        state = state.copy(items = state.items + card, activeWorkflowId = workflowId)
    }

    private fun noteAppend() {
        if (!state.nearBottom) {
            state = state.copy(showNewResponse = true)
        }
    }

    private fun needsPatient(text: String): Boolean {
        val lower = text.lowercase()
        return listOf("her", "him", "patient", "antibiotic", "the ").any { it in lower }
    }

    private fun isCrossPatient(text: String): Boolean {
        val lower = text.lowercase()
        return "rao" in lower && "sharma" in lower
    }

    private fun workflowId(): String = "wf-" + UUID.randomUUID().toString().take(8)

    private fun item(
        kind: TimelineKind,
        text: String,
        scope: PatientScope,
        workflowId: String? = null,
        expanded: Boolean = false,
        dependsOnItemId: String? = null,
        options: List<String> = emptyList(),
        largeSelector: Boolean = false,
        questionKey: String? = null,
        offlineSaved: Boolean = false,
        waitingForConnection: Boolean = false,
        receiptLabel: String? = null,
        selectedLabel: String? = null
    ) = TimelineItem(
        id = "item-" + UUID.randomUUID().toString().take(8),
        kind = kind,
        text = text,
        scope = scope,
        workflowId = workflowId,
        expanded = expanded,
        dependsOnItemId = dependsOnItemId,
        options = options,
        largeSelector = largeSelector,
        questionKey = questionKey,
        offlineSaved = offlineSaved,
        waitingForConnection = waitingForConnection,
        receiptLabel = receiptLabel,
        selectedLabel = selectedLabel
    )
}
