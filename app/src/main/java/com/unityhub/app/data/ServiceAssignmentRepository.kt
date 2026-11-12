package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class ServiceAssignmentDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("service_plan_id") val servicePlanId: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("role_label") val roleLabel: String,
    @SerialName("response_status") val responseStatus: String,
    @SerialName("response_note") val responseNote: String,
    @SerialName("assigned_by") val assignedBy: String,
    @SerialName("responded_at") val respondedAt: String? = null,
    @SerialName("created_at") val createdAt: String
)

data class ServiceMemberOption(
    val memberId: String,
    val userId: String,
    val displayName: String,
    val teamRole: String
)

data class MyServiceAssignment(
    val assignment: ServiceAssignmentDto,
    val servicePlan: ServicePlanDto
)

data class ServiceAssignmentOverviewEntry(
    val assignment: ServiceAssignmentDto,
    val servicePlan: ServicePlanDto,
    val memberDisplayName: String
)


@Serializable
private data class ProfileNameDto(
    val id: String,
    @SerialName("display_name") val displayName: String
)

@Serializable
private data class NewServiceAssignment(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("service_plan_id") val servicePlanId: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("role_label") val roleLabel: String,
    @SerialName("assigned_by") val assignedBy: String
)

@Serializable
data class MinistryPositionDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    val name: String
)

internal object ServiceAssignmentRules {
    fun normalizeRoleLabel(value: String): String {
        val normalized = value.trim()
        require(normalized.length in 2..80) {
            "Enter a role between 2 and 80 characters."
        }
        return normalized
    }

    fun isValidResponse(value: String): Boolean =
        value == "confirmed" || value == "declined"

    fun canRespondToService(status: String): Boolean =
        status != "completed" && status != "cancelled"
}

class ServiceAssignmentRepository(private val client: SupabaseClient) {

    suspend fun loadAssignments(
        servicePlanId: String,
        organizationId: String
    ): List<ServiceAssignmentDto> =
        client.from("service_plan_assignments").select {
            filter {
                eq("service_plan_id", servicePlanId)
                eq("organization_id", organizationId)
            }
        }.decodeList<ServiceAssignmentDto>()
            .sortedWith(compareBy<ServiceAssignmentDto> { it.roleLabel.lowercase() }
                .thenBy { it.createdAt })

    suspend fun loadMyAssignments(
        organizationId: String,
        userId: String
    ): List<MyServiceAssignment> {
        require(organizationId.isNotBlank() && userId.isNotBlank())

        // Resolve only the membership row owned by the signed-in account.
        val membership = client.from("organization_members").select {
            filter {
                eq("organization_id", organizationId)
                eq("user_id", userId)
            }
        }.decodeList<OrganizationMemberDto>().firstOrNull() ?: return emptyList()

        // Filtering by the owned membership ID keeps this view personal even though
        // the organization-level RLS policy also permits members to read team assignments.
        val assignments = client.from("service_plan_assignments").select {
            filter {
                eq("organization_id", organizationId)
                eq("member_id", membership.id)
            }
        }.decodeList<ServiceAssignmentDto>()

        if (assignments.isEmpty()) return emptyList()

        val plansById = client.from("service_plans").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<ServicePlanDto>().associateBy { it.id }

        return assignments.mapNotNull { assignment ->
            plansById[assignment.servicePlanId]?.let { plan ->
                MyServiceAssignment(assignment = assignment, servicePlan = plan)
            }
        }.sortedWith(
            compareBy<MyServiceAssignment> { it.servicePlan.serviceDate ?: "9999-12-31" }
                .thenBy { it.servicePlan.startTime.orEmpty() }
                .thenBy { it.assignment.roleLabel.lowercase() }
        )
    }

    suspend fun loadOrganizationAssignments(
        organizationId: String
    ): List<ServiceAssignmentOverviewEntry> {
        require(organizationId.isNotBlank())

        // Keep every request scoped to the active organization. Supabase RLS remains
        // the authorization boundary for the signed-in account.
        val assignments = client.from("service_plan_assignments").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<ServiceAssignmentDto>()

        if (assignments.isEmpty()) return emptyList()

        val plansById = client.from("service_plans").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<ServicePlanDto>().associateBy { it.id }

        val membersById = loadTeamMembers(organizationId).associateBy { it.memberId }

        return assignments.mapNotNull { assignment ->
            val plan = plansById[assignment.servicePlanId] ?: return@mapNotNull null
            val member = membersById[assignment.memberId]
            ServiceAssignmentOverviewEntry(
                assignment = assignment,
                servicePlan = plan,
                memberDisplayName = member?.displayName?.takeIf(String::isNotBlank)
                    ?: "Team member"
            )
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
    }

    suspend fun loadTeamMembers(organizationId: String): List<ServiceMemberOption> {
        val memberships = client.from("organization_members").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<OrganizationMemberDto>()

        val profiles = client.from("profiles").select()
            .decodeList<ProfileNameDto>()
            .associateBy { it.id }

        return memberships.map { member ->
            ServiceMemberOption(
                memberId = member.id,
                userId = member.userId,
                displayName = profiles[member.userId]?.displayName
                    ?.takeIf(String::isNotBlank)
                    ?: "UnityHub member",
                teamRole = member.role
            )
        }.sortedBy { it.displayName.lowercase() }
    }

    suspend fun loadSuggestedRoles(organizationId: String): List<String> =
        client.from("ministry_positions").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<MinistryPositionDto>()
            .map { it.name }
            .distinct()
            .sortedBy { it.lowercase() }

    suspend fun assignMember(
        organizationId: String,
        servicePlanId: String,
        memberId: String,
        roleLabel: String,
        assignedBy: String
    ) {
        val normalizedRole = ServiceAssignmentRules.normalizeRoleLabel(roleLabel)
        require(memberId.isNotBlank() && assignedBy.isNotBlank())
        client.from("service_plan_assignments").insert(
            NewServiceAssignment(
                organizationId = organizationId,
                servicePlanId = servicePlanId,
                memberId = memberId,
                roleLabel = normalizedRole,
                assignedBy = assignedBy
            )
        )
    }

    suspend fun respond(
        assignmentId: String,
        organizationId: String,
        memberId: String,
        response: String
    ): Boolean {
        require(assignmentId.isNotBlank() && organizationId.isNotBlank() && memberId.isNotBlank())
        require(ServiceAssignmentRules.isValidResponse(response))
        val updated = client.from("service_plan_assignments").update({
            set("response_status", response)
            set("responded_at", Instant.now().toString())
        }) {
            select()
            filter {
                eq("id", assignmentId)
                eq("organization_id", organizationId)
                eq("member_id", memberId)
            }
        }.decodeList<ServiceAssignmentDto>()
        return updated.any { it.responseStatus == response }
    }

    suspend fun removeAssignment(assignmentId: String, organizationId: String) {
        client.from("service_plan_assignments").delete {
            filter {
                eq("id", assignmentId)
                eq("organization_id", organizationId)
            }
        }
    }
}
