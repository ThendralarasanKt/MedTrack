package com.medtrack.app.data.care.model

/**
 * Embedded medication schedule. TEXT_ONLY is never silently normalized.
 */
data class Regimen(
    val kind: Kind,
    val originalText: String,
    val times: List<String>? = null,
    val intervalMinutes: Int? = null,
    val condition: String? = null,
    val maximumDoseText: String? = null,
    val zoneId: String? = null
) {
    enum class Kind { FIXED_TIMES, INTERVAL, AS_NEEDED, TEXT_ONLY }

    init {
        when (kind) {
            Kind.FIXED_TIMES -> require(!times.isNullOrEmpty() && !zoneId.isNullOrBlank()) {
                "FIXED_TIMES regimen requires times and zoneId"
            }
            Kind.INTERVAL -> require(intervalMinutes != null && intervalMinutes > 0) {
                "INTERVAL regimen requires positive intervalMinutes"
            }
            Kind.AS_NEEDED, Kind.TEXT_ONLY -> { /* originalText only */ }
        }
    }

    companion object {
        fun textOnly(originalText: String) =
            Regimen(kind = Kind.TEXT_ONLY, originalText = originalText)
    }
}
