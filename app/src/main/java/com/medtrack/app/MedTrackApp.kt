package com.medtrack.app

import android.app.Application
import com.medtrack.app.data.repository.PatientRepository
import com.medtrack.app.mcp.transport.LocalMcpHttpServer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * MedTrackApp: The entry point of the application.
 * 
 * WHY: We need this class to initialize Hilt Dependency Injection. 
 * The @HiltAndroidApp annotation triggers Hilt's code generation.
 */
@HiltAndroidApp
class MedTrackApp : Application() {
    @Inject lateinit var localMcpHttpServer: LocalMcpHttpServer
    @Inject lateinit var patientRepository: PatientRepository
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        localMcpHttpServer.start()
        appScope.launch {
            val cutoff = LocalDateTime.now().minusDays(30).toString()
            patientRepository.purgeDischargedPatientsBefore(cutoff)
        }
    }

    override fun onTerminate() {
        localMcpHttpServer.stop()
        appScope.cancel()
        super.onTerminate()
    }
}
