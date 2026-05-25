package com.medtrack.app.data.db.model

data class PatientVisitContext(
    val patientId: Int,
    val patientName: String,
    val age: Int,
    val sex: String,
    val contact: String,
    val address: String,
    val medHistory: String,
    val visitId: Int,
    val visitDate: String,
    val visitTime: String,
    val roomNo: String,
    val symptoms: String,
    val diagnosis: String,
    val progressNotes: String
)
