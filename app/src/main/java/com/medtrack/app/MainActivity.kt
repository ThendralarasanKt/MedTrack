package com.medtrack.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.medtrack.app.notification.FollowUpNotificationScheduler
import com.medtrack.app.ui.navigation.AppNavGraph
import com.medtrack.app.ui.navigation.Screen
import com.medtrack.app.ui.theme.MedTrackTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var followUpNotificationScheduler: FollowUpNotificationScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        followUpNotificationScheduler.createNotificationChannel()
        setContent {
            MedTrackTheme {
                // rememberNavController() creates a controller that survives configuration changes (like rotation)
                val navController = rememberNavController()
                LaunchedEffect(Unit) {
                    when (intent.getStringExtra(EXTRA_DESTINATION)) {
                        DESTINATION_FOLLOW_UPS -> navController.navigate(Screen.FollowUps.route)
                        DESTINATION_PATIENT -> {
                            val patientId = intent.getIntExtra(EXTRA_PATIENT_ID, 0)
                            if (patientId > 0) {
                                navController.navigate(Screen.PatientProfile.createRoute(patientId))
                            }
                        }
                    }
                }
                
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    // We pass the padding to our NavGraph so our screens don't get hidden behind the status bar
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AppNavGraph(
                            navController = navController
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_DESTINATION = "destination"
        private const val EXTRA_PATIENT_ID = "patient_id"
        private const val DESTINATION_FOLLOW_UPS = "follow_ups"
        private const val DESTINATION_PATIENT = "patient"

        fun intentForPatient(context: Context, patientId: Int): PendingIntent =
            PendingIntent.getActivity(
                context,
                patientId,
                Intent(context, MainActivity::class.java).apply {
                    putExtra(EXTRA_DESTINATION, DESTINATION_PATIENT)
                    putExtra(EXTRA_PATIENT_ID, patientId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )

        fun intentForFollowUps(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    putExtra(EXTRA_DESTINATION, DESTINATION_FOLLOW_UPS)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )

        private fun immutableFlag(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }
}
