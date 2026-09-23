package com.medtrack.app.ui.handover

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtrack.app.data.care.query.CareCensusQuery
import com.medtrack.app.data.care.query.CensusPatient
import com.medtrack.app.hybrid.account.AccountSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HandoverViewModel @Inject constructor(
    private val censusQuery: CareCensusQuery,
    private val accountSession: AccountSession,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _text = MutableStateFlow("")
    val text = _text.asStateFlow()
    private val _high = MutableStateFlow<List<CensusPatient>>(emptyList())
    val high = _high.asStateFlow()
    private val _stable = MutableStateFlow<List<CensusPatient>>(emptyList())
    val stable = _stable.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val patients = censusQuery.censusPatients(accountSession.ownerAccountId())
            val highAttention = patients.filter {
                it.wardLabel.contains("ICU", true) || it.openTaskCount > 0
            }
            val rest = patients - highAttention.toSet()
            _high.value = highAttention
            _stable.value = rest
            _text.value = buildString {
                appendLine("*MedTrack handover — City Hospital*")
                appendLine()
                appendLine("*High attention (${highAttention.size})*")
                highAttention.forEach { patient ->
                    appendLine("• ${patient.locationLabel}: ${patient.displayName} — ${patient.problemSummary ?: "see chart"}")
                    if (patient.nextTaskTitle != null) appendLine("  PENDING: ${patient.nextTaskTitle}")
                }
                appendLine()
                appendLine("*Stable (${rest.size})*")
                rest.forEach { patient ->
                    appendLine("• ${patient.locationLabel}: ${patient.displayName} — ${patient.problemSummary ?: "stable"}")
                }
            }
        }
    }

    fun copyForWhatsApp() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Handover", _text.value))
    }

    fun share() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, _text.value)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share handover").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
