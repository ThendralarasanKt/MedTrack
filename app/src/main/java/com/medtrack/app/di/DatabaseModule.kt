package com.medtrack.app.di

import android.content.Context
import androidx.room.Room
import com.medtrack.app.data.db.AppDatabase
import com.medtrack.app.data.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DatabaseModule: The "Factory" for our database components.
 * 
 * WHY: Hilt needs to know how to create the AppDatabase instance. 
 * By providing DAOs here, we can inject them directly into our repositories.
 * 
 * HOW: We use @Singleton to ensure only one database instance exists.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "medtrack_db"
        ).fallbackToDestructiveMigration() // Useful during development
         .build()
    }

    @Provides
    fun providePatientDao(db: AppDatabase): PatientDao = db.patientDao()

    @Provides
    fun provideVisitDao(db: AppDatabase): VisitDao = db.visitDao()

    @Provides
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideMedicineDao(db: AppDatabase): MedicineDao = db.medicineDao()

    @Provides
    fun provideReportDao(db: AppDatabase): ReportDao = db.reportDao()

    @Provides
    fun provideFollowUpDao(db: AppDatabase): FollowUpDao = db.followUpDao()
}
