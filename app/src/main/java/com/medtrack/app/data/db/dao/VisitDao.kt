package com.medtrack.app.data.db.dao

import androidx.room.*
import com.medtrack.app.data.db.entity.VisitEntity
import com.medtrack.app.data.db.model.PatientVisitContext
import com.medtrack.app.data.db.model.VisitHistorySummary
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits WHERE patientId = :patientId ORDER BY visitDate DESC, visitTime DESC")
    fun getVisitsForPatient(patientId: Int): Flow<List<VisitEntity>>

    @Query(
        """
        SELECT
            visits.id AS id,
            visits.patientId AS patientId,
            visits.visitDate AS visitDate,
            visits.visitTime AS visitTime,
            visits.roomNo AS roomNo,
            visits.diagnosis AS diagnosis,
            (SELECT COUNT(*) FROM medicines WHERE medicines.visitId = visits.id) AS medicineCount,
            (SELECT COUNT(*) FROM tasks WHERE tasks.visitId = visits.id) AS taskCount,
            (SELECT COUNT(*) FROM tasks WHERE tasks.visitId = visits.id AND tasks.status = 'DONE') AS doneTaskCount,
            (SELECT COUNT(*) FROM reports WHERE reports.visitId = visits.id) AS documentCount,
            (SELECT follow_ups.status FROM follow_ups WHERE follow_ups.visitId = visits.id ORDER BY follow_ups.scheduledDate DESC, follow_ups.scheduledTime DESC LIMIT 1) AS followUpStatus,
            (SELECT follow_ups.reason FROM follow_ups WHERE follow_ups.visitId = visits.id ORDER BY follow_ups.scheduledDate DESC, follow_ups.scheduledTime DESC LIMIT 1) AS followUpReason,
            (SELECT follow_ups.scheduledDate FROM follow_ups WHERE follow_ups.visitId = visits.id ORDER BY follow_ups.scheduledDate DESC, follow_ups.scheduledTime DESC LIMIT 1) AS followUpDate,
            (SELECT follow_ups.scheduledTime FROM follow_ups WHERE follow_ups.visitId = visits.id ORDER BY follow_ups.scheduledDate DESC, follow_ups.scheduledTime DESC LIMIT 1) AS followUpTime
        FROM visits
        WHERE visits.patientId = :patientId
        ORDER BY visits.visitDate DESC, visits.visitTime DESC
        """
    )
    fun getVisitHistoryForPatient(patientId: Int): Flow<List<VisitHistorySummary>>

    @Query("SELECT * FROM visits WHERE id = :visitId")
    suspend fun getVisitById(visitId: Int): VisitEntity?

    @Query(
        """
        SELECT
            patients.id AS patientId,
            patients.name AS patientName,
            patients.age AS age,
            patients.sex AS sex,
            patients.contact AS contact,
            patients.address AS address,
            patients.medHistory AS medHistory,
            visits.id AS visitId,
            visits.visitDate AS visitDate,
            visits.visitTime AS visitTime,
            visits.roomNo AS roomNo,
            visits.symptoms AS symptoms,
            visits.diagnosis AS diagnosis,
            visits.progressNotes AS progressNotes
        FROM visits
        INNER JOIN patients ON patients.id = visits.patientId
        WHERE LOWER(TRIM(patients.name)) = LOWER(TRIM(:patientName))
            AND LOWER(TRIM(visits.roomNo)) = LOWER(TRIM(:roomNo))
        ORDER BY visits.visitDate DESC, visits.visitTime DESC, visits.id DESC
        """
    )
    suspend fun findVisitContextsByPatientNameAndRoom(
        patientName: String,
        roomNo: String
    ): List<PatientVisitContext>

    @Query(
        """
        SELECT
            patients.id AS patientId,
            patients.name AS patientName,
            patients.age AS age,
            patients.sex AS sex,
            patients.contact AS contact,
            patients.address AS address,
            patients.medHistory AS medHistory,
            visits.id AS visitId,
            visits.visitDate AS visitDate,
            visits.visitTime AS visitTime,
            visits.roomNo AS roomNo,
            visits.symptoms AS symptoms,
            visits.diagnosis AS diagnosis,
            visits.progressNotes AS progressNotes
        FROM visits
        INNER JOIN patients ON patients.id = visits.patientId
        WHERE LOWER(TRIM(patients.name)) = LOWER(TRIM(:patientName))
        ORDER BY visits.visitDate DESC, visits.visitTime DESC, visits.id DESC
        LIMIT 1
        """
    )
    suspend fun findLatestVisitContextByPatientName(patientName: String): PatientVisitContext?

    @Query(
        """
        UPDATE visits
        SET roomNo = :newRoomNo
        WHERE id = :visitId
        """
    )
    suspend fun updateVisitRoomNo(visitId: Int, newRoomNo: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisit(visit: VisitEntity): Long

    @Delete
    suspend fun deleteVisit(visit: VisitEntity)
}
