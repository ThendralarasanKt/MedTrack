package com.medtrack.app.ai

import javax.inject.Inject
import org.json.JSONObject

class FakeLocalLlmEngine @Inject constructor() : LocalLlmEngine {
    override suspend fun generate(prompt: String): String {
        val request = extractUserRequest(prompt)
        val roomChange = parseRoomChange(request)

        return if (roomChange != null) {
            JSONObject()
                .put("type", "tool_call")
                .put("tool", "update_patient_room")
                .put(
                    "arguments",
                    JSONObject()
                        .put("patientName", roomChange.patientName)
                        .put("currentRoomNumber", roomChange.currentRoomNumber)
                        .put("newRoomNumber", roomChange.newRoomNumber)
                )
                .toString()
        } else {
            JSONObject()
                .put("type", "answer")
                .put("message", "I can help with patient room changes. Try: move Ramesh from 434A to 530B.")
                .toString()
        }
    }


    private fun extractUserRequest(prompt: String): String {
        val marker = "User request:"
        val markerIndex = prompt.lastIndexOf(marker, ignoreCase = true)
        return if (markerIndex >= 0) {
            prompt.substring(markerIndex + marker.length).trim()
        } else {
            prompt.trim()
        }
    }

    private fun parseRoomChange(request: String): RoomChange? {
        val patterns = listOf(
            """(?i)\b(?:move|change|update|shift|transfer)\s+(.+?)\s+from\s+(?:room\s+)?(?:no\.?\s*)?([a-z0-9-]+)\s+(?:to|into)\s+(?:room\s+)?(?:no\.?\s*)?([a-z0-9-]+)\b""",
            """(?i)\b(?:move|change|update|shift|transfer)\s+(.+?)\s+(?:room|room\s+no|room\s+number)\s+from\s+([a-z0-9-]+)\s+(?:to|into)\s+([a-z0-9-]+)\b""",
            """(?i)\b(?:room|room\s+no|room\s+number)\s+of\s+(.+?)\s+(?:from|is\s+from)\s+([a-z0-9-]+)\s+(?:to|into)\s+([a-z0-9-]+)\b"""
        )

        val match = patterns
            .asSequence()
            .mapNotNull { Regex(it).find(request) }
            .firstOrNull()
            ?: return null

        return RoomChange(
            patientName = normalizePatientName(match.groupValues[1]),
            currentRoomNumber = match.groupValues[2].trim().uppercase(),
            newRoomNumber = match.groupValues[3].trim().uppercase()
        )
    }

    private fun normalizePatientName(value: String): String =
        value
            .trim()
            .removePrefix("patient ")
            .trim()
            .split(Regex("\\s+"))
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }

    private data class RoomChange(
        val patientName: String,
        val currentRoomNumber: String,
        val newRoomNumber: String
    )
}
