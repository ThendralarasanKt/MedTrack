package com.medtrack.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.medtrack.app.data.db.converters.DateConverters
import com.medtrack.app.data.db.dao.*
import com.medtrack.app.data.db.entity.*

/**
 * AppDatabase: The main database class for MedTrack.
 * 
 * WHY: This class ties all entities, DAOs, and Converters together.
 */
@Database(
    entities = [
        PatientEntity::class,
        VisitEntity::class,
        TaskEntity::class,
        MedicineEntity::class,
        ReportEntity::class,
        FollowUpEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(DateConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun visitDao(): VisitDao
    abstract fun taskDao(): TaskDao
    abstract fun medicineDao(): MedicineDao
    abstract fun reportDao(): ReportDao
    abstract fun followUpDao(): FollowUpDao
}
