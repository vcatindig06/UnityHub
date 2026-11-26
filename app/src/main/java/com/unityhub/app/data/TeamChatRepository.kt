package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.selectAsFlow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import java.time.Instant

@Serializable
data class TeamChatMessageDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("sender_id") val senderId: String,
    val content: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("reply_to_message_id") val replyToMessageId: String? = null,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("pinned_at") val pinnedAt: String? = null,
    @SerialName("pinned_by") val pinnedBy: String? = null
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
    val content: String,
    @SerialName("reply_to_message_id") val replyToMessageId: String? = null
)

@Serializable
data class TeamChatReactionDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("message_id") val messageId: String,
    @SerialName("user_id") val userId: String,
    val emoji: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
private data class NewTeamChatReaction(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("message_id") val messageId: String,
    @SerialName("user_id") val userId: String,
    val emoji: String
)

data class TeamChatMessagePage(
    val messages: List<TeamChatMessageDto>,
    val hasMore: Boolean
)

sealed interface TeamChatRealtimeEvent {
    data object Connected : TeamChatRealtimeEvent
    data class MessageReceived(val message: TeamChatMessageDto) : TeamChatRealtimeEvent
    data class MessageUpdated(val message: TeamChatMessageDto) : TeamChatRealtimeEvent
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
        .associateBy { it.id }
        .values
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

@OptIn(SupabaseExperimental::class)
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
            buildJsonObject {
                put("p_organization_id", organizationId)
                put("p_page_size", TeamChatRules.PAGE_SIZE + 1)
                before?.let { cursor ->
                    put("p_before_created_at", cursor.createdAt)
                    put("p_before_message_id", cursor.id)
                }
            }
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
        val channel = client.realtime.channel("team-chat:$organizationId")
        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "team_messages"
            filter = "organization_id=eq.$organizationId"
        }
        val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "team_messages"
            filter = "organization_id=eq.$organizationId"
        }
        val insertCollector = launch {
            changes.collect { action ->
                val message = CHAT_JSON.decodeFromJsonElement<TeamChatMessageDto>(action.record)
                send(TeamChatRealtimeEvent.MessageReceived(message))
            }
        }
        val updateCollector = launch {
            updates.collect { action ->
                val message = CHAT_JSON.decodeFromJsonElement<TeamChatMessageDto>(action.record)
                send(TeamChatRealtimeEvent.MessageUpdated(message))
            }
        }

        try {
            channel.subscribe(blockUntilSubscribed = true)
            send(TeamChatRealtimeEvent.Connected)
            awaitCancellation()
        } finally {
            insertCollector.cancelAndJoin()
            updateCollector.cancelAndJoin()
            runCatching { client.realtime.removeChannel(channel) }
        }
    }

    suspend fun markAsRead(
        organizationId: String,
        userId: String,
        messageId: String
    ) {
        require(organizationId.isNotBlank() && userId.isNotBlank() && messageId.isNotBlank())
        client.postgrest.rpc(
            "mark_team_chat_read",
            buildJsonObject {
                put("p_organization_id", organizationId)
                put("p_message_id", messageId)
            }
        )
    }

    suspend fun loadReactions(organizationId: String): List<TeamChatReactionDto> =
        client.from("team_message_reactions").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<TeamChatReactionDto>()

    fun observeReactions(organizationId: String): Flow<List<TeamChatReactionDto>> =
        client.from("team_message_reactions").selectAsFlow(
            TeamChatReactionDto::id,
            filter = { eq("organization_id", organizationId) }
        )

    suspend fun toggleReaction(
        organizationId: String, messageId: String, userId: String, emoji: String
    ) {
        require(organizationId.isNotBlank() && messageId.isNotBlank() && userId.isNotBlank())
        val normalized = emoji.trim()
        require(normalized.isNotEmpty() && normalized.length <= 16) { "Choose one emoji reaction." }
        val existing = client.from("team_message_reactions").select {
            filter {
                eq("organization_id", organizationId)
                eq("message_id", messageId)
                eq("user_id", userId)
                eq("emoji", normalized)
            }
        }.decodeList<TeamChatReactionDto>().firstOrNull()
        if (existing != null) {
            client.from("team_message_reactions").delete {
                filter {
                    eq("id", existing.id)
                    eq("organization_id", organizationId)
                    eq("user_id", userId)
                }
            }
        } else {
            client.from("team_message_reactions").insert(
                NewTeamChatReaction(organizationId, messageId, userId, normalized)
            )
        }
    }

    suspend fun editMessage(organizationId: String, messageId: String, senderId: String, content: String) {
        val updated = client.from("team_messages").update({
            set("content", TeamChatRules.normalizeMessage(content))
            set("edited_at", Instant.now().toString())
        }) {
            select()
            filter {
                eq("id", messageId)
                eq("organization_id", organizationId)
                eq("sender_id", senderId)
            }
        }.decodeList<TeamChatMessageDto>()
        check(updated.isNotEmpty()) { "This message could not be edited." }
    }

    suspend fun deleteMessage(organizationId: String, messageId: String, senderId: String) {
        val updated = client.from("team_messages").update({
            set("content", "This message was deleted")
            set("deleted_at", Instant.now().toString())
        }) {
            select()
            filter {
                eq("id", messageId)
                eq("organization_id", organizationId)
                eq("sender_id", senderId)
            }
        }.decodeList<TeamChatMessageDto>()
        check(updated.isNotEmpty()) { "This message could not be deleted." }
    }

    suspend fun setPinned(organizationId: String, messageId: String, userId: String, pinned: Boolean) {
        val updated = client.from("team_messages").update({
            set("pinned_at", if (pinned) Instant.now().toString() else null)
            set("pinned_by", if (pinned) userId else null)
        }) {
            select()
            filter {
                eq("id", messageId)
                eq("organization_id", organizationId)
                if (!pinned) eq("pinned_by", userId)
            }
        }.decodeList<TeamChatMessageDto>()
        check(updated.isNotEmpty()) {
            if (pinned) "This message could not be pinned." else "Only the pinner can unpin this message."
        }
    }

    suspend fun sendMessage(
        organizationId: String,
        senderId: String,
        content: String,
        replyToMessageId: String? = null
    ) {
        require(organizationId.isNotBlank() && senderId.isNotBlank())
        client.from("team_messages").insert(
            NewTeamChatMessage(
                organizationId = organizationId,
                senderId = senderId,
                content = TeamChatRules.normalizeMessage(content),
                replyToMessageId = replyToMessageId
            )
        )
    }
}

private val CHAT_JSON = Json {
    ignoreUnknownKeys = true
}
