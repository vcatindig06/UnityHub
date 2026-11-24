package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ServicePlanFilterRulesTest {
    @Test
    fun statusFiltersReturnOnlyTheRequestedState() {
        val plans = listOf(
            servicePlan("draft", "2026-11-25", status = "draft"),
            servicePlan("published", "2026-11-26", status = "published"),
            servicePlan("completed", "2026-11-23", status = "completed"),
            servicePlan("cancelled", "2026-11-24", status = "cancelled")
        )

        assertEquals(
            listOf("draft"),
            ServicePlanFilterRules.filter(plans, ServicePlanStatusFilter.DRAFT).map { it.id }
        )
        assertEquals(
            listOf("published"),
            ServicePlanFilterRules.filter(plans, ServicePlanStatusFilter.PUBLISHED).map { it.id }
        )
        assertEquals(
            listOf("completed"),
            ServicePlanFilterRules.filter(plans, ServicePlanStatusFilter.COMPLETED).map { it.id }
        )
        assertEquals(
            listOf("cancelled"),
            ServicePlanFilterRules.filter(plans, ServicePlanStatusFilter.CANCELLED).map { it.id }
        )
    }

    @Test
    fun searchMatchesTitleWithoutCaseSensitivityAndTrimsWhitespace() {
        val plans = listOf(
            servicePlan("worship", "2026-11-25", title = "Sunday Worship"),
            servicePlan("youth", "2026-11-26", title = "Youth Fellowship")
        )

        assertEquals(
            listOf("worship"),
            ServicePlanFilterRules.filter(plans, searchQuery = "  SUNDAY WORSHIP ").map { it.id }
        )
    }

    @Test
    fun searchMatchesDateTimeAndNotes() {
        val plans = listOf(
            servicePlan("date", "2026-12-06", time = "09:00:00"),
            servicePlan("notes", "2026-11-25", notes = "Communion and prayer"),
            servicePlan("time", "2026-11-25", time = "18:30:00")
        )

        assertEquals(
            listOf("date"),
            ServicePlanFilterRules.filter(plans, searchQuery = "2026-12").map { it.id }
        )
        assertEquals(
            listOf("notes"),
            ServicePlanFilterRules.filter(plans, searchQuery = "COMMUNION").map { it.id }
        )
        assertEquals(
            listOf("time"),
            ServicePlanFilterRules.filter(plans, searchQuery = "18:30").map { it.id }
        )
    }

    @Test
    fun searchAndStatusFiltersAreCombined() {
        val plans = listOf(
            servicePlan("published-worship", "2026-11-25", title = "Sunday Worship", status = "published"),
            servicePlan("draft-worship", "2026-11-26", title = "Sunday Worship", status = "draft"),
            servicePlan("published-youth", "2026-11-27", title = "Youth Fellowship", status = "published")
        )

        assertEquals(
            listOf("published-worship"),
            ServicePlanFilterRules.filter(
                plans,
                statusFilter = ServicePlanStatusFilter.PUBLISHED,
                searchQuery = "worship"
            ).map { it.id }
        )
    }

    @Test
    fun allStatusAndBlankSearchPreserveEveryPlan() {
        val plans = listOf(
            servicePlan("one", "2026-11-25"),
            servicePlan("two", null, status = "cancelled")
        )

        assertEquals(
            plans.map { it.id },
            ServicePlanFilterRules.filter(plans, searchQuery = "   ").map { it.id }
        )
    }

    private fun servicePlan(
        id: String,
        date: String?,
        time: String? = null,
        title: String = id,
        notes: String = "",
        status: String = "draft"
    ) = ServicePlanDto(
        id = id,
        organizationId = "organization-1",
        title = title,
        serviceDate = date,
        startTime = time,
        notes = notes,
        status = status,
        createdBy = "member-1",
        createdAt = "2026-11-06T00:00:00Z",
        updatedAt = "2026-11-06T00:00:00Z"
    )
}
