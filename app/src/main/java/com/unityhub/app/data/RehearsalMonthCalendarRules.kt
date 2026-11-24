package com.unityhub.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class RehearsalTimeFilter(val label: String) {
    UPCOMING("Upcoming"),
    PAST("Past"),
    ALL("All")
}

enum class RehearsalStatusFilter(val label: String) {
    ALL("Any status"),
    SCHEDULED("Scheduled"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled")
}

data class RehearsalMonthCalendarDay(
    val date: LocalDate,
    val isInDisplayedMonth: Boolean,
    val rehearsals: List<RehearsalDto>
)

object RehearsalMonthCalendarRules {

    fun filter(
        rehearsals: List<RehearsalDto>,
        filter: RehearsalTimeFilter,
        today: LocalDate = LocalDate.now(),
        statusFilter: RehearsalStatusFilter = RehearsalStatusFilter.ALL,
        searchQuery: String = ""
    ): List<RehearsalDto> {
        val normalizedQuery = searchQuery.trim().lowercase(Locale.ROOT)
        val matching = rehearsals.filter { rehearsal ->
            val date = runCatching { LocalDate.parse(rehearsal.rehearsalDate) }.getOrNull()
            val matchesTime = date != null && when (filter) {
                RehearsalTimeFilter.UPCOMING ->
                    date >= today && rehearsal.status == "scheduled"
                RehearsalTimeFilter.PAST ->
                    date < today || rehearsal.status != "scheduled"
                RehearsalTimeFilter.ALL -> true
            }
            val matchesStatus = when (statusFilter) {
                RehearsalStatusFilter.ALL -> true
                RehearsalStatusFilter.SCHEDULED -> rehearsal.status == "scheduled"
                RehearsalStatusFilter.COMPLETED -> rehearsal.status == "completed"
                RehearsalStatusFilter.CANCELLED -> rehearsal.status == "cancelled"
            }
            val matchesQuery = normalizedQuery.isBlank() ||
                rehearsal.title.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                rehearsal.location.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                rehearsal.notes.lowercase(Locale.ROOT).contains(normalizedQuery)

            matchesTime && matchesStatus && matchesQuery
        }

        return if (filter == RehearsalTimeFilter.PAST) {
            matching.sortedWith(
                compareByDescending<RehearsalDto> { it.rehearsalDate }
                    .thenByDescending { it.startTime }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            )
        } else {
            matching.sortedWith(
                compareBy<RehearsalDto> { it.rehearsalDate }
                    .thenBy { it.startTime }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            )
        }
    }

    fun monthGrid(
        month: YearMonth,
        rehearsals: List<RehearsalDto>
    ): List<RehearsalMonthCalendarDay> {
        val firstDay = month.atDay(1)
        val lastDay = month.atEndOfMonth()
        val gridStart = firstDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val gridEnd = lastDay.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val dayCount = ChronoUnit.DAYS.between(gridStart, gridEnd).toInt() + 1

        return (0 until dayCount).map { offset ->
            val date = gridStart.plusDays(offset.toLong())
            RehearsalMonthCalendarDay(
                date = date,
                isInDisplayedMonth = YearMonth.from(date) == month,
                rehearsals = rehearsalsOn(date, rehearsals)
            )
        }
    }

    fun rehearsalsOn(
        date: LocalDate,
        rehearsals: List<RehearsalDto>
    ): List<RehearsalDto> = rehearsals.filter {
        it.rehearsalDate == date.toString()
    }.sortedWith(
        compareBy<RehearsalDto> { it.startTime }
            .thenBy { it.title.lowercase(Locale.ROOT) }
    )
}
