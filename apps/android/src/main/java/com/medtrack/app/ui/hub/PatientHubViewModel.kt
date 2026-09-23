package com.medtrack.app.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtrack.app.data.care.CareCatalogBootstrap
import com.medtrack.app.hybrid.account.AccountSession
import com.medtrack.app.data.care.command.AssignTaskRequest
import com.medtrack.app.data.care.command.CareWritePath
import com.medtrack.app.data.care.command.ChangeMedicationOrderRequest
import com.medtrack.app.data.care.command.RecordAllergyRequest
import com.medtrack.app.data.care.command.RecordEncounterRequest
import com.medtrack.app.data.care.command.RecordObservationRequest
import com.medtrack.app.data.care.command.RecordProblemRequest
import com.medtrack.app.data.care.command.RecordTransferRequest
import com.medtrack.app.data.care.command.RespondToTaskRequest
import com.medtrack.app.data.care.model.CareEnums
import com.medtrack.app.data.care.model.ClinicalTime
import com.medtrack.app.data.care.model.Regimen
import com.medtrack.app.data.care.query.CareCensusQuery
import com.medtrack.app.data.care.query.PatientHub
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PatientHubViewModel @Inject constructor(
    private val censusQuery: CareCensusQuery,
    private val writePath: CareWritePath,
    private val catalog: CareCatalogBootstrap,
    private val accountSession: AccountSession
) : ViewModel() {
    private val workspace get() = accountSession.workspace()
    private val _hub = MutableStateFlow<PatientHub?>(null)
    val hub = _hub.asStateFlow()
    private val _discharged = MutableStateFlow(false)
    val discharged = _discharged.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun load(admissionId: String) {
        viewModelScope.launch {
            _hub.value = censusQuery.patientHub(admissionId)
        }
    }

    fun completeTask(taskId: String, admissionId: String) {
        mutate(admissionId) {
            writePath.work.respondToTask(
                workspace,
                RespondToTaskRequest(taskId = taskId, action = CareEnums.TaskResponseAction.COMPLETE)
            )
        }
    }

    fun moveBed(admissionId: String, bedCode: String) {
        mutate(admissionId) {
            val bed = catalog.findBed(bedCode) ?: error("No bed matches \"$bedCode\"")
            writePath.admissions.recordTransfer(
                workspace,
                RecordTransferRequest(
                    admissionId = admissionId,
                    effectiveAt = ClinicalTime.instant(System.currentTimeMillis()),
                    locationId = bed.id
                )
            )
        }
    }

    fun discharge(admissionId: String) {
        viewModelScope.launch {
            runCatching { writePath.admissions.dischargeAdmission(workspace, admissionId) }
                .onSuccess { _discharged.value = true }
                .onFailure { _error.value = it.message }
        }
    }

    fun addProblem(admissionId: String, patientId: String, description: String) {
        mutate(admissionId) {
            writePath.clinical.recordProblem(
                workspace,
                RecordProblemRequest(
                    patientId = patientId,
                    admissionId = admissionId,
                    description = description.trim(),
                    onset = ClinicalTime.instant(System.currentTimeMillis())
                )
            )
        }
    }

    fun addAllergy(admissionId: String, patientId: String, substance: String, reaction: String) {
        mutate(admissionId) {
            writePath.clinical.recordAllergy(
                workspace,
                RecordAllergyRequest(
                    patientId = patientId,
                    substance = substance.trim(),
                    reaction = reaction.trim().takeIf { it.isNotBlank() }
                )
            )
        }
    }

    fun addBedsideNote(admissionId: String, observations: String, plan: String) {
        mutate(admissionId) {
            val summary = listOf(observations.trim(), plan.trim())
                .filter { it.isNotBlank() }
                .joinToString("\n")
            writePath.clinical.recordEncounter(
                workspace,
                RecordEncounterRequest(
                    admissionId = admissionId,
                    kind = CareEnums.EncounterKind.ROUND,
                    reviewedAt = ClinicalTime.instant(System.currentTimeMillis()),
                    summary = summary.ifBlank { "Bedside review" },
                    noteText = summary.takeIf { it.isNotBlank() },
                    noteKind = CareEnums.NoteKind.PROGRESS
                )
            )
        }
    }

    fun addMedication(admissionId: String, name: String, dose: String, schedule: String) {
        mutate(admissionId) {
            writePath.therapy.changeMedicationOrder(
                workspace,
                ChangeMedicationOrderRequest(
                    admissionId = admissionId,
                    medicationDisplayName = name.trim(),
                    action = CareEnums.MedicationOrderAction.START,
                    doseText = dose.trim().takeIf { it.isNotBlank() },
                    schedule = Regimen.textOnly(schedule.trim().ifBlank { "as directed" }),
                    orderedAt = ClinicalTime.instant(System.currentTimeMillis())
                )
            )
        }
    }

    fun stopMedication(admissionId: String, orderId: String, medicationId: String) {
        mutate(admissionId) {
            writePath.therapy.changeMedicationOrder(
                workspace,
                ChangeMedicationOrderRequest(
                    admissionId = admissionId,
                    medicationId = medicationId,
                    orderId = orderId,
                    action = CareEnums.MedicationOrderAction.STOP,
                    reason = "Stopped from patient hub",
                    orderedAt = ClinicalTime.instant(System.currentTimeMillis())
                )
            )
        }
    }

    fun addTask(admissionId: String, title: String, hoursFromNow: String) {
        mutate(admissionId) {
            val hours = hoursFromNow.trim().toLongOrNull()?.coerceIn(1, 72) ?: 4L
            writePath.work.assignTask(
                workspace,
                AssignTaskRequest(
                    admissionId = admissionId,
                    title = title.trim(),
                    followUpOwnerPersonId = workspace.actorPersonId,
                    dueAt = System.currentTimeMillis() + hours * 3_600_000,
                    dueZoneId = accountSession.timeZone(),
                    scheduleReminder = true
                )
            )
        }
    }

    fun addVital(admissionId: String, patientId: String, name: String, value: String, unit: String) {
        mutate(admissionId) {
            val numeric = value.trim().toDoubleOrNull() != null
            writePath.clinical.recordObservation(
                workspace,
                RecordObservationRequest(
                    patientId = patientId,
                    admissionId = admissionId,
                    name = name.trim(),
                    valueKind = if (numeric) {
                        CareEnums.ObservationValueKind.NUMBER
                    } else {
                        CareEnums.ObservationValueKind.TEXT
                    },
                    numericValue = value.trim().takeIf { numeric },
                    textValue = value.trim().takeIf { !numeric },
                    unit = unit.trim().takeIf { it.isNotBlank() },
                    observedAt = ClinicalTime.instant(System.currentTimeMillis())
                )
            )
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun mutate(admissionId: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onFailure { _error.value = it.message ?: "Could not save" }
            load(admissionId)
        }
    }
}
