package com.medtrack.app

import android.app.Application
import com.medtrack.app.mcp.transport.LocalMcpHttpServer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * MedTrackApp: The entry point of the application.
 * 
 * WHY: We need this class to initialize Hilt Dependency Injection. 
 * The @HiltAndroidApp annotation triggers Hilt's code generation.
 */
@HiltAndroidApp
class MedTrackApp : Application() {
    @Inject lateinit var localMcpHttpServer: LocalMcpHttpServer

    override fun onCreate() {
        super.onCreate()
        localMcpHttpServer.start()
    }

    override fun onTerminate() {
        localMcpHttpServer.stop()
        super.onTerminate()
    }
}
