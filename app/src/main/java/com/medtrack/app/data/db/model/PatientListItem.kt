package com.medtrack.app.data.db.model

data class PatientListItem(
    val id: Int,
    val name: String,
    val age: Int,
    val sex: String,
    val contact: String,
    val address: String,
    val medHistory: String,
    val photoPath: String,
    val latestRoomNo: String?
)
