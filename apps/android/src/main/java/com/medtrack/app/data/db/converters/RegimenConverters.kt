package com.medtrack.app.data.db.converters

import androidx.room.TypeConverter
import com.medtrack.app.data.care.model.Regimen
import org.json.JSONArray
import org.json.JSONObject

class RegimenConverters {
    @TypeConverter
    fun fromRegimen(value: Regimen?): String? {
        if (value == null) return null
        val json = JSONObject()
        json.put("kind", value.kind.name)
        json.put("originalText", value.originalText)
        if (value.times != null) {
            json.put("times", JSONArray(value.times))
        } else {
            json.put("times", JSONObject.NULL)
        }
        if (value.intervalMinutes != null) {
            json.put("intervalMinutes", value.intervalMinutes)
        } else {
            json.put("intervalMinutes", JSONObject.NULL)
        }
        json.put("condition", value.condition ?: JSONObject.NULL)
        json.put("maximumDoseText", value.maximumDoseText ?: JSONObject.NULL)
        json.put("zoneId", value.zoneId ?: JSONObject.NULL)
        return json.toString()
    }

    @TypeConverter
    fun toRegimen(value: String?): Regimen? {
        if (value.isNullOrBlank()) return null
        val json = JSONObject(value)
        val times = if (json.isNull("times")) {
            null
        } else {
            val array = json.getJSONArray("times")
            (0 until array.length()).map { array.getString(it) }
        }
        return Regimen(
            kind = Regimen.Kind.valueOf(json.getString("kind")),
            originalText = json.getString("originalText"),
            times = times,
            intervalMinutes = if (json.isNull("intervalMinutes")) null else json.getInt("intervalMinutes"),
            condition = json.nullableString("condition"),
            maximumDoseText = json.nullableString("maximumDoseText"),
            zoneId = json.nullableString("zoneId")
        )
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key).takeIf { it.isNotBlank() }
}
