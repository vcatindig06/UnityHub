package com.unityhub.app.ui

import com.unityhub.app.data.MyServiceAssignment
import com.unityhub.app.data.ServicePlanDto
import java.time.LocalDate

object HomeDashboardRules {
    fun upcomingPlans(
        plans: List<ServicePlanDto>,
        today: LocalDate
    ): List<ServicePlanDto> =
        plans.filter { plan ->
            if (plan.status == "completed" || plan.status == "cancelled") {
                false
            } else {
                val date = plan.serviceDate?.let {
                    runCatching { LocalDate.parse(it) }.getOrNull()
                }
                date != null && !date.isBefore(today)
            }
        }.sortedWith(
            compareBy<ServicePlanDto> { it.serviceDate.orEmpty() }
                .thenBy { it.startTime ?: "99:99" }
                .thenBy { it.title.lowercase() }
        )

    fun upcomingAssignments(
        assignments: List<MyServiceAssignment>,
        today: LocalDate
    ): List<MyServiceAssignment> =
        assignments.filter { assignment ->
            val plan = assignment.servicePlan
            if (plan.status == "completed" || plan.status == "cancelled") {
                false
            } else {
                val date = plan.serviceDate?.let {
                    runCatching { LocalDate.parse(it) }.getOrNull()
                }
                date == null || !date.isBefore(today)
            }
        }.sortedWith(
            compareBy<MyServiceAssignment> { it.servicePlan.serviceDate ?: "9999-12-31" }
                .thenBy { it.servicePlan.startTime.orEmpty() }
                .thenBy { it.assignment.roleLabel.lowercase() }
        )
}
