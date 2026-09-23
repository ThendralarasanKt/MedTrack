package com.medtrack.app

import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.medtrack.app.data.db.AppDatabase
import com.medtrack.app.mcp.transport.LocalMcpHttpServer
import com.medtrack.app.startup.AppStartupHook
import com.medtrack.app.startup.StartupRecordPolicy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * MedTrackApp: The entry point of the application.
 *
 * WHY: We need this class to initialize Hilt Dependency Injection.
 * The @HiltAndroidApp annotation triggers Hilt's code generation.
 */
@HiltAndroidApp
class MedTrackApp : Application() {
    @Inject lateinit var localMcpHttpServer: LocalMcpHttpServer
    @Inject lateinit var startupHooks: Set<@JvmSuppressWildcards AppStartupHook>
    @Inject lateinit var firebaseAnalytics: FirebaseAnalytics
    @Inject lateinit var appDatabase: AppDatabase
    @Inject lateinit var startupRecordPolicy: StartupRecordPolicy

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        firebaseAnalytics.logEvent(
            FirebaseAnalytics.Event.APP_OPEN,
            Bundle()
        )
        if (BuildConfig.ENABLE_LOCAL_MCP_SERVER) {
            localMcpHttpServer.start()
        }
        applicationScope.launch {
            runCatching {
                startupRecordPolicy.onApplicationStart(appDatabase)
                startupHooks.forEach { hook -> hook.onApplicationCreate() }
            }.onFailure { error ->
                Log.e(TAG, "Startup hooks failed", error)
            }
        }
    }

    override fun onTerminate() {
        if (BuildConfig.ENABLE_LOCAL_MCP_SERVER) {
            localMcpHttpServer.stop()
        }
        super.onTerminate()
    }

    companion object {
        private const val TAG = "MedTrackApp"
    }
}
