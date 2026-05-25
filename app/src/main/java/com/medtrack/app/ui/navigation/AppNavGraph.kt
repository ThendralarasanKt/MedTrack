package com.medtrack.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.medtrack.app.ui.assistant.AssistantScreen
import com.medtrack.app.ui.dashboard.DashboardScreen
import com.medtrack.app.ui.followup.FollowUpScreen
import com.medtrack.app.ui.patient.AddPatientScreen
import com.medtrack.app.ui.patient.PatientProfileScreen
import com.medtrack.app.ui.visit.NewVisitScreen
import com.medtrack.app.ui.visit.VisitDetailScreen

/**
 * AppNavGraph: The Central Switchboard for Navigation.
 */
@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onAddPatientClick = { 
                    navController.navigate(Screen.AddPatient.route) 
                },
                onPatientClick = { patientId ->
                    navController.navigate(Screen.PatientProfile.createRoute(patientId))
                },
                onFollowUpClick = { visitId ->
                    navController.navigate(Screen.VisitDetail.createRoute(visitId))
                },
                onAssistantClick = {
                    navController.navigate(Screen.Assistant.route)
                }
            )
        }

        composable(Screen.Assistant.route) {
            AssistantScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.AddPatient.route) {
            AddPatientScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.PatientProfile.route,
            arguments = listOf(
                navArgument("patientId") { type = NavType.IntType },
                navArgument("tab") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getInt("patientId") ?: 0
            val initialTab = backStackEntry.arguments?.getInt("tab") ?: 0
            PatientProfileScreen(
                patientId = patientId,
                initialTab = initialTab,
                onBack = { navController.popBackStack() },
                onNewVisitClick = { id ->
                    navController.navigate(Screen.NewVisit.createRoute(id))
                },
                onVisitClick = { visitId ->
                    navController.navigate(Screen.VisitDetail.createRoute(visitId))
                }
            )
        }
        
        composable(
            route = Screen.NewVisit.route,
            arguments = listOf(navArgument("patientId") { type = NavType.IntType })
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getInt("patientId") ?: 0
            NewVisitScreen(
                patientId = patientId,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.VisitDetail.route,
            arguments = listOf(navArgument("visitId") { type = NavType.IntType })
        ) { backStackEntry ->
            val visitId = backStackEntry.arguments?.getInt("visitId") ?: 0
            VisitDetailScreen(
                visitId = visitId,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.FollowUps.route) {
            FollowUpScreen(
                onFollowUpClick = { visitId ->
                    navController.navigate(Screen.VisitDetail.createRoute(visitId))
                }
            )
        }
    }
}
