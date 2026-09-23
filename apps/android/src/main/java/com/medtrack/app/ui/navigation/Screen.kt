package com.medtrack.app.ui.navigation

sealed class Screen(val route: String) {
    object Census : Screen("census")
    object Rounds : Screen("rounds")
    object Capture : Screen("capture") {
        fun createRoute(admissionId: String? = null) =
            if (admissionId.isNullOrBlank()) route else "capture/$admissionId"
    }
    object Inbox : Screen("inbox")
    object Handover : Screen("handover")
    object Admit : Screen("admit")

    object PatientHub : Screen("patient_hub/{admissionId}") {
        fun createRoute(admissionId: String) = "patient_hub/$admissionId"
    }

    object Profile : Screen("profile")

    companion object {
        // Use literals here — referencing Census.route during Screen.<clinit> NPEs
        // nested objects that are still null (sealed-class init order).
        val workspaceRoutes = setOf(
            "census",
            "rounds",
            "capture",
            "capture/{admissionId}",
            "inbox",
            "handover"
        )

        fun showsBottomBar(route: String?): Boolean = route in workspaceRoutes
    }
}
