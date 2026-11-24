package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

data class MemberServiceHistoryItem(
    val assignment: ServiceAssignmentDto,
    val servicePlan: ServicePlanDto
)

data class MemberRehearsalHistoryItem(
    val assignment: RehearsalAssignmentDto,
    val rehearsal: RehearsalDto
)

data class MemberParticipationSnapshot(
    val services: List<MemberServiceHistoryItem>,
    val rehearsals: List<MemberRehearsalHistoryItem>
)

internal object MemberParticipationRules {
    fun serviceStatusLabel(status: String): String = when (status) {
        "confirmed" -> "Confirmed"
        "declined" -> "Can't serve"
        "pending" -> "Awaiting response"
        else -> "Response unavailable"
    }

    fun rehearsalStatusLabel(status: String): String = when (status) {
        "available" -> "Available"
        "unavailable" -> "Can't attend"
        "attended" -> "Attended"
        "absent" -> "Absent"
        "pending" -> "Awaiting response"
        else -> "Not recorded"
    }
}

/**
 * Read-only participation history for one organization membership.
 *
 * Every query is scoped to the active organization and selected membership.
 * Supabase RLS remains the authorization boundary; this repository does not
 * use service-role credentials or bypass database policies.
 */
class MemberParticipationRepository(private val client: SupabaseClient) {

    suspend fun load(
        organizationId: String,
        memberId: String
    ): MemberParticipationSnapshot {
        require(organizationId.isNotBlank() && memberId.isNotBlank()) {
            "Choose a valid team member."
        }

        val serviceAssignments = client.from("service_plan_assignments").select {
            filter {
                eq("organization_id", organizationId)
                eq("member_id", memberId)
            }
        }.decodeList<ServiceAssignmentDto>()

        val rehearsalAssignments = client.from("rehearsal_assignments").select {
            filter {
                eq("organization_id", organizationId)
                eq("member_id", memberId)
            }
        }.decodeList<RehearsalAssignmentDto>()

        val servicePlansById = if (serviceAssignments.isEmpty()) {
            emptyMap()
        } else {
            client.from("service_plans").select {
                filter { eq("organization_id", organizationId) }
            }.decodeList<ServicePlanDto>().associateBy { it.id }
        }

        val rehearsalsById = if (rehearsalAssignments.isEmpty()) {
            emptyMap()
        } else {
            client.from("rehearsals").select {
                filter { eq("organization_id", organizationId) }
            }.decodeList<RehearsalDto>().associateBy { it.id }
        }

        val serviceHistory = serviceAssignments.mapNotNull { assignment ->
            servicePlansById[assignment.servicePlanId]?.let { plan ->
                MemberServiceHistoryItem(assignment, plan)
            }
        }.sortedWith(
            compareByDescending<MemberServiceHistoryItem> {
                it.servicePlan.serviceDate.orEmpty()
            }.thenByDescending {
                it.servicePlan.startTime.orEmpty()
            }
        )

        val rehearsalHistory = rehearsalAssignments.mapNotNull { assignment ->
            rehearsalsById[assignment.rehearsalId]?.let { rehearsal ->
                MemberRehearsalHistoryItem(assignment, rehearsal)
            }
        }.sortedWith(
            compareByDescending<MemberRehearsalHistoryItem> {
                it.rehearsal.rehearsalDate
            }.thenByDescending {
                it.rehearsal.startTime
            }
        )

        return MemberParticipationSnapshot(
            services = serviceHistory,
            rehearsals = rehearsalHistory
        )
    }
}
