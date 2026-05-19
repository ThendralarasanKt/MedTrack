package com.medtrack.app.data.db.converters

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * DateConverters: The "Translator" for our database.
 * 
 * WHY: SQLite doesn't support Date types. We convert them to Strings 
 * for storage and back to Objects for our Kotlin code.
 */
class DateConverters {
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter = DateTimeFormatter.ISO_LOCAL_TIME

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? = value?.format(dateTimeFormatter)

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? = value?.let { 
        LocalDateTime.parse(it, dateTimeFormatter) 
    }

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.format(dateFormatter)

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { 
        LocalDate.parse(it, dateFormatter) 
    }

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.format(timeFormatter)

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let { 
        LocalTime.parse(it, timeFormatter) 
    }
}
