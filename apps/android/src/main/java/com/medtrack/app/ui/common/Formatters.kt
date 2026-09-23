package com.medtrack.app.ui.common

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TwelveHourFormatter = DateTimeFormatter.ofPattern("h:mma", Locale.US)
private val DisplayDateFormatter = DateTimeFormatter.ofPattern("dd-MM-yy", Locale.US)

fun formatAppTime(value: String?): String {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank()) return ""

    val time = runCatching { LocalTime.parse(raw.take(5)) }
        .getOrElse {
            runCatching { LocalTime.parse(raw) }.getOrNull()
        } ?: return raw

    return time.format(TwelveHourFormatter).lowercase(Locale.US)
}

fun formatAppTime(value: LocalTime?): String =
    value?.format(TwelveHourFormatter)?.lowercase(Locale.US).orEmpty()

fun formatAppDate(value: String?): String {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank()) return ""

    val date = runCatching { LocalDate.parse(raw.take(10)) }
        .getOrElse {
            runCatching { LocalDateTime.parse(raw).toLocalDate() }.getOrNull()
        } ?: return raw

    return date.format(DisplayDateFormatter)
}

fun formatAppDate(value: LocalDate?): String =
    value?.format(DisplayDateFormatter).orEmpty()
