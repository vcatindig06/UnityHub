package com.unityhub.app.data

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RehearsalCalendarRulesTest {

    private fun rehearsal(
        endTime: String? = "20:00:00",
        notes: String = "Bring charts"
    ) = RehearsalDto(
        id = "rehearsal-1",
        organizationId = "org-1",
        title = "Sunday worship rehearsal",
        rehearsalDate = "2026-12-03",
        startTime = "18:30:00",
        endTime = endTime,
        location = "Main hall",
        notes = notes,
        status = "scheduled",
        servicePlanId = null,
        createdBy = "user-1",
        createdAt = "2026-11-24T09:00:00Z",
        updatedAt = "2026-11-24T09:00:00Z"
    )

    @Test
    fun buildsCalendarEventWithLocalZoneAndExistingTimes() {
        val zone = ZoneId.of("Asia/Manila")
        val draft = RehearsalCalendarRules.buildEvent(rehearsal(), zone)

        assertEquals("UnityHub · Sunday worship rehearsal", draft.title)
        assertEquals("Main hall", draft.location)
        assertTrue(draft.description.contains("Bring charts"))
        assertEquals(zone.id, draft.timeZoneId)
        assertEquals(
            Instant.parse("2026-12-03T10:30:00Z").toEpochMilli(),
            draft.beginAtMillis
        )
        assertEquals(
            Instant.parse("2026-12-03T12:00:00Z").toEpochMilli(),
            draft.endAtMillis
        )
    }

    @Test
    fun usesOneHourEndTimeWhenNoEndTimeWasScheduled() {
        val draft = RehearsalCalendarRules.buildEvent(
            rehearsal(endTime = null, notes = ""),
            ZoneId.of("Asia/Manila")
        )

        assertEquals(
            60L * 60L * 1000L,
            draft.endAtMillis - draft.beginAtMillis
        )
        assertFalseDescriptionNotes(draft.description)
    }

    @Test
    fun rejectsAnEndTimeThatIsNotAfterTheStart() {
        assertTrue(
            runCatching {
                RehearsalCalendarRules.buildEvent(
                    rehearsal(endTime = "18:00:00"),
                    ZoneId.of("Asia/Manila")
                )
            }.isFailure
        )
    }

    private fun assertFalseDescriptionNotes(description: String) {
        assertEquals("Added from UnityHub", description)
    }
}
