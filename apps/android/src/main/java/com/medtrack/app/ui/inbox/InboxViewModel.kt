package com.medtrack.app.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtrack.app.data.care.command.CareWritePath
import com.medtrack.app.hybrid.account.AccountSession
import com.medtrack.app.data.care.command.RecordEncounterRequest
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.ClinicalTime
import com.medtrack.app.data.care.query.CareCensusQuery
import com.medtrack.app.data.care.query.CensusPatient
import com.medtrack.app.data.care.query.InboxItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val censusQuery: CareCensusQuery,
    private val writePath: CareWritePath,
    private val accountSession: AccountSession
) : ViewModel() {
    private val owner get() = accountSession.ownerAccountId()
    private val workspace get() = accountSession.workspace()

    private val _items = MutableStateFlow<List<InboxItem>>(emptyList())
    val items = _items.asStateFlow()
    private val _candidates = MutableStateFlow<List<CensusPatient>>(emptyList())
    val candidates = _candidates.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _opened = MutableSharedFlow<String>()
    val opened = _opened.asSharedFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _items.value = censusQuery.inbox(owner)
            _candidates.value = censusQuery.censusPatients(owner, discharged = false)
        }
    }

    fun link(intakeId: String, admissionId: String) {
        viewModelScope.launch {
            val summary = _items.value.firstOrNull { it.intake.id == intakeId }?.intake?.summary
            runCatching {
                writePath.admissions.resolveIntake(workspace, intakeId, admissionId, summary)
                writePath.clinical.recordEncounter(
                    workspace,
                    RecordEncounterRequest(
                        admissionId = admissionId,
                        kind = CareEnums.EncounterKind.REFERRAL_REVIEW,
                        reviewedAt = ClinicalTime.instant(System.currentTimeMillis()),
                        summary = summary ?: "Linked unassigned intake",
                        noteText = summary,
                        noteKind = CareEnums.NoteKind.OTHER
                    )
                )
            }.onSuccess {
                refresh()
                _opened.emit(admissionId)
            }.onFailure {
                _error.value = it.message ?: "Could not link intake"
            }
        }
    }

    fun dismiss(intakeId: String) {
        viewModelScope.launch {
            runCatching { writePath.admissions.dismissIntake(workspace, intakeId) }
                .onSuccess { refresh() }
                .onFailure { _error.value = it.message ?: "Could not dismiss intake" }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
