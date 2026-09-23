package com.medtrack.app.data.db.converters

import androidx.room.TypeConverter
import com.medtrack.app.data.care.model.ClinicalTime
import org.json.JSONObject

/**
 * Persists [ClinicalTime] as a compact JSON string in SQLite columns.
 */
class ClinicalTimeConverters {
    @TypeConverter
    fun fromClinicalTime(value: ClinicalTime?): String? {
        if (value == null) return null
        val json = JSONObject()
        json.put("precision", value.precision.name)
        if (value.instantEpochMillis != null) {
            json.put("instantEpochMillis", value.instantEpochMillis)
        } else {
            json.put("instantEpochMillis", JSONObject.NULL)
        }
        json.put("localDateIso", value.localDateIso ?: JSONObject.NULL)
        json.put("zoneId", value.zoneId ?: JSONObject.NULL)
        json.put("originalText", value.originalText ?: JSONObject.NULL)
        json.put("uncertaintyNote", value.uncertaintyNote ?: JSONObject.NULL)
        return json.toString()
    }

    @TypeConverter
    fun toClinicalTime(value: String?): ClinicalTime? {
        if (value.isNullOrBlank()) return null
        val json = JSONObject(value)
        val precision = ClinicalTime.Precision.valueOf(json.getString("precision"))
        return ClinicalTime(
            precision = precision,
            instantEpochMillis = if (json.isNull("instantEpochMillis")) {
                null
            } else {
                json.getLong("instantEpochMillis")
            },
            localDateIso = json.nullableString("localDateIso"),
            zoneId = json.nullableString("zoneId"),
            originalText = json.nullableString("originalText"),
            uncertaintyNote = json.nullableString("uncertaintyNote")
        )
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key).takeIf { it.isNotBlank() }
}
