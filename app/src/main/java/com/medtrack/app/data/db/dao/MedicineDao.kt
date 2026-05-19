package com.medtrack.app.data.db.dao

import androidx.room.*
import com.medtrack.app.data.db.entity.MedicineEntity
import kotlinx.coroutines.flow.Flow

/**
 * MedicineDao: Manages prescriptions for each visit.
 */
@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines WHERE visitId = :visitId")
    fun getMedicinesForVisit(visitId: Int): Flow<List<MedicineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicine(medicine: MedicineEntity)

    @Update
    suspend fun updateMedicine(medicine: MedicineEntity)

    @Delete
    suspend fun deleteMedicine(medicine: MedicineEntity)
}
