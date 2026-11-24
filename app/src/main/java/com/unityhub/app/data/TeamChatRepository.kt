package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.ExperimentalSupabaseApi
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.selectAsFlow
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TeamChatMessageDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("sender_id") val senderId: String,
    val content: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
private data class NewTeamChatMessage(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("sender_id") val senderId: String,
    val content: String
)

internal object TeamChatRules {
    const val MAX_MESSAGE_LENGTH = 1_000
    const val MESSAGE_HISTORY_LIMIT = 100

    fun normalizeMessage(value: String): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "Write a message before sending." }
        require(normalized.length <= MAX_MESSAGE_LENGTH) {
            "Messages must be ${MAX_MESSAGE_LENGTH} characters or fewer."
        }
        return normalized
    }

    fun latestMessages(messages: List<TeamChatMessageDto>): List<TeamChatMessageDto> =
        messages.distinctBy { it.id }
            .sortedWith(compareBy<TeamChatMessageDto> { it.createdAt }.thenBy { it.id })
            .takeLast(MESSAGE_HISTORY_LIMIT)
}

class TeamChatRepository(private val client: SupabaseClient) {
    suspend fun loadMessages(organizationId: String): List<TeamChatMessageDto> {
        require(organizationId.isNotBlank())
        return TeamChatRules.latestMessages(
            client.from("team_messages").select {
                filter { eq("organization_id", organizationId) }
            }.decodeList<TeamChatMessageDto>()
        )
    }

    suspend fun sendMessage(organizationId: String, senderId: String, content: String) {
        require(organizationId.isNotBlank() && senderId.isNotBlank())
        client.from("team_messages").insert(
            NewTeamChatMessage(
                organizationId = organizationId,
                senderId = senderId,
                content = TeamChatRules.normalizeMessage(content)
            )
        )
    }

    @OptIn(ExperimentalSupabaseApi::class)
    fun observeMessages(organizationId: String): Flow<List<TeamChatMessageDto>> {
        require(organizationId.isNotBlank())
        return client.from("team_messages").selectAsFlow(
            TeamChatMessageDto::id,
            filter = FilterOperation("organization_id", FilterOperator.EQ, organizationId)
        )
    }
}
