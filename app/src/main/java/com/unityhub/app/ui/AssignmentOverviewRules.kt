package com.unityhub.app.ui

import com.unityhub.app.data.ServiceAssignmentOverviewEntry
import java.time.LocalDate

enum class AssignmentResponseFilter(val label: String) {
    ALL("All"),
    PENDING("Awaiting"),
    CONFIRMED("Confirmed"),
    DECLINED("Can't serve")
}

object AssignmentOverviewRules {
    fun upcoming(
        entries: List<ServiceAssignmentOverviewEntry>,
        today: LocalDate
    ): List<ServiceAssignmentOverviewEntry> =
        entries.filter { entry ->
            val plan = entry.servicePlan
            if (plan.status == "completed" || plan.status == "cancelled") {
                false
            } else {
                val date = plan.serviceDate?.let {
                    runCatching { LocalDate.parse(it) }.getOrNull()
                }
                date == null || !date.isBefore(today)
            }
        }.sortedWith(
            compareBy<ServiceAssignmentOverviewEntry> {
                it.servicePlan.serviceDate ?: "9999-12-31"
            }.thenBy {
                it.servicePlan.startTime ?: "99:99"
            }.thenBy {
                it.servicePlan.title.lowercase()
            }.thenBy {
                it.assignment.roleLabel.lowercase()
            }.thenBy {
                it.memberDisplayName.lowercase()
            }
        )

    fun filter(
        entries: List<ServiceAssignmentOverviewEntry>,
        responseFilter: AssignmentResponseFilter,
        query: String
    ): List<ServiceAssignmentOverviewEntry> {
        val normalizedQuery = query.trim().lowercase()
        return entries.filter { entry ->
            val matchesResponse = when (responseFilter) {
                AssignmentResponseFilter.ALL -> true
                AssignmentResponseFilter.PENDING -> entry.assignment.responseStatus == "pending"
                AssignmentResponseFilter.CONFIRMED -> entry.assignment.responseStatus == "confirmed"
                AssignmentResponseFilter.DECLINED -> entry.assignment.responseStatus == "declined"
            }
            val matchesQuery = normalizedQuery.isBlank() ||
                entry.memberDisplayName.lowercase().contains(normalizedQuery) ||
                entry.assignment.roleLabel.lowercase().contains(normalizedQuery) ||
                entry.servicePlan.title.lowercase().contains(normalizedQuery)
            matchesResponse && matchesQuery
        }
    }
}
