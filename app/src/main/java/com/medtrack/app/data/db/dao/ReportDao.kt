package com.medtrack.app.data.db.dao

import androidx.room.*
import com.medtrack.app.data.db.entity.ReportEntity
import kotlinx.coroutines.flow.Flow

/**
 * ReportDao: Manages medical reports (photos/PDFs) linked to visits.
 */
@Dao
interface ReportDao {
    @Query("SELECT * FROM reports WHERE visitId = :visitId")
    fun getReportsForVisit(visitId: Int): Flow<List<ReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ReportEntity)

    @Delete
    suspend fun deleteReport(report: ReportEntity)
}
