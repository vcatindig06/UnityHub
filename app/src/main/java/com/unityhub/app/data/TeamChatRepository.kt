package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.selectAsFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Instant

@Serializable
data class TeamChatMessageDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("sender_id") val senderId: String,
    val content: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class TeamChatReadStateDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("last_read_message_id") val lastReadMessageId: String,
    @SerialName("last_read_at") val lastReadAt: String
)

@Serializable
private data class NewTeamChatMessage(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("sender_id") val senderId: String,
    val content: String
)

@Serializable
private data class TeamChatPageParams(
    @SerialName("p_organization_id") val organizationId: String,
    @SerialName("p_before_created_at") val beforeCreatedAt: String? = null,
    @SerialName("p_before_message_id") val beforeMessageId: String? = null,
    @SerialName("p_page_size") val pageSize: Int = TeamChatRules.PAGE_SIZE + 1
)

@Serializable
private data class NewTeamChatReadState(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("last_read_message_id") val lastReadMessageId: String
)

data class TeamChatMessagePage(
    val messages: List<TeamChatMessageDto>,
    val hasMore: Boolean
)

sealed interface TeamChatRealtimeEvent {
    data object Connected : TeamChatRealtimeEvent
    data class MessageReceived(val message: TeamChatMessageDto) : TeamChatRealtimeEvent
}

internal object TeamChatRules {
    const val MAX_MESSAGE_LENGTH = 1_000
    const val PAGE_SIZE = 50

    fun normalizeMessage(value: String): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "Write a message before sending." }
        require(normalized.length <= MAX_MESSAGE_LENGTH) {
            "Messages must be ${MAX_MESSAGE_LENGTH} characters or fewer."
        }
        return normalized
    }

    fun mergeMessages(
        current: List<TeamChatMessageDto>,
        incoming: List<TeamChatMessageDto>
    ): List<TeamChatMessageDto> = (current + incoming)
        .distinctBy { it.id }
        .sortedWith(compareBy<TeamChatMessageDto> { it.createdAt }.thenBy { it.id })

    fun readByCount(
        message: TeamChatMessageDto,
        readStates: List<TeamChatReadStateDto>,
        currentMemberIds: Set<String>
    ): Int = readStates.count { state ->
        state.userId != message.senderId &&
            state.userId in currentMemberIds &&
            hasReadMessage(state, message)
    }

    fun unreadCount(
        messages: List<TeamChatMessageDto>,
        readState: TeamChatReadStateDto?,
        currentUserId: String
    ): Int = messages.count { message ->
        message.senderId != currentUserId &&
            (readState == null || !hasReadMessage(readState, message))
    }

    fun hasReadMessage(
        readState: TeamChatReadStateDto,
        message: TeamChatMessageDto
    ): Boolean {
        val cursorTime = runCatching { Instant.parse(readState.lastReadAt) }.getOrNull()
            ?: return readState.lastReadMessageId == message.id
        val messageTime = runCatching { Instant.parse(message.createdAt) }.getOrNull()
            ?: return readState.lastReadMessageId == message.id
        return when {
            cursorTime > messageTime -> true
            cursorTime < messageTime -> false
            else -> readState.lastReadMessageId >= message.id
        }
    }

    /**
     * Follow new messages only when the reader is already at (or very near)
     * the latest message. The first non-empty load always positions the list
     * at the newest message.
     */
    fun shouldFollowLatest(
        lastVisibleItemIndex: Int,
        totalItemCount: Int,
        hasPositionedList: Boolean
    ): Boolean {
        if (totalItemCount <= 0) return false
        if (!hasPositionedList) return true
        return lastVisibleItemIndex >= totalItemCount - 2
    }
}

class TeamChatRepository(private val client: SupabaseClient) {
    suspend fun loadLatestMessages(organizationId: String): TeamChatMessagePage =
        loadMessagePage(organizationId, before = null)

    suspend fun loadOlderMessages(
        organizationId: String,
        before: TeamChatMessageDto
    ): TeamChatMessagePage = loadMessagePage(organizationId, before)

    private suspend fun loadMessagePage(
        organizationId: String,
        before: TeamChatMessageDto?
    ): TeamChatMessagePage {
        require(organizationId.isNotBlank())
        val fetched = client.postgrest.rpc(
            "get_team_chat_messages",
            TeamChatPageParams(
                organizationId = organizationId,
                beforeCreatedAt = before?.createdAt,
                beforeMessageId = before?.id
            )
        ).decodeList<TeamChatMessageDto>()

        val hasMore = fetched.size > TeamChatRules.PAGE_SIZE
        return TeamChatMessagePage(
            messages = fetched.take(TeamChatRules.PAGE_SIZE).asReversed(),
            hasMore = hasMore
        )
    }

    suspend fun loadReadStates(organizationId: String): List<TeamChatReadStateDto> {
        require(organizationId.isNotBlank())
        return client.from("team_chat_read_states").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<TeamChatReadStateDto>()
    }

    fun observeReadStates(organizationId: String): Flow<List<TeamChatReadStateDto>> {
        require(organizationId.isNotBlank())
        return client.from("team_chat_read_states").selectAsFlow(
            TeamChatReadStateDto::id,
            filter = { eq("organization_id", organizationId) }
        )
    }

    /**
     * The subscription is established before Connected is emitted. The screen
     * then loads the latest page, so inserts occurring during that request are
     * already queued by the Realtime collector and are not lost in a gap.
     */
    fun observeNewMessages(organizationId: String): Flow<TeamChatRealtimeEvent> = channelFlow {
        require(organizationId.isNotBlank())
        val channel = client.realtime.createChannel("team-chat:$organizationId")
        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "team_messages"
            filter = "organization_id=eq.$organizationId"
        }
        val collector = launch {
            changes.collect { action ->
                val message = CHAT_JSON.decodeFromJsonElement<TeamChatMessageDto>(action.record)
                send(TeamChatRealtimeEvent.MessageReceived(message))
            }
        }

        try {
            client.realtime.connect()
            channel.join(blockUntilJoined = true)
            send(TeamChatRealtimeEvent.Connected)
            kotlinx.coroutines.awaitCancellation()
        } finally {
            collector.cancel()
            runCatching { client.realtime.removeChannel(channel) }
        }
    }

    suspend fun markAsRead(
        organizationId: String,
        userId: String,
        messageId: String
    ) {
        require(organizationId.isNotBlank() && userId.isNotBlank() && messageId.isNotBlank())
        client.from("team_chat_read_states").insert(
            NewTeamChatReadState(
                organizationId = organizationId,
                userId = userId,
                lastReadMessageId = messageId
            ),
            upsert = true,
            onConflict = "organization_id,user_id"
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
}

private val CHAT_JSON = Json {
    ignoreUnknownKeys = true
}
