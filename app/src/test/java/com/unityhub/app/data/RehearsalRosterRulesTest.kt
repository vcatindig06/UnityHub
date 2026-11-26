package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RehearsalRosterRulesTest {
    @Test
    fun summaryCountsAvailabilityAndAttendanceWithoutLosingUnrecordedRows() {
        val assignments = listOf(
            assignment("a", "available"),
            assignment("b", "unavailable"),
            assignment("c", "pending"),
            assignment("d", "attended"),
            assignment("e", "absent"),
            assignment("f", "unexpected")
        )

        val summary = RehearsalRosterRules.summary(assignments)

        assertEquals(6, summary.invited)
        assertEquals(1, summary.available)
        assertEquals(1, summary.unavailable)
        assertEquals(1, summary.awaiting)
        assertEquals(1, summary.attended)
        assertEquals(1, summary.absent)
        assertEquals(4, summary.notRecorded)
    }

    @Test
    fun thirtyDayPeriodIncludesThirtyCalendarDatesAndExcludesFutureRows() {
        val today = LocalDate.parse("2026-11-27")
        val dates = listOf(
            "2026-11-27",
            "2026-10-29",
            "2026-10-28",
            "2026-11-28"
        )

        val visible = dates.filter {
            RehearsalRosterRules.includesDate(it, AttendancePeriod.LAST_30_DAYS, today)
        }

        assertEquals(listOf("2026-11-27", "2026-10-29"), visible)
    }

    @Test
    fun ninetyDayPeriodIncludesItsBoundaryButNotThePreviousDay() {
        val today = LocalDate.parse("2026-11-27")

        assertTrue(RehearsalRosterRules.includesDate("2026-08-30", AttendancePeriod.LAST_90_DAYS, today))
        assertTrue(!RehearsalRosterRules.includesDate("2026-08-29", AttendancePeriod.LAST_90_DAYS, today))
    }

    @Test
    fun allTimePeriodKeepsOlderCompletedAttendanceRows() {
        val today = LocalDate.parse("2026-11-27")

        assertTrue(RehearsalRosterRules.includesDate("2024-01-01", AttendancePeriod.ALL_TIME, today))
    }

    @Test
    fun invalidAttendanceDatesAreExcludedFromPeriodSummaries() {
        val today = LocalDate.parse("2026-11-27")

        assertTrue(!RehearsalRosterRules.includesDate("not-a-date", AttendancePeriod.ALL_TIME, today))
    }

    @Test
    fun attendanceRateUsesOnlyAttendedAndAbsentOutcomes() {
        assertEquals(75, RehearsalRosterRules.attendanceRate(attended = 3, absent = 1))
    }

    @Test
    fun attendanceRateIsNotInventedWhenNoAttendanceWasRecorded() {
        assertEquals(null, RehearsalRosterRules.attendanceRate(attended = 0, absent = 0))
    }

    @Test
    fun scheduledRehearsalOffersAvailabilityFilters() {
        assertEquals(
            listOf(
                RehearsalRosterFilter.ALL,
                RehearsalRosterFilter.AWAITING,
                RehearsalRosterFilter.AVAILABLE,
                RehearsalRosterFilter.UNAVAILABLE
            ),
            RehearsalRosterRules.filtersFor("scheduled")
        )
    }

    @Test
    fun completedRehearsalOffersAttendanceFilters() {
        assertEquals(
            listOf(
                RehearsalRosterFilter.ALL,
                RehearsalRosterFilter.ATTENDED,
                RehearsalRosterFilter.ABSENT,
                RehearsalRosterFilter.NOT_RECORDED
            ),
            RehearsalRosterRules.filtersFor("completed")
        )
    }

    @Test
    fun filtersAvailabilityAndSearchesMemberNamesWithoutCaseSensitivity() {
        val assignments = listOf(
            assignment("alex", "available", member = "member-a"),
            assignment("jamie", "available", member = "member-b"),
            assignment("taylor", "unavailable", member = "member-c"),
            assignment("casey", "pending", member = "member-d")
        )

        val result = RehearsalRosterRules.filter(
            assignments = assignments,
            memberNames = mapOf(
                "member-a" to "Alex Rivera",
                "member-b" to "Jamie Cruz",
                "member-c" to "Taylor Santos",
                "member-d" to "Casey Lee"
            ),
            selectedFilter = RehearsalRosterFilter.AVAILABLE,
            query = "  JAMIE "
        )

        assertEquals(listOf("jamie"), result.map { it.id })
    }

    @Test
    fun notRecordedIncludesInviteesWithoutFinalAttendanceAndEmptySearchShowsAll() {
        val assignments = listOf(
            assignment("attended", "attended"),
            assignment("absent", "absent"),
            assignment("available", "available"),
            assignment("pending", "pending")
        )

        val unrecorded = RehearsalRosterRules.filter(
            assignments,
            emptyMap(),
            RehearsalRosterFilter.NOT_RECORDED,
            ""
        )
        val all = RehearsalRosterRules.filter(
            assignments,
            emptyMap(),
            RehearsalRosterFilter.ALL,
            ""
        )

        assertEquals(setOf("available", "pending"), unrecorded.map { it.id }.toSet())
        assertEquals(4, all.size)
        assertTrue(all.all { it.memberId == "member-1" })
    }

    private fun assignment(
        id: String,
        response: String,
        member: String = "member-1"
    ) = RehearsalAssignmentDto(
        id = id,
        organizationId = "organization-1",
        rehearsalId = "rehearsal-1",
        memberId = member,
        responseStatus = response,
        responseNote = "",
        assignedBy = "leader",
        respondedAt = null,
        createdAt = "2026-11-01T00:00:00Z"
    )
}
