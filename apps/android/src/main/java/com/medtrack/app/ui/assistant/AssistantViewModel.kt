package com.medtrack.app.ui.assistant

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtrack.app.ai.AiAssistantOrchestrator
import com.medtrack.app.ai.AiConversationMessage
import com.medtrack.app.ai.StagedCareProposal
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AssistantMessage(
    val text: String,
    val fromUser: Boolean
)

data class StagedProposalUi(
    val proposal: StagedCareProposal,
    val selected: Boolean = true
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val orchestrator: AiAssistantOrchestrator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val admissionId: String? = savedStateHandle.get<String>("admissionId")?.ifBlank { null }

    val contextLabel: String =
        if (admissionId == null) {
            "No patient selected — global or new-patient input."
        } else {
            "Patient context: admission ${admissionId.take(8)}"
        }

    private val _messages = MutableStateFlow<List<AssistantMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input = _input.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _proposals = MutableStateFlow<List<StagedProposalUi>>(emptyList())
    val proposals = _proposals.asStateFlow()

    private val _commitError = MutableStateFlow<String?>(null)
    val commitError = _commitError.asStateFlow()

    fun onInputChange(value: String) {
        _input.value = value
    }

    fun toggleProposal(toolCallId: String) {
        val items = _proposals.value
        val target = items.firstOrNull { it.proposal.toolCallId == toolCallId } ?: return
        if (target.selected) {
            val dependents = items.filter {
                it.selected && target.proposal.atomicGroupId in it.proposal.dependsOnGroupIds
            }
            if (dependents.isNotEmpty()) {
                _commitError.value =
                    "Cannot deselect a prerequisite while dependent groups remain selected."
                return
            }
        }
        _commitError.value = null
        _proposals.update { current ->
            current.map { item ->
                if (item.proposal.toolCallId == toolCallId) item.copy(selected = !item.selected) else item
            }
        }
    }

    fun discardProposals() {
        val current = _proposals.value.map { it.proposal }
        _proposals.value = emptyList()
        _commitError.value = null
        if (current.isEmpty()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { orchestrator.discardProposal(current) } }
        }
    }

    fun sendMessage() {
        val message = _input.value.trim()
        if (message.isBlank() || _isLoading.value) return

        val conversation = _messages.value.map {
            AiConversationMessage(text = it.text, fromUser = it.fromUser)
        }
        _input.value = ""
        _commitError.value = null
        _messages.value = _messages.value + AssistantMessage(message, fromUser = true)

        viewModelScope.launch {
            _isLoading.value = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    orchestrator.parseUserMessage(message, conversation, admissionId)
                }
            }
            result.onSuccess { parsed ->
                _messages.value = _messages.value + AssistantMessage(parsed.narrative, fromUser = false)
                _proposals.value = parsed.proposals.map { StagedProposalUi(it) }
            }.onFailure { error ->
                _messages.value = _messages.value + AssistantMessage(
                    error.message ?: "Assistant failed to process the request.",
                    fromUser = false
                )
                _proposals.value = emptyList()
            }
            _isLoading.value = false
        }
    }

    fun confirmSelected(onSuccess: () -> Unit) {
        if (_isLoading.value) return
        val selected = _proposals.value.filter { it.selected }.map { it.proposal }
        if (selected.isEmpty()) {
            discardProposals()
            onSuccess()
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _commitError.value = null
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    orchestrator.commitProposals(selected)
                }
            }
            outcome.onSuccess { summary ->
                _messages.value = _messages.value + AssistantMessage(summary, fromUser = false)
                _isLoading.value = false
                if (summary.contains("needs review")) {
                    _commitError.value = summary
                } else {
                    _proposals.value = emptyList()
                    onSuccess()
                }
            }.onFailure { error ->
                _commitError.value = error.message ?: "Commit failed. Nothing further was written."
                _isLoading.value = false
            }
        }
    }
}
