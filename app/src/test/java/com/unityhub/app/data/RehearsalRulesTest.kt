package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RehearsalRulesTest {
    @Test
    fun normalizesTitleAndRejectsInvalidLengths() {
        assertEquals("Sunday rehearsal", RehearsalRules.normalizeTitle("  Sunday   rehearsal  "))
        assertTrue(runCatching { RehearsalRules.normalizeTitle(" ") }.isFailure)
        assertTrue(runCatching { RehearsalRules.normalizeTitle("x".repeat(121)) }.isFailure)
    }

    @Test
    fun acceptsIsoDatesAndRejectsMalformedOrImpossibleDates() {
        assertEquals("2026-11-15", RehearsalRules.normalizeDate(" 2026-11-15 "))
        assertTrue(runCatching { RehearsalRules.normalizeDate("11/15/2026") }.isFailure)
        assertTrue(runCatching { RehearsalRules.normalizeDate("2026-02-30") }.isFailure)
    }

    @Test
    fun normalizesTimesAndValidatesEndAfterStart() {
        assertEquals("18:30:00", RehearsalRules.normalizeTime("18:30"))
        assertEquals("20:00:00", RehearsalRules.normalizeEndTime("18:30", "20:00"))
        assertNull(RehearsalRules.normalizeEndTime("18:30", ""))
        assertTrue(runCatching { RehearsalRules.normalizeEndTime("18:30", "18:30") }.isFailure)
        assertTrue(runCatching { RehearsalRules.normalizeEndTime("18:30", "17:45") }.isFailure)
        assertTrue(runCatching { RehearsalRules.normalizeTime("25:00") }.isFailure)
    }

    @Test
    fun locationAndNotesRespectDatabaseLimits() {
        assertEquals("Main hall", RehearsalRules.normalizeLocation(" Main hall "))
        assertEquals("Bring charts", RehearsalRules.normalizeNotes(" Bring charts "))
        assertTrue(runCatching { RehearsalRules.normalizeLocation("x".repeat(161)) }.isFailure)
        assertTrue(runCatching { RehearsalRules.normalizeNotes("x".repeat(1001)) }.isFailure)
    }

    @Test
    fun permitsOnlySupportedStatusesAndAvailabilityResponses() {
        assertEquals("scheduled", RehearsalRules.normalizeStatus(" SCHEDULED "))
        assertEquals("completed", RehearsalRules.normalizeStatus("completed"))
        assertTrue(RehearsalRules.canRespond("scheduled"))
        assertTrue(!RehearsalRules.canRespond("completed"))
        assertTrue(RehearsalRules.isAvailabilityResponse("available"))
        assertTrue(RehearsalRules.isAvailabilityResponse("unavailable"))
        assertTrue(!RehearsalRules.isAvailabilityResponse("attended"))
        assertTrue(RehearsalRules.isAttendanceResult("attended"))
        assertTrue(RehearsalRules.isAttendanceResult("absent"))
        assertTrue(!RehearsalRules.isAttendanceResult("available"))
        assertTrue(runCatching { RehearsalRules.normalizeStatus("pending") }.isFailure)
    }

    @Test
    fun formatsDateTimeAndResponseLabels() {
        assertEquals("6:30 PM", RehearsalRules.formatTime("18:30:00"))
        assertEquals("Sun, Nov 15, 2026", RehearsalRules.formatDate("2026-11-15"))
        assertEquals("Available", RehearsalRules.responseLabel("available"))
        assertEquals("Awaiting response", RehearsalRules.responseLabel("pending"))
    }
}
