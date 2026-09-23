package com.medtrack.app.ui.admit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtrack.app.data.care.CareCatalogBootstrap
import com.medtrack.app.data.care.command.CareWritePath
import com.medtrack.app.hybrid.account.AccountSession
import com.medtrack.app.data.care.command.CreatePatientAndAdmissionRequest
import com.medtrack.app.data.care.command.RecordProblemRequest
import com.medtrack.app.data.care.command.RecordTransferRequest
import com.medtrack.app.data.care.model.ClinicalTime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdmitUiState(
    val beds: List<String> = emptyList(),
    val error: String? = null,
    val saving: Boolean = false
)

@HiltViewModel
class AdmitViewModel @Inject constructor(
    private val writePath: CareWritePath,
    private val catalog: CareCatalogBootstrap,
    private val accountSession: AccountSession
) : ViewModel() {
    private val workspace get() = accountSession.workspace()
    private val _state = MutableStateFlow(AdmitUiState())
    val state = _state.asStateFlow()
    private val _admitted = MutableSharedFlow<String>()
    val admitted = _admitted.asSharedFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(beds = catalog.bedLabels())
        }
    }

    fun admit(name: String, age: String, sex: String, problem: String, bedCode: String) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            runCatching {
                val cat = catalog.ensure()
                val created = writePath.admissions.createPatientAndAdmission(
                    workspace,
                    CreatePatientAndAdmissionRequest(
                        patientDisplayName = name.trim(),
                        hospitalId = cat.hospitalId,
                        reportedAge = age.trim().takeIf { it.isNotBlank() },
                        sexConceptId = sex.trim().takeIf { it.isNotBlank() }
                    )
                )
                if (bedCode.isNotBlank()) {
                    val bed = catalog.findBed(bedCode)
                        ?: error("No bed matches \"$bedCode\". Use a catalog bed such as ICU Bed 04.")
                    writePath.admissions.recordTransfer(
                        workspace,
                        RecordTransferRequest(
                            admissionId = created.admissionId,
                            effectiveAt = ClinicalTime.instant(System.currentTimeMillis()),
                            locationId = bed.id
                        )
                    )
                }
                if (problem.isNotBlank()) {
                    writePath.clinical.recordProblem(
                        workspace,
                        RecordProblemRequest(
                            patientId = created.patientId,
                            admissionId = created.admissionId,
                            description = problem.trim(),
                            onset = ClinicalTime.instant(System.currentTimeMillis())
                        )
                    )
                }
                created.admissionId
            }.onSuccess { admissionId ->
                _state.value = _state.value.copy(saving = false)
                _admitted.emit(admissionId)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    saving = false,
                    error = error.message ?: "Admit failed"
                )
            }
        }
    }
}
