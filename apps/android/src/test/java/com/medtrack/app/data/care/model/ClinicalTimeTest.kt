package com.medtrack.app.data.care.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ClinicalTimeTest {
    @Test
    fun instantRequiresEpoch() {
        assertThrows(IllegalArgumentException::class.java) {
            ClinicalTime(precision = ClinicalTime.Precision.INSTANT)
        }
    }

    @Test
    fun dateRequiresLocalDate() {
        assertThrows(IllegalArgumentException::class.java) {
            ClinicalTime(precision = ClinicalTime.Precision.DATE)
        }
    }

    @Test
    fun unknownAllowsEmptyFields() {
        val time = ClinicalTime.unknown("bed 12 sometime today")
        assertEquals(ClinicalTime.Precision.UNKNOWN, time.precision)
        assertEquals("bed 12 sometime today", time.originalText)
    }

    @Test
    fun instantFactory() {
        val time = ClinicalTime.instant(1_700_000_000_000L, "Asia/Kolkata")
        assertEquals(ClinicalTime.Precision.INSTANT, time.precision)
        assertEquals(1_700_000_000_000L, time.instantEpochMillis)
        assertEquals("Asia/Kolkata", time.zoneId)
    }
}
