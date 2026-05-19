package com.medtrack.app.data.repository

import com.medtrack.app.data.db.dao.PatientDao
import com.medtrack.app.data.db.entity.PatientEntity
import com.medtrack.app.data.db.model.PatientListItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PatientRepository: Manages patient records for the app.
 */
@Singleton
class PatientRepository @Inject constructor(
    private val patientDao: PatientDao
) {
    fun getAllPatients(): Flow<List<PatientEntity>> = patientDao.getAllPatients()

    fun getPatientListItems(): Flow<List<PatientListItem>> = patientDao.getPatientListItems()

    fun searchPatients(searchQuery: String): Flow<List<PatientEntity>> =
        patientDao.searchPatients(searchQuery)

    fun searchPatientListItems(searchQuery: String): Flow<List<PatientListItem>> =
        patientDao.searchPatientListItems(searchQuery)

    suspend fun getPatientById(id: Int): PatientEntity? = patientDao.getPatientById(id)

    suspend fun insertPatient(patient: PatientEntity): Long = patientDao.insertPatient(patient)

    suspend fun updatePatient(patient: PatientEntity) = patientDao.updatePatient(patient)

    suspend fun deletePatient(patient: PatientEntity) = patientDao.deletePatient(patient)
}
