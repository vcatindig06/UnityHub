package com.unityhub.app.data

import java.util.Locale

enum class ServicePlanStatusFilter(
    val label: String,
    internal val status: String? = null
) {
    ALL("All statuses"),
    DRAFT("Draft", "draft"),
    PUBLISHED("Published", "published"),
    COMPLETED("Completed", "completed"),
    CANCELLED("Cancelled", "cancelled")
}

object ServicePlanFilterRules {
    fun filter(
        plans: List<ServicePlanDto>,
        statusFilter: ServicePlanStatusFilter = ServicePlanStatusFilter.ALL,
        searchQuery: String = ""
    ): List<ServicePlanDto> {
        val query = searchQuery.trim().lowercase(Locale.ROOT)

        return plans.filter { plan ->
            val matchesStatus = statusFilter.status?.let { status ->
                plan.status.equals(status, ignoreCase = true)
            } ?: true
            val matchesQuery = query.isBlank() || listOf(
                plan.title,
                plan.serviceDate.orEmpty(),
                plan.startTime.orEmpty(),
                plan.notes,
                plan.status
            ).any { value -> value.lowercase(Locale.ROOT).contains(query) }

            matchesStatus && matchesQuery
        }
    }
}
