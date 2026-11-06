package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
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
