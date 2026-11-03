package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrganizationDirectoryDto(
    @SerialName("organization_id") val organizationId: String,
    val name: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class OrganizationDto(
    val id: String,
    val name: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class OrganizationMemberDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("user_id") val userId: String,
    val role: String,
    @SerialName("joined_at") val joinedAt: String
)

@Serializable
data class OrganizationJoinRequestDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("user_id") val userId: String,
    val message: String,
    val status: String,
    @SerialName("requester_display_name") val requesterDisplayName: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("reviewed_by") val reviewedBy: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null
)

@Serializable
private data class CreateOrganizationParams(
    @SerialName("p_name") val name: String
)

@Serializable
private data class RequestToJoinParams(
    @SerialName("p_organization_id") val organizationId: String,
    @SerialName("p_message") val message: String
)

@Serializable
private data class ReviewJoinRequestParams(
    @SerialName("p_request_id") val requestId: String,
    @SerialName("p_approve") val approve: Boolean,
    @SerialName("p_role") val role: String
)

data class OrganizationWorkspace(
    val organization: OrganizationDto? = null,
    val role: String? = null,
    val directory: List<OrganizationDirectoryDto> = emptyList(),
    val myRequests: List<OrganizationJoinRequestDto> = emptyList(),
    val pendingRequests: List<OrganizationJoinRequestDto> = emptyList(),
    val members: List<OrganizationMemberDto> = emptyList()
)

class OrganizationRepository(
    private val client: SupabaseClient
) {
    suspend fun loadWorkspace(userId: String): OrganizationWorkspace {
        require(userId.isNotBlank()) { "No signed-in user was found." }

        val memberships = client.from("organization_members")
            .select {
                filter { eq("user_id", userId) }
            }
            .decodeList<OrganizationMemberDto>()

        val membership = memberships.firstOrNull()

        if (membership == null) {
            val directory = client.from("organization_directory")
                .select()
                .decodeList<OrganizationDirectoryDto>()

            val requests = client.from("organization_join_requests")
                .select {
                    filter { eq("user_id", userId) }
                }
                .decodeList<OrganizationJoinRequestDto>()

            return OrganizationWorkspace(
                directory = directory.sortedBy { it.name.lowercase() },
                myRequests = requests
            )
        }

        val organization = client.from("organizations")
            .select {
                filter { eq("id", membership.organizationId) }
            }
            .decodeList<OrganizationDto>()
            .firstOrNull()
            ?: error("Your team record could not be loaded. Contact your team administrator.")

        val members = client.from("organization_members")
            .select {
                filter { eq("organization_id", membership.organizationId) }
            }
            .decodeList<OrganizationMemberDto>()

        val pendingRequests = if (membership.role == "admin") {
            client.from("organization_join_requests")
                .select {
                    filter {
                        eq("organization_id", membership.organizationId)
                        eq("status", "pending")
                    }
                }
                .decodeList<OrganizationJoinRequestDto>()
                .sortedBy { it.createdAt }
        } else {
            emptyList()
        }

        return OrganizationWorkspace(
            organization = organization,
            role = membership.role,
            pendingRequests = pendingRequests,
            members = members.sortedBy { it.joinedAt }
        )
    }

    suspend fun createOrganization(name: String) {
        require(name.trim().length in 2..100) {
            "Team name must be between 2 and 100 characters."
        }
        client.postgrest.rpc(
            "create_organization",
            CreateOrganizationParams(name = name.trim())
        )
    }

    suspend fun requestToJoin(organizationId: String) {
        client.postgrest.rpc(
            "request_to_join",
            RequestToJoinParams(
                organizationId = organizationId,
                message = ""
            )
        )
    }

    suspend fun reviewJoinRequest(requestId: String, approve: Boolean) {
        client.postgrest.rpc(
            "review_join_request",
            ReviewJoinRequestParams(
                requestId = requestId,
                approve = approve,
                role = "team_member"
            )
        )
    }
}
