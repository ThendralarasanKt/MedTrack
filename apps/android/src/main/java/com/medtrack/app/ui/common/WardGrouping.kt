package com.medtrack.app.ui.common

fun wardGroupLabel(roomNo: String?): String {
    val room = roomNo?.trim().orEmpty()
    if (room.isBlank()) return "Unassigned"
    val upper = room.uppercase()
    return when {
        "ICU" in upper -> "ICU"
        "WARD" in upper -> room.substringBefore("•").trim().ifBlank { room }
        room.first().isDigit() -> "Floor ${room.first()}"
        else -> room
    }
}

fun isIcuRoom(roomNo: String?): Boolean =
    roomNo?.contains("ICU", ignoreCase = true) == true

fun acuityLabel(diagnosis: String?, pendingTaskCount: Int, roomNo: String?): String {
    val text = diagnosis.orEmpty().lowercase()
    return when {
        isIcuRoom(roomNo) || text.contains("sepsis") || text.contains("aki") -> "High acuity"
        pendingTaskCount > 0 || text.contains("fever") || text.contains("watch") -> "Watch"
        else -> "Stable"
    }
}
