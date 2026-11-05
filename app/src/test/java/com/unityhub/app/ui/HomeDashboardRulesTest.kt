package com.unityhub.app.ui

import com.unityhub.app.data.MyServiceAssignment
import com.unityhub.app.data.ServiceAssignmentDto
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

    @Test
    fun upcomingAssignmentsKeepsOnlyCurrentAndFutureActiveServices() {
        val assignments = listOf(
            assigned("future", "Bass", "2026-11-08"),
            assigned("past", "Drums", "2026-11-04"),
            assigned("completed", "Keys", "2026-11-07", planStatus = "completed"),
            assigned("cancelled", "Vocal", "2026-11-09", planStatus = "cancelled"),
            assigned("today", "Acoustic Guitar", "2026-11-05"),
            assigned("undated", "Media", null)
        )

        assertEquals(
            listOf("today", "future", "undated"),
            HomeDashboardRules.upcomingAssignments(assignments, today).map { it.assignment.id }
        )
    }

    @Test
    fun upcomingAssignmentsSortsByServiceDateTimeAndRole() {
        val assignments = listOf(
            assigned("later", "Keys", "2026-11-05", time = "18:00:00"),
            assigned("drums", "Drums", "2026-11-05", time = "09:00:00"),
            assigned("vocal", "Main Vocal", "2026-11-05", time = "09:00:00"),
            assigned("tomorrow", "Bass", "2026-11-06", time = "08:00:00")
        )

        assertEquals(
            listOf("drums", "vocal", "later", "tomorrow"),
            HomeDashboardRules.upcomingAssignments(assignments, today).map { it.assignment.id }
        )
    }

    @Test
    fun upcomingAssignmentsIncludesUnscheduledServicesAfterDatedServices() {
        val assignments = listOf(
            assigned("undated", "Keys", null),
            assigned("dated", "Vocal", "2026-11-07")
        )

        assertEquals(
            listOf("dated", "undated"),
            HomeDashboardRules.upcomingAssignments(assignments, today).map { it.assignment.id }
        )
    }

    private fun assigned(
        id: String,
        role: String,
        date: String?,
        time: String? = "10:00:00",
        planStatus: String = "published"
    ) = MyServiceAssignment(
        assignment = ServiceAssignmentDto(
            id = id,
            organizationId = "organization",
            servicePlanId = "service-$id",
            memberId = "membership",
            roleLabel = role,
            responseStatus = "pending",
            responseNote = "",
            assignedBy = "leader",
            respondedAt = null,
            createdAt = "2026-11-01T00:00:00Z"
        ),
        servicePlan = plan(
            id = "service-$id",
            date = date,
            time = time,
            status = planStatus
        )
    )

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
