package com.medtrack.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.medtrack.app.ui.admit.AdmitScreen
import com.medtrack.app.ui.conversation.ConversationScreen
import com.medtrack.app.ui.census.CensusScreen
import com.medtrack.app.ui.handover.HandoverScreen
import com.medtrack.app.ui.hub.PatientHubScreen
import com.medtrack.app.ui.inbox.InboxScreen
import com.medtrack.app.ui.profile.ProfileScreen
import com.medtrack.app.ui.rounds.RoundsScreen

@Composable
fun AppNavGraph(navController: NavHostController, onSignedOut: () -> Unit) {
    NavHost(
        navController = navController,
        startDestination = Screen.Census.route
    ) {
        composable(Screen.Census.route) {
            CensusScreen(
                onAdmitClick = { navController.navigate(Screen.Admit.route) },
                onPatientClick = { admissionId ->
                    navController.navigate(Screen.PatientHub.createRoute(admissionId))
                },
                onCaptureClick = { navController.navigate(Screen.Capture.route) },
                onRoundsClick = { navController.navigate(Screen.Rounds.route) },
                onProfileClick = { navController.navigate(Screen.Profile.route) }
            )
        }
        composable(Screen.Rounds.route) {
            RoundsScreen(
                onPatientClick = { admissionId ->
                    navController.navigate(Screen.PatientHub.createRoute(admissionId))
                }
            )
        }
        composable(Screen.Capture.route) {
            ConversationScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = "capture/{admissionId}",
            arguments = listOf(navArgument("admissionId") { type = NavType.StringType })
        ) {
            ConversationScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Inbox.route) {
            InboxScreen(
                onOpenPatient = { admissionId ->
                    navController.navigate(Screen.PatientHub.createRoute(admissionId))
                }
            )
        }
        composable(Screen.Handover.route) { HandoverScreen() }
        composable(Screen.Admit.route) {
            AdmitScreen(
                onBack = { navController.popBackStack() },
                onAdmitted = { admissionId ->
                    navController.popBackStack()
                    navController.navigate(Screen.PatientHub.createRoute(admissionId))
                }
            )
        }
        composable(
            route = Screen.PatientHub.route,
            arguments = listOf(navArgument("admissionId") { type = NavType.StringType })
        ) { entry ->
            val admissionId = entry.arguments?.getString("admissionId").orEmpty()
            PatientHubScreen(
                admissionId = admissionId,
                onBack = { navController.popBackStack() },
                onHandover = { navController.navigate(Screen.Handover.route) },
                onCapture = { navController.navigate(Screen.Capture.createRoute(admissionId)) }
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onSignedOut = onSignedOut
            )
        }
    }
}
