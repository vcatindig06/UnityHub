package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class ServiceCalendarRulesTest {
    @Test
    fun monthGridStartsOnMondayAndEndsOnSunday() {
        val grid = ServiceCalendarRules.monthGrid(YearMonth.of(2026, 11), emptyList())

        assertEquals(DayOfWeek.MONDAY, grid.first().date.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, grid.last().date.dayOfWeek)
        assertEquals(0, grid.size % 7)
        assertTrue(grid.first().date.isBefore(LocalDate.of(2026, 11, 1)))
        assertTrue(grid.last().date.isAfter(LocalDate.of(2026, 11, 30)))
    }

    @Test
    fun monthGridMarksAdjacentMonthDaysAndIncludesServiceCounts() {
        val service = servicePlan("service-1", "2026-11-01", "09:00:00", "Sunday worship")
        val grid = ServiceCalendarRules.monthGrid(YearMonth.of(2026, 11), listOf(service))
        val serviceDay = grid.single { it.date == LocalDate.of(2026, 11, 1) }
        val adjacentDay = grid.first()

        assertTrue(serviceDay.isInDisplayedMonth)
        assertEquals(1, serviceDay.services.size)
        assertFalse(adjacentDay.isInDisplayedMonth)
    }

    @Test
    fun weekDatesAlwaysReturnMondayThroughSunday() {
        val week = ServiceCalendarRules.weekDates(LocalDate.of(2026, 11, 8))

        assertEquals(7, week.size)
        assertEquals(LocalDate.of(2026, 11, 2), week.first())
        assertEquals(LocalDate.of(2026, 11, 8), week.last())
        assertTrue(week.zipWithNext().all { (first, second) -> first.plusDays(1) == second })
    }

    @Test
    fun servicesOnSortByStartTimeThenTitleAndIgnoreUndatedOrInvalidPlans() {
        val later = servicePlan("later", "2026-11-08", "10:00:00", "Late service")
        val titleB = servicePlan("title-b", "2026-11-08", "09:00:00", "Beta service")
        val undated = servicePlan("undated", null, null, "Unscheduled")
        val invalid = servicePlan("invalid", "not-a-date", null, "Invalid date")
        val otherDay = servicePlan("other", "2026-11-09", null, "Other day")
        val titleA = servicePlan("title-a", "2026-11-08", "09:00:00", "Alpha service")

        val result = ServiceCalendarRules.servicesOn(
            LocalDate.of(2026, 11, 8),
            listOf(later, titleB, undated, invalid, otherDay, titleA)
        )

        assertEquals(listOf("title-a", "title-b", "later"), result.map { it.id })
    }

    private fun servicePlan(id: String, date: String?, time: String?, title: String) = ServicePlanDto(
        id = id,
        organizationId = "organization-1",
        title = title,
        serviceDate = date,
        startTime = time,
        notes = "",
        status = "draft",
        createdBy = "member-1",
        createdAt = "2026-11-06T00:00:00Z",
        updatedAt = "2026-11-06T00:00:00Z"
    )
}
