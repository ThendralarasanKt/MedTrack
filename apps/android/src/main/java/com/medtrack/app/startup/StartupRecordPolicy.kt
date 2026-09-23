package com.medtrack.app.startup

import com.medtrack.app.data.db.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application startup policy for clinical record retention.
 * Discharged admissions stay on disk; this path only touches the live database.
 */
@Singleton
class StartupRecordPolicy @Inject constructor() {
    suspend fun onApplicationStart(database: AppDatabase) {
        database.careDao().getPerson("")
    }
}
