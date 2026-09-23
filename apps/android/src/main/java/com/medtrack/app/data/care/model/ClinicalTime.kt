package com.medtrack.app.data.care.model

/**
 * Clinical time with explicit precision. UNKNOWN never fabricates midnight.
 */
data class ClinicalTime(
    val precision: Precision,
    val instantEpochMillis: Long? = null,
    val localDateIso: String? = null,
    val zoneId: String? = null,
    val originalText: String? = null,
    val uncertaintyNote: String? = null
) {
    enum class Precision { INSTANT, DATE, UNKNOWN }

    init {
        when (precision) {
            Precision.INSTANT -> require(instantEpochMillis != null) {
                "INSTANT ClinicalTime requires instantEpochMillis"
            }
            Precision.DATE -> require(!localDateIso.isNullOrBlank()) {
                "DATE ClinicalTime requires localDateIso"
            }
            Precision.UNKNOWN -> { /* neither required */ }
        }
    }

    companion object {
        fun unknown(originalText: String? = null, note: String? = null) =
            ClinicalTime(
                precision = Precision.UNKNOWN,
                originalText = originalText,
                uncertaintyNote = note
            )

        fun date(localDateIso: String, zoneId: String? = null) =
            ClinicalTime(
                precision = Precision.DATE,
                localDateIso = localDateIso,
                zoneId = zoneId
            )

        fun instant(epochMillis: Long, zoneId: String = "UTC") =
            ClinicalTime(
                precision = Precision.INSTANT,
                instantEpochMillis = epochMillis,
                zoneId = zoneId
            )
    }
}
