package com.medtrack.app.ui.patient

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtrack.app.data.db.entity.PatientEntity
import com.medtrack.app.data.repository.PatientRepository
import com.medtrack.app.data.storage.FileStorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AddPatientViewModel: Handles the logic for registering new patients.
 * 
 * WHY: We separate the UI from the saving logic. This VM ensures 
 * that the "Save" action happens safely in a background thread.
 */
@HiltViewModel
class AddPatientViewModel @Inject constructor(
    private val repository: PatientRepository,
    private val fileStorageManager: FileStorageManager
) : ViewModel() {

    // A "One-Time Event" to tell the UI to close the screen after saving
    private val _saveSuccess = MutableSharedFlow<Boolean>()
    val saveSuccess = _saveSuccess.asSharedFlow()

    fun savePatient(
        name: String,
        age: String,
        sex: String,
        contact: String,
        address: String,
        history: String,
        photoUri: Uri? = null
    ) {
        viewModelScope.launch {
            if (name.isBlank() || age.isBlank()) return@launch
            
            val patient = PatientEntity(
                name = name,
                age = age.toIntOrNull() ?: 0,
                sex = sex,
                contact = contact,
                address = address,
                medHistory = history
            )
            
            val patientId = repository.insertPatient(patient).toInt()
            photoUri?.let { uri ->
                fileStorageManager.savePatientPhoto(uri, patientId)?.let { path ->
                    repository.updatePatient(patient.copy(id = patientId, photoPath = path))
                }
            }
            _saveSuccess.emit(true)
        }
    }
}
