package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceRunSheetRulesTest {
    @Test
    fun totalsOnlyKnownDurationsAndCountsUnscheduledItems() {
        val summary = ServiceRunSheetRules.summarize(
            itemDurations = listOf(5, 18, null, 12, null),
            serviceStartTime = "09:00:00"
        )

        assertEquals(35, summary.totalMinutes)
        assertEquals(2, summary.itemsWithoutDuration)
        assertNull(summary.estimatedFinishTime)
    }

    @Test
    fun calculatesEstimatedFinishWhenEveryItemHasDuration() {
        val summary = ServiceRunSheetRules.summarize(
            itemDurations = listOf(10, 25, 8, 17),
            serviceStartTime = "09:30:00"
        )

        assertEquals(60, summary.totalMinutes)
        assertEquals(0, summary.itemsWithoutDuration)
        assertEquals("10:30 AM", summary.estimatedFinishTime)
    }

    @Test
    fun finishEstimateCorrectlyCrossesMidnight() {
        val summary = ServiceRunSheetRules.summarize(
            itemDurations = listOf(45, 30),
            serviceStartTime = "23:30:00"
        )

        assertEquals("12:45 AM (+1 day)", summary.estimatedFinishTime)
    }

    @Test
    fun finishEstimateCanCrossMoreThanOneDay() {
        val summary = ServiceRunSheetRules.summarize(
            itemDurations = listOf(600, 600, 600),
            serviceStartTime = "06:00:00"
        )

        assertEquals("12:00 AM (+2 days)", summary.estimatedFinishTime)
    }

    @Test
    fun emptyItemsOrMissingStartTimeDoNotInventFinishTimes() {
        assertNull(ServiceRunSheetRules.summarize(emptyList(), "09:00").estimatedFinishTime)
        assertNull(ServiceRunSheetRules.summarize(listOf(15, 20), null).estimatedFinishTime)
        assertNull(ServiceRunSheetRules.summarize(listOf(15, 20), "not-a-time").estimatedFinishTime)
    }

    @Test
    fun formatsRuntimeAsHoursAndMinutes() {
        assertEquals("0 min", ServiceRunSheetRules.formatDuration(0))
        assertEquals("42 min", ServiceRunSheetRules.formatDuration(42))
        assertEquals("1 hr", ServiceRunSheetRules.formatDuration(60))
        assertEquals("1 hr 15 min", ServiceRunSheetRules.formatDuration(75))
        assertEquals("2 hr 5 min", ServiceRunSheetRules.formatDuration(125))
    }
}
