package com.medtrack.app.ui.patient

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtrack.app.data.db.entity.PatientEntity
import com.medtrack.app.data.db.model.VisitHistorySummary
import com.medtrack.app.data.repository.ClinicalRepository
import com.medtrack.app.data.repository.PatientRepository
import com.medtrack.app.data.storage.FileStorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PatientProfileViewModel: Manages state for a single patient's profile.
 * 
 * WHY: It provides the specific patient's info and their history 
 * to the UI in a reactive way.
 */
@HiltViewModel
class PatientProfileViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val clinicalRepository: ClinicalRepository,
    private val fileStorageManager: FileStorageManager
) : ViewModel() {

    private val _patient = MutableStateFlow<PatientEntity?>(null)
    val patient = _patient.asStateFlow()

    private val _visits = MutableStateFlow<List<VisitHistorySummary>>(emptyList())
    val visits = _visits.asStateFlow()

    /**
     * loadPatientData: Called when the screen opens.
     */
    fun loadPatientData(patientId: Int) {
        viewModelScope.launch {
            // Fetch patient details
            _patient.value = patientRepository.getPatientById(patientId)
            
            // Observe visit history (updates automatically if a new visit is saved)
            clinicalRepository.getVisitHistoryForPatient(patientId).collect {
                _visits.value = it
            }
        }
    }

    fun updatePatientPhoto(patientId: Int, uri: Uri) {
        viewModelScope.launch {
            val patient = patientRepository.getPatientById(patientId) ?: return@launch
            fileStorageManager.savePatientPhoto(uri, patientId)?.let { path ->
                val updated = patient.copy(photoPath = path)
                patientRepository.updatePatient(updated)
                _patient.value = updated
            }
        }
    }
}
