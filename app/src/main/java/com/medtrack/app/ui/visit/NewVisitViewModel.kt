package com.medtrack.app.ui.visit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtrack.app.data.db.entity.*
import com.medtrack.app.data.repository.ClinicalRepository
import com.medtrack.app.data.storage.FileStorageManager
import com.medtrack.app.notification.FollowUpNotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * NewVisitViewModel: Manages the complex state of a new consultation.
 */
@HiltViewModel
class NewVisitViewModel @Inject constructor(
    private val repository: ClinicalRepository,
    private val fileStorageManager: FileStorageManager,
    private val followUpNotificationScheduler: FollowUpNotificationScheduler
) : ViewModel() {

    private val _saveSuccess = MutableSharedFlow<Boolean>()
    val saveSuccess = _saveSuccess.asSharedFlow()

    private val _tasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    val tasks = _tasks.asStateFlow()

    private val _medicines = MutableStateFlow<List<MedicineEntity>>(emptyList())
    val medicines = _medicines.asStateFlow()

    fun addTask(name: String, assignee: String, role: String, notes: String, isDone: Boolean) {
        val newTask = TaskEntity(
            visitId = 0,
            taskName = name,
            assignedTo = assignee,
            role = role,
            instructions = notes,
            status = if (isDone) "DONE" else "PENDING"
        )
        _tasks.value = _tasks.value + newTask
    }

    fun removeTask(task: TaskEntity) {
        _tasks.value = _tasks.value - task
    }

    fun addMedicine(name: String, dosage: String, duration: String) {
        val newMed = MedicineEntity(
            visitId = 0,
            name = name,
            dosage = dosage,
            duration = duration
        )
        _medicines.value = _medicines.value + newMed
    }

    fun removeMedicine(medicine: MedicineEntity) {
        _medicines.value = _medicines.value - medicine
    }

    fun saveVisit(
        patientId: Int,
        roomNo: String,
        symptoms: String,
        diagnosis: String,
        notes: String,
        followUpDate: LocalDate? = null,
        followUpTime: LocalTime? = null,
        followUpReason: String = "",
        reportUris: List<Pair<Uri, String>> = emptyList()
    ) {
        viewModelScope.launch {
            val visit = VisitEntity(
                patientId = patientId,
                visitDate = LocalDate.now().toString(),
                visitTime = LocalTime.now().toString(),
                roomNo = roomNo,
                symptoms = symptoms,
                diagnosis = diagnosis,
                progressNotes = notes
            )
            val visitId = repository.insertVisit(visit).toInt()

            _tasks.value.forEach { task ->
                repository.insertTask(task.copy(visitId = visitId))
            }

            _medicines.value.forEach { med ->
                repository.insertMedicine(med.copy(visitId = visitId))
            }

            reportUris.forEach { (uri, displayName) ->
                fileStorageManager.saveReport(uri, visitId, displayName)?.let { savedFile ->
                    repository.insertReport(
                        ReportEntity(
                            visitId = visitId,
                            fileName = savedFile.fileName,
                            filePath = savedFile.filePath,
                            fileType = savedFile.fileType
                        )
                    )
                }
            }

            followUpDate?.let {
                val scheduledTime = followUpTime?.toString()?.take(5) ?: "08:00"
                val followUpId = repository.insertFollowUp(
                    FollowUpEntity(
                        patientId = patientId,
                        visitId = visitId,
                        scheduledDate = it.toString(),
                        scheduledTime = scheduledTime,
                        reason = followUpReason.ifBlank { "Follow-up for visit #$visitId" }
                    )
                ).toInt()
                followUpNotificationScheduler.schedule(
                    followUpId = followUpId,
                    scheduledDate = it.toString(),
                    scheduledTime = scheduledTime
                )
            }

            _saveSuccess.emit(true)
        }
    }
}
