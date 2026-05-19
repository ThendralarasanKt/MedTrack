package com.medtrack.app.data.db.model

data class VisitHistorySummary(
    val id: Int,
    val patientId: Int,
    val visitDate: String,
    val visitTime: String,
    val roomNo: String,
    val diagnosis: String,
    val medicineCount: Int,
    val taskCount: Int,
    val doneTaskCount: Int,
    val documentCount: Int,
    val followUpStatus: String?,
    val followUpReason: String?,
    val followUpDate: String?,
    val followUpTime: String?
)
