package com.unityhub.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class ServiceCalendarDay(
    val date: LocalDate,
    val isInDisplayedMonth: Boolean,
    val services: List<ServicePlanDto>
)

object ServiceCalendarRules {
    fun monthGrid(month: YearMonth, plans: List<ServicePlanDto>): List<ServiceCalendarDay> {
        val firstDay = month.atDay(1)
        val lastDay = month.atEndOfMonth()
        val gridStart = firstDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val gridEnd = lastDay.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val dayCount = ChronoUnit.DAYS.between(gridStart, gridEnd).toInt() + 1

        return (0 until dayCount).map { offset ->
            val date = gridStart.plusDays(offset.toLong())
            ServiceCalendarDay(
                date = date,
                isInDisplayedMonth = YearMonth.from(date) == month,
                services = servicesOn(date, plans)
            )
        }
    }

    fun weekDates(date: LocalDate): List<LocalDate> {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0L..6L).map(monday::plusDays)
    }

    fun servicesOn(date: LocalDate, plans: List<ServicePlanDto>): List<ServicePlanDto> =
        plans.filter { plan ->
            plan.serviceDate?.let { value ->
                runCatching { LocalDate.parse(value) }.getOrNull() == date
            } ?: false
        }.sortedWith(
            compareBy<ServicePlanDto> { it.startTime?.take(5) ?: "99:99" }
                .thenBy { it.title.lowercase(Locale.ROOT) }
        )
}
