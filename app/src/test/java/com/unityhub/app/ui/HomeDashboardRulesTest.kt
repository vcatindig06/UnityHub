package com.unityhub.app.ui

import com.unityhub.app.data.ServicePlanDto
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HomeDashboardRulesTest {
    private val today = LocalDate.of(2026, 11, 5)

    @Test
    fun upcomingPlansIncludesTodayAndFutureDates() {
        val plans = listOf(
            plan("future", "2026-11-09"),
            plan("today", "2026-11-05")
        )

        assertEquals(listOf("today", "future"), HomeDashboardRules.upcomingPlans(plans, today).map { it.id })
    }

    @Test
    fun upcomingPlansExcludesPastCompletedAndCancelledServices() {
        val plans = listOf(
            plan("past", "2026-11-04"),
            plan("completed", "2026-11-06", status = "completed"),
            plan("cancelled", "2026-11-07", status = "cancelled"),
            plan("draft", "2026-11-08", status = "draft")
        )

        assertEquals(listOf("draft"), HomeDashboardRules.upcomingPlans(plans, today).map { it.id })
    }

    @Test
    fun upcomingPlansSortsByDateThenTimeThenTitle() {
        val plans = listOf(
            plan("evening", "2026-11-05", time = "18:00:00", title = "Evening"),
            plan("morning", "2026-11-05", time = "09:00:00", title = "Morning"),
            plan("future", "2026-11-06", time = "10:00:00", title = "Future")
        )

        assertEquals(
            listOf("morning", "evening", "future"),
            HomeDashboardRules.upcomingPlans(plans, today).map { it.id }
        )
    }

    @Test
    fun upcomingPlansIgnoresUnscheduledOrInvalidDates() {
        val plans = listOf(
            plan("undated", null),
            plan("invalid", "next Sunday"),
            plan("scheduled", "2026-11-05")
        )

        assertEquals(listOf("scheduled"), HomeDashboardRules.upcomingPlans(plans, today).map { it.id })
    }

    private fun plan(
        id: String,
        date: String?,
        time: String? = "10:00:00",
        title: String = id,
        status: String = "published"
    ) = ServicePlanDto(
        id = id,
        organizationId = "organization",
        title = title,
        serviceDate = date,
        startTime = time,
        status = status,
        createdBy = "user",
        createdAt = "2026-11-01T00:00:00Z",
        updatedAt = "2026-11-01T00:00:00Z"
    )
}
