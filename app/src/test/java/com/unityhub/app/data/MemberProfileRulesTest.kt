package com.unityhub.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MemberProfileRulesTest {

    @Test
    fun summaryCountsOnlyActiveServicesOnOrAfterTodayAsUpcoming() {
        val services = listOf(
            service("Today", "2026-11-23", "published", "confirmed"),
            service("Cancelled", "2026-11-24", "cancelled", "declined"),
            service("Past", "2026-11-22", "completed", "pending"),
            service("Future draft", "2026-11-25", "draft", "pending"),
            service("Future published", "2026-11-26", "published", "declined"),
            service("No date", null, "published", "pending")
        )

        val summary = MemberProfileRules.summarize(
            services = services,
            rehearsals = emptyList(),
            today = LocalDate.parse("2026-11-23")
        )

        assertEquals(3, summary.upcomingServices)
        assertEquals(1, summary.confirmedServices)
        assertEquals(2, summary.declinedServices)
        assertEquals(3, summary.awaitingServiceResponses)
    }

    @Test
    fun summarySeparatesUpcomingRehearsalsFromCompletedAttendance() {
        val rehearsals = listOf(
            rehearsal("Future available", "2026-11-24", "scheduled", "available"),
            rehearsal("Today pending", "2026-11-23", "scheduled", "pending"),
            rehearsal("Cancelled", "2026-11-25", "cancelled", "available"),
            rehearsal("Attended", "2026-11-20", "completed", "attended"),
            rehearsal("Absent", "2026-11-19", "completed", "absent"),
            rehearsal("Not recorded", "2026-11-18", "completed", "available"),
            rehearsal("Also not recorded", "2026-11-17", "completed", "pending")
        )

        val summary = MemberProfileRules.summarize(
            services = emptyList(),
            rehearsals = rehearsals,
            today = LocalDate.parse("2026-11-23")
        )

        assertEquals(2, summary.upcomingRehearsals)
        assertEquals(1, summary.attendedRehearsals)
        assertEquals(1, summary.absentRehearsals)
        assertEquals(2, summary.unrecordedRehearsals)
    }

    @Test
    fun summaryCountsOnlySongsCreatedAndRecordedAttendanceAsContributions() {
        val summary = MemberProfileRules.summarize(
            services = emptyList(),
            rehearsals = listOf(
                rehearsal("Attended rehearsal", "2026-11-18", "completed", "attended"),
                rehearsal("Missed rehearsal", "2026-11-17", "completed", "absent")
            ),
            today = LocalDate.parse("2026-11-23"),
            songsCreated = 4
        )

        assertEquals(4, summary.songsCreated)
        assertEquals(1, summary.attendedRehearsals)
    }

    @Test
    fun recentServicesAreNewestFirstAndRespectTheLimit() {
        val services = listOf(
            service("Older", "2026-11-01", "completed", "confirmed"),
            service("Newest", "2026-11-20", "published", "pending"),
            service("Middle", "2026-11-10", "completed", "declined")
        )

        val result = MemberProfileRules.recentServices(services, limit = 2)

        assertEquals(listOf("Newest", "Middle"), result.map { it.serviceTitle })
    }

    @Test
    fun recentRehearsalsAreNewestFirstAndRespectTheLimit() {
        val rehearsals = listOf(
            rehearsal("Older", "2026-11-01", "completed", "attended"),
            rehearsal("Newest", "2026-11-20", "scheduled", "available"),
            rehearsal("Middle", "2026-11-10", "completed", "absent")
        )

        val result = MemberProfileRules.recentRehearsals(rehearsals, limit = 2)

        assertEquals(listOf("Newest", "Middle"), result.map { it.rehearsalTitle })
    }

    @Test
    fun responseLabelsRemainReadableAndDoNotExposeResponseNotes() {
        assertEquals("Confirmed", MemberProfileRules.serviceResponseLabel("confirmed"))
        assertEquals("Unable to serve", MemberProfileRules.serviceResponseLabel("declined"))
        assertEquals("Awaiting response", MemberProfileRules.serviceResponseLabel("pending"))
        assertEquals("Attended", MemberProfileRules.rehearsalResponseLabel("completed", "attended"))
        assertEquals("Absent", MemberProfileRules.rehearsalResponseLabel("completed", "absent"))
        assertEquals("Not recorded", MemberProfileRules.rehearsalResponseLabel("completed", "available"))
        assertEquals("Cancelled", MemberProfileRules.rehearsalResponseLabel("cancelled", "pending"))
    }

    private fun service(
        title: String,
        date: String?,
        serviceStatus: String,
        responseStatus: String
    ) = MemberServiceActivity(
        assignmentId = title,
        serviceTitle = title,
        serviceDate = date,
        startTime = "10:00",
        roleLabel = "Worship team",
        serviceStatus = serviceStatus,
        responseStatus = responseStatus
    )

    private fun rehearsal(
        title: String,
        date: String,
        rehearsalStatus: String,
        responseStatus: String
    ) = MemberRehearsalActivity(
        assignmentId = title,
        rehearsalTitle = title,
        rehearsalDate = date,
        startTime = "18:00",
        location = "Main hall",
        rehearsalStatus = rehearsalStatus,
        responseStatus = responseStatus
    )
}
