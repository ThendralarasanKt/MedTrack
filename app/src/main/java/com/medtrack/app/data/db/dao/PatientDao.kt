package com.medtrack.app.data.db.dao

import androidx.room.*
import com.medtrack.app.data.db.entity.PatientEntity
import com.medtrack.app.data.db.model.PatientListItem
import kotlinx.coroutines.flow.Flow

/**
 * PatientDao: The Librarian for our "patients" table.
 * 
 * WHY: We updated the search query to check both Name and ID.
 * Since we have 10-30 patients/day, quick lookups by ID are essential.
 */
@Dao
interface PatientDao {

    @Query("SELECT * FROM patients ORDER BY name ASC")
    fun getAllPatients(): Flow<List<PatientEntity>>

    @Query(
        """
        SELECT
            patients.id AS id,
            patients.name AS name,
            patients.age AS age,
            patients.sex AS sex,
            patients.contact AS contact,
            patients.address AS address,
            patients.medHistory AS medHistory,
            patients.photoPath AS photoPath,
            (
                SELECT visits.roomNo
                FROM visits
                WHERE visits.patientId = patients.id AND visits.roomNo != ''
                ORDER BY visits.visitDate DESC, visits.visitTime DESC
                LIMIT 1
            ) AS latestRoomNo
        FROM patients
        ORDER BY patients.name ASC
        """
    )
    fun getPatientListItems(): Flow<List<PatientListItem>>

    @Query(
        """
        SELECT
            patients.id AS id,
            patients.name AS name,
            patients.age AS age,
            patients.sex AS sex,
            patients.contact AS contact,
            patients.address AS address,
            patients.medHistory AS medHistory,
            patients.photoPath AS photoPath,
            (
                SELECT visits.roomNo
                FROM visits
                WHERE visits.patientId = patients.id AND visits.roomNo != ''
                ORDER BY visits.visitDate DESC, visits.visitTime DESC
                LIMIT 1
            ) AS latestRoomNo
        FROM patients
        WHERE patients.isDischarged = 0
        ORDER BY patients.name ASC
        """
    )
    fun getActivePatientListItems(): Flow<List<PatientListItem>>

    @Query(
        """
        SELECT
            patients.id AS id,
            patients.name AS name,
            patients.age AS age,
            patients.sex AS sex,
            patients.contact AS contact,
            patients.address AS address,
            patients.medHistory AS medHistory,
            patients.photoPath AS photoPath,
            (
                SELECT visits.roomNo
                FROM visits
                WHERE visits.patientId = patients.id AND visits.roomNo != ''
                ORDER BY visits.visitDate DESC, visits.visitTime DESC
                LIMIT 1
            ) AS latestRoomNo
        FROM patients
        WHERE patients.isDischarged = 1
        ORDER BY patients.dischargedAt DESC, patients.name ASC
        """
    )
    fun getDischargedPatientListItems(): Flow<List<PatientListItem>>

    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun getPatientById(id: Int): PatientEntity?

    /**
     * Search by Name OR ID. 
     * We convert the ID to a string in the query to allow partial ID matching.
     */
    @Query("SELECT * FROM patients WHERE name LIKE '%' || :searchQuery || '%' OR CAST(id AS TEXT) LIKE '%' || :searchQuery || '%'")
    fun searchPatients(searchQuery: String): Flow<List<PatientEntity>>

    @Query(
        """
        SELECT
            patients.id AS id,
            patients.name AS name,
            patients.age AS age,
            patients.sex AS sex,
            patients.contact AS contact,
            patients.address AS address,
            patients.medHistory AS medHistory,
            patients.photoPath AS photoPath,
            (
                SELECT visits.roomNo
                FROM visits
                WHERE visits.patientId = patients.id AND visits.roomNo != ''
                ORDER BY visits.visitDate DESC, visits.visitTime DESC
                LIMIT 1
            ) AS latestRoomNo
        FROM patients
        WHERE patients.name LIKE '%' || :searchQuery || '%'
            OR patients.contact LIKE '%' || :searchQuery || '%'
            OR CAST(patients.id AS TEXT) LIKE '%' || :searchQuery || '%'
            OR EXISTS (
                SELECT 1
                FROM visits
                WHERE visits.patientId = patients.id
                    AND visits.roomNo LIKE '%' || :searchQuery || '%'
            )
        ORDER BY patients.name ASC
        """
    )
    fun searchPatientListItems(searchQuery: String): Flow<List<PatientListItem>>

    @Query(
        """
        SELECT
            patients.id AS id,
            patients.name AS name,
            patients.age AS age,
            patients.sex AS sex,
            patients.contact AS contact,
            patients.address AS address,
            patients.medHistory AS medHistory,
            patients.photoPath AS photoPath,
            (
                SELECT visits.roomNo
                FROM visits
                WHERE visits.patientId = patients.id AND visits.roomNo != ''
                ORDER BY visits.visitDate DESC, visits.visitTime DESC
                LIMIT 1
            ) AS latestRoomNo
        FROM patients
        WHERE patients.isDischarged = 0
            AND (
                patients.name LIKE '%' || :searchQuery || '%'
                OR patients.contact LIKE '%' || :searchQuery || '%'
                OR CAST(patients.id AS TEXT) LIKE '%' || :searchQuery || '%'
                OR EXISTS (
                    SELECT 1
                    FROM visits
                    WHERE visits.patientId = patients.id
                        AND visits.roomNo LIKE '%' || :searchQuery || '%'
                )
            )
        ORDER BY patients.name ASC
        """
    )
    fun searchActivePatientListItems(searchQuery: String): Flow<List<PatientListItem>>

    @Query(
        """
        SELECT
            patients.id AS id,
            patients.name AS name,
            patients.age AS age,
            patients.sex AS sex,
            patients.contact AS contact,
            patients.address AS address,
            patients.medHistory AS medHistory,
            patients.photoPath AS photoPath,
            (
                SELECT visits.roomNo
                FROM visits
                WHERE visits.patientId = patients.id AND visits.roomNo != ''
                ORDER BY visits.visitDate DESC, visits.visitTime DESC
                LIMIT 1
            ) AS latestRoomNo
        FROM patients
        WHERE patients.isDischarged = 1
            AND (
                patients.name LIKE '%' || :searchQuery || '%'
                OR patients.contact LIKE '%' || :searchQuery || '%'
                OR CAST(patients.id AS TEXT) LIKE '%' || :searchQuery || '%'
                OR EXISTS (
                    SELECT 1
                    FROM visits
                    WHERE visits.patientId = patients.id
                        AND visits.roomNo LIKE '%' || :searchQuery || '%'
                )
            )
        ORDER BY patients.dischargedAt DESC, patients.name ASC
        """
    )
    fun searchDischargedPatientListItems(searchQuery: String): Flow<List<PatientListItem>>

    @Query(
        """
        UPDATE patients
        SET isDischarged = 1, dischargedAt = :dischargedAt
        WHERE id = :patientId
        """
    )
    suspend fun dischargePatient(patientId: Int, dischargedAt: String): Int

    @Query(
        """
        DELETE FROM patients
        WHERE isDischarged = 1
            AND dischargedAt IS NOT NULL
            AND dischargedAt < :cutoff
        """
    )
    suspend fun deleteDischargedPatientsBefore(cutoff: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: PatientEntity): Long

    @Update
    suspend fun updatePatient(patient: PatientEntity)

    @Delete
    suspend fun deletePatient(patient: PatientEntity)
}
