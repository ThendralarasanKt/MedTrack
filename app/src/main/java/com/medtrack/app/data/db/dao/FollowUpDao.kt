package com.medtrack.app.data.db.dao

import androidx.room.*
import com.medtrack.app.data.db.entity.FollowUpEntity
import com.medtrack.app.data.db.model.FollowUpWithPatient
import kotlinx.coroutines.flow.Flow

/**
 * FollowUpDao: Manages scheduling and notifications for follow-ups.
 */
@Dao
interface FollowUpDao {
    @Query("SELECT * FROM follow_ups ORDER BY scheduledDate ASC")
    fun getAllFollowUps(): Flow<List<FollowUpEntity>>

    @Query(
        """
        SELECT 
            follow_ups.id AS id,
            follow_ups.patientId AS patientId,
            follow_ups.visitId AS visitId,
            patients.name AS patientName,
            visits.roomNo AS roomNo,
            follow_ups.scheduledDate AS scheduledDate,
            follow_ups.scheduledTime AS scheduledTime,
            follow_ups.reason AS reason,
            follow_ups.isNotified AS isNotified,
            follow_ups.status AS status,
            follow_ups.notifiedAt AS notifiedAt,
            follow_ups.completedAt AS completedAt
        FROM follow_ups
        INNER JOIN patients ON patients.id = follow_ups.patientId
        INNER JOIN visits ON visits.id = follow_ups.visitId
        WHERE follow_ups.status != 'DONE'
        ORDER BY follow_ups.scheduledDate ASC, follow_ups.scheduledTime ASC
        """
    )
    fun getAllFollowUpsWithPatients(): Flow<List<FollowUpWithPatient>>

    @Query(
        """
        SELECT 
            follow_ups.id AS id,
            follow_ups.patientId AS patientId,
            follow_ups.visitId AS visitId,
            patients.name AS patientName,
            visits.roomNo AS roomNo,
            follow_ups.scheduledDate AS scheduledDate,
            follow_ups.scheduledTime AS scheduledTime,
            follow_ups.reason AS reason,
            follow_ups.isNotified AS isNotified,
            follow_ups.status AS status,
            follow_ups.notifiedAt AS notifiedAt,
            follow_ups.completedAt AS completedAt
        FROM follow_ups
        INNER JOIN patients ON patients.id = follow_ups.patientId
        INNER JOIN visits ON visits.id = follow_ups.visitId
        WHERE follow_ups.id = :id
        """
    )
    suspend fun getFollowUpWithPatientById(id: Int): FollowUpWithPatient?

    @Query(
        """
        SELECT * FROM follow_ups
        WHERE status = 'PENDING'
        ORDER BY scheduledDate ASC, scheduledTime ASC
        """
    )
    suspend fun getPendingFollowUps(): List<FollowUpEntity>

    @Query("SELECT * FROM follow_ups WHERE patientId = :patientId")
    fun getFollowUpsForPatient(patientId: Int): Flow<List<FollowUpEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollowUp(followUp: FollowUpEntity): Long

    @Update
    suspend fun updateFollowUp(followUp: FollowUpEntity)

    @Delete
    suspend fun deleteFollowUp(followUp: FollowUpEntity)

    @Query("SELECT * FROM follow_ups WHERE id = :id")
    suspend fun getFollowUpById(id: Int): FollowUpEntity?

    @Query(
        """
        UPDATE follow_ups
        SET isNotified = 1, status = 'NOTIFIED', notifiedAt = :notifiedAt
        WHERE id = :id AND status != 'DONE'
        """
    )
    suspend fun markNotified(id: Int, notifiedAt: String)

    @Query(
        """
        UPDATE follow_ups
        SET status = 'DONE', completedAt = :completedAt
        WHERE id = :id
        """
    )
    suspend fun markDone(id: Int, completedAt: String)
}
