package com.medtrack.app.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtrack.app.ai.AiAssistantOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AssistantMessage(
    val text: String,
    val fromUser: Boolean
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val orchestrator: AiAssistantOrchestrator
) : ViewModel() {
    private val _messages = MutableStateFlow<List<AssistantMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input = _input.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun onInputChange(value: String) {
        _input.value = value
    }

    fun sendMessage() {
        val message = _input.value.trim()
        if (message.isBlank() || _isLoading.value) return

        _input.value = ""
        _messages.value = _messages.value + AssistantMessage(message, fromUser = true)

        viewModelScope.launch {
            _isLoading.value = true
            val response = runCatching {
                withContext(Dispatchers.Default) {
                    orchestrator.handleUserMessage(message)
                }
            }.getOrElse { error ->
                error.message ?: "Assistant failed to process the request."
            }
            _messages.value = _messages.value + AssistantMessage(response, fromUser = false)
            _isLoading.value = false
        }
    }
}
