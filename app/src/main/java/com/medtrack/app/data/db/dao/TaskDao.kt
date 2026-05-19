package com.medtrack.app.data.db.dao

import androidx.room.*
import com.medtrack.app.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * TaskDao: Manages tasks assigned during visits.
 * 
 * WHY: Doctors need to track pending lab tests or nursing tasks. 
 * This DAO allows toggling task status.
 */
@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE visitId = :visitId")
    fun getTasksForVisit(visitId: Int): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: Int, status: String)
}
