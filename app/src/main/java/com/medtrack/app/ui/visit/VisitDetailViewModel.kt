package com.medtrack.app.ui.visit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtrack.app.data.db.entity.*
import com.medtrack.app.data.repository.ClinicalRepository
import com.medtrack.app.data.storage.FileStorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * VisitDetailViewModel: Fetches all data related to a specific past visit.
 */
@HiltViewModel
class VisitDetailViewModel @Inject constructor(
    private val repository: ClinicalRepository,
    private val fileStorageManager: FileStorageManager
) : ViewModel() {

    private val _visit = MutableStateFlow<VisitEntity?>(null)
    val visit = _visit.asStateFlow()

    private val _tasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    val tasks = _tasks.asStateFlow()

    private val _medicines = MutableStateFlow<List<MedicineEntity>>(emptyList())
    val medicines = _medicines.asStateFlow()

    private val _reports = MutableStateFlow<List<ReportEntity>>(emptyList())
    val reports = _reports.asStateFlow()

    /**
     * loadVisitData: Loads all "child" records for a visit ID.
     */
    fun loadVisitData(visitId: Int) {
        viewModelScope.launch {
            _visit.value = repository.getVisitById(visitId)
        }
        viewModelScope.launch {
            repository.getTasksForVisit(visitId).collect { _tasks.value = it }
        }
        viewModelScope.launch {
            repository.getMedicinesForVisit(visitId).collect { _medicines.value = it }
        }
        viewModelScope.launch {
            repository.getReportsForVisit(visitId).collect { _reports.value = it }
        }
    }

    /**
     * toggleTaskStatus: Allows the doctor/nurse to mark a task as DONE from this screen.
     */
    fun toggleTaskStatus(taskId: Int, currentStatus: String) {
        viewModelScope.launch {
            val isDone = currentStatus != "DONE"
            repository.updateTaskStatus(taskId, isDone)
        }
    }

    fun addReport(visitId: Int, uri: Uri, displayName: String) {
        viewModelScope.launch {
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
    }
}
