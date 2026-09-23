package com.medtrack.app.ui.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val admissionId: String? = savedStateHandle.get<String>("admissionId")?.ifBlank { null }
    private val timeline = ConversationTimeline(restoreOrInitial())

    private val _state = MutableStateFlow(timeline.state)
    val state = _state.asStateFlow()

    fun onComposerChange(value: String) = publish { timeline.onComposerChange(value) }
    fun sendFromComposer() = publish { timeline.sendFromComposer() }
    fun useComposerAsAnswer() = publish { timeline.useComposerAsAnswer() }
    fun sendComposerAsNewMessage() = publish { timeline.sendComposerAsNewMessage() }
    fun answerCard(itemId: String, selection: String) = publish { timeline.answerCard(itemId, selection) }
    fun changeAnswer(answerId: String) = publish { timeline.changeAnswer(answerId) }
    fun approveProposal(itemId: String) = publish { timeline.approveProposal(itemId) }
    fun discardProposal(itemId: String) = publish { timeline.discardProposal(itemId) }
    fun openSelector(itemId: String) = publish { timeline.openSelector(itemId) }
    fun closeSelector(selection: String?) = publish { timeline.closeSelector(selection) }
    fun setNearBottom(nearBottom: Boolean) = publish { timeline.setNearBottom(nearBottom) }
    fun jumpToLatest() = publish { timeline.jumpToLatest() }
    fun setOnline(online: Boolean) = publish { timeline.setOnline(online) }
    fun clearScope() = publish { timeline.clearScope() }
    fun applyStarter(label: String) = publish { timeline.applyStarter(label) }
    fun markReminderScheduled(receiptId: String) = publish { timeline.markReminderScheduled(receiptId) }

    private fun publish(block: () -> Unit) {
        block()
        _state.value = timeline.state
        savedStateHandle[THREAD_KEY] = timeline.state.toJson()
    }

    private fun restoreOrInitial(): ConversationState {
        val saved = savedStateHandle.get<String>(THREAD_KEY)
        if (!saved.isNullOrBlank()) {
            return runCatching { ConversationState.fromJson(saved) }.getOrElse { initialScope() }
        }
        return initialScope()
    }

    private fun initialScope(): ConversationState {
        val scope = admissionId?.let { PatientScope.selected(it) } ?: PatientScope.Global
        return ConversationState(activeScope = scope)
    }

    companion object {
        const val THREAD_KEY = "conversation.thread"
    }
}
