package com.unityhub.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class RehearsalCalendarEventDraft(
    val title: String,
    val location: String,
    val description: String,
    val beginAtMillis: Long,
    val endAtMillis: Long,
    val timeZoneId: String
)

internal object RehearsalCalendarRules {
    fun buildEvent(
        rehearsal: RehearsalDto,
        zoneId: ZoneId
    ): RehearsalCalendarEventDraft {
        require(rehearsal.id.isNotBlank())
        val date = LocalDate.parse(rehearsal.rehearsalDate)
        val start = LocalDateTime.of(date, LocalTime.parse(rehearsal.startTime))
        val end = rehearsal.endTime
            ?.takeIf(String::isNotBlank)
            ?.let { LocalDateTime.of(date, LocalTime.parse(it)) }
            ?: start.plusHours(1)

        require(end.isAfter(start)) {
            "The rehearsal end time must be later than its start time."
        }

        val description = buildString {
            append("Added from UnityHub")
            if (rehearsal.notes.isNotBlank()) {
                append("\n\n")
                append(rehearsal.notes.trim())
            }
        }

        return RehearsalCalendarEventDraft(
            title = "UnityHub · ${rehearsal.title}",
            location = rehearsal.location,
            description = description,
            beginAtMillis = start.atZone(zoneId).toInstant().toEpochMilli(),
            endAtMillis = end.atZone(zoneId).toInstant().toEpochMilli(),
            timeZoneId = zoneId.id
        )
    }
}
