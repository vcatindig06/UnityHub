package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MemberMinistryPositionDto(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("position_id") val positionId: String,
    @SerialName("assigned_by") val assignedBy: String? = null
)

@Serializable
private data class NewMinistryPosition(
    @SerialName("organization_id") val organizationId: String,
    val name: String
)

@Serializable
private data class NewMemberMinistryPosition(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("position_id") val positionId: String,
    @SerialName("assigned_by") val assignedBy: String
)

@Serializable
private data class ChangeMemberRoleParams(
    @SerialName("p_member_id") val memberId: String,
    @SerialName("p_new_role") val newRole: String
)

@Serializable
private data class RemoveMemberParams(
    @SerialName("p_member_id") val memberId: String
)

@Serializable
private data class TransferOrganizationOwnershipParams(
    @SerialName("p_organization_id") val organizationId: String,
    @SerialName("p_new_owner_user_id") val newOwnerUserId: String
)

internal object TeamAccessRules {
    val availableRoles = listOf("worship_leader", "song_editor", "team_member", "viewer", "admin")

    fun normalizeRole(value: String): String {
        val normalized = value.trim().lowercase()
        require(normalized in availableRoles) { "Choose a valid team access role." }
        return normalized
    }

    fun roleLabel(role: String): String = when (role) {
        "admin" -> "Administrator"
        "worship_leader" -> "Worship Leader"
        "song_editor" -> "Song Editor"
        "viewer" -> "Viewer"
        else -> "Team Member"
    }

    fun canManageMember(
        isAdmin: Boolean,
        currentUserId: String,
        organizationOwnerId: String,
        targetUserId: String
    ): Boolean = isAdmin && targetUserId.isNotBlank() &&
        targetUserId != currentUserId && targetUserId != organizationOwnerId

    fun canTransferOwnership(
        currentUserId: String,
        organizationOwnerId: String,
        targetUserId: String
    ): Boolean = currentUserId.isNotBlank() &&
        currentUserId == organizationOwnerId &&
        targetUserId.isNotBlank() && targetUserId != currentUserId
}

internal object TeamManagementRules {
    fun normalizePositionName(value: String): String {
        val normalized = value.trim()
        require(normalized.length in 2..60) {
            "A ministry position name must contain 2–60 characters."
        }
        return normalized
    }

    fun positionsToAdd(current: Set<String>, selected: Set<String>): Set<String> =
        selected - current

    fun positionsToRemove(current: Set<String>, selected: Set<String>): Set<String> =
        current - selected
}

internal object TeamDirectoryRules {
    fun filterMembers(
        members: List<ServiceMemberOption>,
        assignedPositionsByMember: Map<String, List<String>>,
        searchQuery: String,
        selectedPosition: String
    ): List<ServiceMemberOption> {
        val query = searchQuery.trim()

        return members.filter { member ->
            val assignedPositions = assignedPositionsByMember[member.memberId].orEmpty()
            val searchableRole = member.teamRole.replace('_', ' ')

            val matchesSearch = query.isBlank() ||
                member.displayName.contains(query, ignoreCase = true) ||
                searchableRole.contains(query, ignoreCase = true) ||
                assignedPositions.any { it.contains(query, ignoreCase = true) }

            val matchesPosition = selectedPosition.isBlank() ||
                assignedPositions.any { it.equals(selectedPosition, ignoreCase = true) }

            matchesSearch && matchesPosition
        }.sortedWith(
            compareBy<ServiceMemberOption> { it.displayName.lowercase() }
                .thenBy { it.memberId }
        )
    }
}

class TeamManagementRepository(private val client: SupabaseClient) {

    suspend fun changeMemberRole(memberId: String, newRole: String) {
        require(memberId.isNotBlank()) { "A team member must be selected." }
        client.postgrest.rpc(
            "change_member_role",
            ChangeMemberRoleParams(memberId, TeamAccessRules.normalizeRole(newRole))
        )
    }

    suspend fun removeMember(memberId: String) {
        require(memberId.isNotBlank()) { "A team member must be selected." }
        client.postgrest.rpc("remove_member", RemoveMemberParams(memberId))
    }

    suspend fun transferOrganizationOwnership(organizationId: String, newOwnerUserId: String) {
        require(organizationId.isNotBlank() && newOwnerUserId.isNotBlank()) {
            "Choose a valid organization member."
        }
        client.postgrest.rpc(
            "transfer_organization_ownership",
            TransferOrganizationOwnershipParams(organizationId, newOwnerUserId)
        )
    }

    suspend fun loadMinistryPositions(organizationId: String): List<MinistryPositionDto> =
        client.from("ministry_positions").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<MinistryPositionDto>()
            .sortedBy { it.name.lowercase() }

    suspend fun loadMemberPositions(organizationId: String): List<MemberMinistryPositionDto> =
        client.from("member_positions").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<MemberMinistryPositionDto>()

    suspend fun createMinistryPosition(organizationId: String, name: String) {
        val normalizedName = TeamManagementRules.normalizePositionName(name)
        val existingNames = loadMinistryPositions(organizationId)
            .map { it.name.trim().lowercase() }
            .toSet()
        require(normalizedName.lowercase() !in existingNames) {
            "That ministry position already exists."
        }

        client.from("ministry_positions").insert(
            NewMinistryPosition(
                organizationId = organizationId,
                name = normalizedName
            )
        )
    }

    suspend fun assignPosition(
        organizationId: String,
        memberId: String,
        positionId: String,
        assignedBy: String
    ) {
        require(organizationId.isNotBlank() && memberId.isNotBlank() &&
            positionId.isNotBlank() && assignedBy.isNotBlank()
        )
        client.from("member_positions").insert(
            NewMemberMinistryPosition(
                organizationId = organizationId,
                memberId = memberId,
                positionId = positionId,
                assignedBy = assignedBy
            )
        )
    }

    suspend fun removePosition(
        organizationId: String,
        memberId: String,
        positionId: String
    ) {
        client.from("member_positions").delete {
            filter {
                eq("organization_id", organizationId)
                eq("member_id", memberId)
                eq("position_id", positionId)
            }
        }
    }
}
