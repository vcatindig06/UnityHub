package com.unityhub.app.ui

import com.unityhub.app.data.ServiceAssignmentDto
import com.unityhub.app.data.ServiceAssignmentOverviewEntry
import com.unityhub.app.data.ServicePlanDto
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AssignmentOverviewRulesTest {
    private val today = LocalDate.of(2026, 11, 5)

    @Test
    fun upcomingExcludesPastCompletedAndCancelledServices() {
        val entries = listOf(
            entry("future", "pending", "2026-11-08"),
            entry("past", "pending", "2026-11-04"),
            entry("completed", "confirmed", "2026-11-06", planStatus = "completed"),
            entry("cancelled", "declined", "2026-11-07", planStatus = "cancelled"),
            entry("today", "confirmed", "2026-11-05"),
            entry("undated", "pending", null)
        )

        assertEquals(
            listOf("today", "future", "undated"),
            AssignmentOverviewRules.upcoming(entries, today).map { it.assignment.id }
        )
    }

    @Test
    fun upcomingSortsByDateTimeServiceRoleAndMember() {
        val entries = listOf(
            entry("later", "pending", "2026-11-05", time = "18:00:00"),
            entry("early-bass", "pending", "2026-11-05", time = "09:00:00", role = "Bass"),
            entry("early-vocal", "pending", "2026-11-05", time = "09:00:00", role = "Main Vocal"),
            entry("tomorrow", "pending", "2026-11-06", time = "08:00:00")
        )

        assertEquals(
            listOf("early-bass", "early-vocal", "later", "tomorrow"),
            AssignmentOverviewRules.upcoming(entries, today).map { it.assignment.id }
        )
    }

    @Test
    fun filterMatchesResponseAndSearchAcrossMemberRoleAndService() {
        val entries = listOf(
            entry("one", "pending", "2026-11-08", member = "Alex Rivera", role = "Keys", service = "Sunday Worship"),
            entry("two", "confirmed", "2026-11-08", member = "Jamie Cruz", role = "Drums", service = "Sunday Worship"),
            entry("three", "declined", "2026-11-09", member = "Taylor Santos", role = "Bass", service = "Youth Night")
        )

        assertEquals(
            listOf("one"),
            AssignmentOverviewRules.filter(entries, AssignmentResponseFilter.PENDING, "alex")
                .map { it.assignment.id }
        )
        assertEquals(
            listOf("two"),
            AssignmentOverviewRules.filter(entries, AssignmentResponseFilter.CONFIRMED, "drum")
                .map { it.assignment.id }
        )
        assertEquals(
            listOf("three"),
            AssignmentOverviewRules.filter(entries, AssignmentResponseFilter.DECLINED, "youth")
                .map { it.assignment.id }
        )
        assertEquals(3, AssignmentOverviewRules.filter(entries, AssignmentResponseFilter.ALL, "").size)
    }

    private fun entry(
        id: String,
        response: String,
        date: String?,
        time: String? = "10:00:00",
        member: String = "Team member",
        role: String = "Vocal",
        service: String = id,
        planStatus: String = "published"
    ) = ServiceAssignmentOverviewEntry(
        assignment = ServiceAssignmentDto(
            id = id,
            organizationId = "organization",
            servicePlanId = "service-$id",
            memberId = "membership-$id",
            roleLabel = role,
            responseStatus = response,
            responseNote = "",
            assignedBy = "leader",
            respondedAt = null,
            createdAt = "2026-11-01T00:00:00Z"
        ),
        servicePlan = ServicePlanDto(
            id = "service-$id",
            organizationId = "organization",
            title = service,
            serviceDate = date,
            startTime = time,
            notes = "",
            status = planStatus,
            createdBy = "leader",
            createdAt = "2026-11-01T00:00:00Z",
            updatedAt = "2026-11-01T00:00:00Z"
        ),
        memberDisplayName = member
    )
}
