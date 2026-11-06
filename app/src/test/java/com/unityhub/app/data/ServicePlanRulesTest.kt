package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicePlanRulesTest {
    @Test
    fun titleIsTrimmedAndMustContainOneTo160Characters() {
        assertEquals("Sunday Worship", ServicePlanRules.normalizeTitle("  Sunday Worship  "))
        assertTrue(runCatching { ServicePlanRules.normalizeTitle("  ") }.isFailure)
        assertTrue(runCatching { ServicePlanRules.normalizeTitle("S".repeat(161)) }.isFailure)
    }

    @Test
    fun blankDateIsOptionalAndValidIsoDateIsPreserved() {
        assertNull(ServicePlanRules.normalizeDate("  "))
        assertEquals("2026-11-08", ServicePlanRules.normalizeDate(" 2026-11-08 "))
    }

    @Test
    fun malformedOrImpossibleDatesAreRejected() {
        assertTrue(runCatching { ServicePlanRules.normalizeDate("11/08/2026") }.isFailure)
        assertTrue(runCatching { ServicePlanRules.normalizeDate("2026-02-30") }.isFailure)
    }

    @Test
    fun blankTimeIsOptionalAndMinutePrecisionIsStoredWithSeconds() {
        assertNull(ServicePlanRules.normalizeTime(""))
        assertEquals("09:30:00", ServicePlanRules.normalizeTime("09:30"))
    }

    @Test
    fun secondsArePreservedAndInvalidTimesAreRejected() {
        assertEquals("21:05:17", ServicePlanRules.normalizeTime("21:05:17"))
        assertTrue(runCatching { ServicePlanRules.normalizeTime("25:00") }.isFailure)
        assertTrue(runCatching { ServicePlanRules.normalizeTime("9:30 AM") }.isFailure)
    }
}
