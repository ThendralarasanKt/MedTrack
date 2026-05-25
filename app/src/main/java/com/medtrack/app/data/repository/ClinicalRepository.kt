package com.medtrack.app.data.repository

import com.medtrack.app.data.db.dao.*
import com.medtrack.app.data.db.entity.*
import com.medtrack.app.data.db.model.PatientCurrentProcess
import com.medtrack.app.data.db.model.PatientVisitContext
import com.medtrack.app.data.db.model.FollowUpWithPatient
import com.medtrack.app.data.db.model.VisitHistorySummary
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ClinicalRepository: Manages the medical data for consultations.
 */
@Singleton
class ClinicalRepository @Inject constructor(
    private val visitDao: VisitDao,
    private val taskDao: TaskDao,
    private val medicineDao: MedicineDao,
    private val reportDao: ReportDao,
    private val followUpDao: FollowUpDao
) {
    // Visits
    fun getVisitsForPatient(patientId: Int): Flow<List<VisitEntity>> = 
        visitDao.getVisitsForPatient(patientId)

    fun getVisitHistoryForPatient(patientId: Int): Flow<List<VisitHistorySummary>> =
        visitDao.getVisitHistoryForPatient(patientId)

    suspend fun getVisitById(visitId: Int): VisitEntity? = visitDao.getVisitById(visitId)

    suspend fun findVisitContextsByPatientNameAndRoom(
        patientName: String,
        roomNo: String
    ): List<PatientVisitContext> = visitDao.findVisitContextsByPatientNameAndRoom(patientName, roomNo)

    suspend fun findLatestVisitContextByPatientName(
        patientName: String
    ): PatientVisitContext? = visitDao.findLatestVisitContextByPatientName(patientName)

    suspend fun updateVisitRoomNo(visitId: Int, newRoomNo: String): Int =
        visitDao.updateVisitRoomNo(visitId, newRoomNo)

    suspend fun insertVisit(visit: VisitEntity): Long = visitDao.insertVisit(visit)

    // Tasks (Supports Custom Roles)
    fun getTasksForVisit(visitId: Int): Flow<List<TaskEntity>> = 
        taskDao.getTasksForVisit(visitId)

    suspend fun insertTask(task: TaskEntity) = taskDao.insertTask(task)
    
    suspend fun updateTaskStatus(taskId: Int, isDone: Boolean) {
        val status = if (isDone) "DONE" else "PENDING"
        taskDao.updateTaskStatus(taskId, status)
    }

    // Medicines
    fun getMedicinesForVisit(visitId: Int): Flow<List<MedicineEntity>> = 
        medicineDao.getMedicinesForVisit(visitId)

    suspend fun insertMedicine(medicine: MedicineEntity) = 
        medicineDao.insertMedicine(medicine)

    suspend fun getMedicinesForVisitNow(visitId: Int): List<MedicineEntity> =
        medicineDao.getMedicinesForVisitNow(visitId)

    // Reports
    fun getReportsForVisit(visitId: Int): Flow<List<ReportEntity>> = 
        reportDao.getReportsForVisit(visitId)

    suspend fun insertReport(report: ReportEntity) = reportDao.insertReport(report)

    suspend fun getReportsForVisitNow(visitId: Int): List<ReportEntity> =
        reportDao.getReportsForVisitNow(visitId)

    // Follow-Ups
    fun getAllFollowUps(): Flow<List<FollowUpEntity>> = followUpDao.getAllFollowUps()

    fun getAllFollowUpsWithPatients(): Flow<List<FollowUpWithPatient>> =
        followUpDao.getAllFollowUpsWithPatients()
    
    suspend fun insertFollowUp(followUp: FollowUpEntity) = followUpDao.insertFollowUp(followUp)

    suspend fun updateFollowUp(followUp: FollowUpEntity) = followUpDao.updateFollowUp(followUp)

    suspend fun getFollowUpById(id: Int): FollowUpEntity? = followUpDao.getFollowUpById(id)

    suspend fun getLatestFollowUpForVisit(visitId: Int): FollowUpWithPatient? =
        followUpDao.getLatestFollowUpForVisit(visitId)

    suspend fun markFollowUpDone(id: Int, completedAt: String) =
        followUpDao.markDone(id, completedAt)

    suspend fun getTasksForVisitNow(visitId: Int): List<TaskEntity> =
        taskDao.getTasksForVisitNow(visitId)

    suspend fun getPatientCurrentProcess(
        patientName: String,
        roomNo: String
    ): PatientCurrentProcess? {
        val context = findVisitContextsByPatientNameAndRoom(patientName, roomNo).firstOrNull() ?: return null
        return PatientCurrentProcess(
            context = context,
            medicines = getMedicinesForVisitNow(context.visitId),
            tasks = getTasksForVisitNow(context.visitId),
            reports = getReportsForVisitNow(context.visitId),
            followUp = getLatestFollowUpForVisit(context.visitId)
        )
    }
}
