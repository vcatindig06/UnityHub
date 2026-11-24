package com.unityhub.app.data

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RehearsalMonthCalendarRulesTest {

    private fun rehearsal(
        id: String,
        date: String,
        time: String = "18:00:00",
        status: String = "scheduled",
        title: String = id
    ) = RehearsalDto(
        id = id,
        organizationId = "org-1",
        title = title,
        rehearsalDate = date,
        startTime = time,
        endTime = null,
        location = "",
        notes = "",
        status = status,
        servicePlanId = null,
        createdBy = "user-1",
        createdAt = "2026-11-01T00:00:00Z",
        updatedAt = "2026-11-01T00:00:00Z"
    )

    @Test
    fun monthGridStartsOnMondayAndIncludesWholeWeeks() {
        val grid = RehearsalMonthCalendarRules.monthGrid(
            YearMonth.of(2026, 11),
            emptyList()
        )

        assertEquals(42, grid.size)
        assertEquals(LocalDate.parse("2026-10-26"), grid.first().date)
        assertEquals(LocalDate.parse("2026-12-06"), grid.last().date)
        assertFalse(grid.first().isInDisplayedMonth)
        assertTrue(grid.first { it.date == LocalDate.parse("2026-11-01") }.isInDisplayedMonth)
        assertFalse(grid.last().isInDisplayedMonth)
    }

    @Test
    fun calendarMarksDatesAndSortsSameDayEventsByStartTime() {
        val later = rehearsal("later", "2026-11-25", time = "19:00:00")
        val earlier = rehearsal("earlier", "2026-11-25", time = "18:00:00")
        val grid = RehearsalCalendarRules.monthGrid(
            YearMonth.of(2026, 11),
            listOf(later, earlier)
        )
        val day = grid.first { it.date == LocalDate.parse("2026-11-25") }

        assertTrue(day.isInDisplayedMonth)
        assertEquals(listOf("earlier", "later"), day.rehearsals.map { it.id })
    }

    @Test
    fun upcomingAndPastFiltersSeparateScheduledAndInactiveRehearsals() {
        val rehearsals = listOf(
            rehearsal("yesterday", "2026-11-23"),
            rehearsal("today", "2026-11-24"),
            rehearsal("tomorrow", "2026-11-25"),
            rehearsal("completed-future", "2026-11-26", status = "completed"),
            rehearsal("cancelled-future", "2026-11-27", status = "cancelled")
        )

        val upcoming = RehearsalCalendarRules.filter(
            rehearsals,
            RehearsalTimeFilter.UPCOMING,
            LocalDate.parse("2026-11-24")
        )
        val past = RehearsalCalendarRules.filter(
            rehearsals,
            RehearsalTimeFilter.PAST,
            LocalDate.parse("2026-11-24")
        )
        val all = RehearsalCalendarRules.filter(
            rehearsals,
            RehearsalTimeFilter.ALL,
            LocalDate.parse("2026-11-24")
        )

        assertEquals(listOf("today", "tomorrow"), upcoming.map { it.id })
        assertEquals(
            listOf("cancelled-future", "completed-future", "yesterday"),
            past.map { it.id }
        )
        assertEquals(5, all.size)
    }

    @Test
    fun invalidDatesAreExcludedFromCalendarFiltering() {
        val malformed = rehearsal("bad", "not-a-date")
        val valid = rehearsal("valid", "2026-11-25")
        val result = RehearsalCalendarRules.filter(
            listOf(malformed, valid),
            RehearsalTimeFilter.ALL,
            LocalDate.parse("2026-11-24")
        )

        assertEquals(listOf("valid"), result.map { it.id })
    }
}
