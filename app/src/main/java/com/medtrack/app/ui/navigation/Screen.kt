package com.medtrack.app.ui.navigation

/**
 * Screen: Defines all possible destinations in the app.
 * 
 * WHY: Using a sealed class prevents typos in route names 
 * and makes it easy to pass arguments (like patientId).
 */
sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object AddPatient : Screen("add_patient")
    object Assistant : Screen("assistant")
    
    object PatientProfile : Screen("patient_profile/{patientId}?tab={tab}") {
        fun createRoute(patientId: Int, tab: Int = 0) = "patient_profile/$patientId?tab=$tab"
    }
    
    object NewVisit : Screen("new_visit/{patientId}") {
        fun createRoute(patientId: Int) = "new_visit/$patientId"
    }
    
    object VisitDetail : Screen("visit_detail/{visitId}") {
        fun createRoute(visitId: Int) = "visit_detail/$visitId"
    }

    object FollowUps : Screen("follow_ups")
}
