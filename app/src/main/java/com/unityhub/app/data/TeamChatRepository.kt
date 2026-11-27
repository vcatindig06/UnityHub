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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import java.time.Instant

@Serializable
data class TeamChatMessageDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("conversation_id") val conversationId: String = "",
    @SerialName("sender_id") val senderId: String,
    val content: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("reply_to_message_id") val replyToMessageId: String? = null,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("pinned_at") val pinnedAt: String? = null,
    @SerialName("pinned_by") val pinnedBy: String? = null,
    @SerialName("attachment_id") val attachmentId: String? = null,
    @SerialName("attachment_path") val attachmentPath: String? = null,
    @SerialName("attachment_name") val attachmentName: String? = null,
    @SerialName("attachment_mime_type") val attachmentMimeType: String? = null,
    @SerialName("attachment_size") val attachmentSize: Long? = null
)

@Serializable
data class TeamChatReadStateDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("conversation_id") val conversationId: String = "",
    @SerialName("user_id") val userId: String,
    @SerialName("last_read_message_id") val lastReadMessageId: String,
    @SerialName("last_read_at") val lastReadAt: String
)

@Serializable
private data class NewTeamChatMessage(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val content: String,
    @SerialName("reply_to_message_id") val replyToMessageId: String? = null,
    @SerialName("attachment_id") val attachmentId: String? = null,
    @SerialName("attachment_path") val attachmentPath: String? = null,
    @SerialName("attachment_name") val attachmentName: String? = null,
    @SerialName("attachment_mime_type") val attachmentMimeType: String? = null,
    @SerialName("attachment_size") val attachmentSize: Long? = null
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

@Serializable
data class ChatConversationDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("conversation_type") val conversationType: String,
    val title: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class ChatConversationMemberDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("member_role") val memberRole: String,
    @SerialName("joined_at") val joinedAt: String
)

@Serializable
private data class CreatedChatConversationDto(
    @SerialName("conversation_id") val conversationId: String
)

data class ChatConversationSummary(
    val conversation: ChatConversationDto,
    val members: List<ChatConversationMemberDto>
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
    suspend fun loadConversations(organizationId: String): List<ChatConversationSummary> {
        require(organizationId.isNotBlank())
        val conversations = client.from("chat_conversations").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<ChatConversationDto>()
        if (conversations.isEmpty()) return emptyList()

        val members = client.from("chat_conversation_members").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<ChatConversationMemberDto>()
        val membersByConversation = members.groupBy { it.conversationId }
        return conversations.map { conversation ->
            ChatConversationSummary(
                conversation = conversation,
                members = membersByConversation[conversation.id].orEmpty()
            )
        }.sortedWith(
            compareByDescending<ChatConversationSummary> { it.conversation.conversationType == "organization" }
                .thenByDescending { it.conversation.updatedAt }
        )
    }

    suspend fun getOrCreateDirectConversation(organizationId: String, otherUserId: String): String {
        require(organizationId.isNotBlank() && otherUserId.isNotBlank())
        val created = client.postgrest.rpc(
            "get_or_create_direct_chat",
            buildJsonObject {
                put("p_organization_id", organizationId)
                put("p_other_user_id", otherUserId)
            }
        ).decodeList<CreatedChatConversationDto>()
        return created.singleOrNull()?.conversationId
            ?: error("The direct conversation could not be opened.")
    }

    suspend fun createGroupConversation(
        organizationId: String,
        title: String,
        memberUserIds: List<String>
    ): String {
        require(organizationId.isNotBlank())
        val normalizedTitle = title.trim()
        require(normalizedTitle.length in 2..80) { "Group name must contain 2 to 80 characters." }
        val members = memberUserIds.filter(String::isNotBlank).distinct()
        require(members.isNotEmpty()) { "Choose at least one other group member." }
        val created = client.postgrest.rpc(
            "create_group_chat",
            buildJsonObject {
                put("p_organization_id", organizationId)
                put("p_title", normalizedTitle)
                put("p_member_user_ids", JsonArray(members.map { memberId -> JsonPrimitive(memberId) }))
            }
        ).decodeList<CreatedChatConversationDto>()
        return created.singleOrNull()?.conversationId
            ?: error("The group conversation could not be created.")
    }

    suspend fun renameGroupConversation(conversationId: String, title: String) {
        val normalizedTitle = title.trim()
        require(normalizedTitle.length in 2..80) {
            "Group name must contain 2 to 80 characters."
        }
        client.postgrest.rpc(
            "rename_group_chat",
            buildJsonObject {
                put("p_conversation_id", conversationId)
                put("p_title", normalizedTitle)
            }
        )
    }

    suspend fun addGroupMember(conversationId: String, userId: String) {
        require(conversationId.isNotBlank() && userId.isNotBlank())
        client.postgrest.rpc(
            "add_group_chat_member",
            buildJsonObject {
                put("p_conversation_id", conversationId)
                put("p_user_id", userId)
            }
        )
    }

    suspend fun removeGroupMember(conversationId: String, userId: String) {
        require(conversationId.isNotBlank() && userId.isNotBlank())
        client.postgrest.rpc(
            "remove_group_chat_member",
            buildJsonObject {
                put("p_conversation_id", conversationId)
                put("p_user_id", userId)
            }
        )
    }

    suspend fun loadLatestMessages(
        organizationId: String,
        conversationId: String
    ): TeamChatMessagePage = loadMessagePage(organizationId, conversationId, before = null)

    suspend fun loadOlderMessages(
        organizationId: String,
        conversationId: String,
        before: TeamChatMessageDto
    ): TeamChatMessagePage = loadMessagePage(organizationId, conversationId, before)

    private suspend fun loadMessagePage(
        organizationId: String,
        conversationId: String,
        before: TeamChatMessageDto?
    ): TeamChatMessagePage {
        require(organizationId.isNotBlank() && conversationId.isNotBlank())
        val fetched = client.postgrest.rpc(
            "get_chat_conversation_messages",
            buildJsonObject {
                put("p_conversation_id", conversationId)
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

    suspend fun loadReadStates(
        organizationId: String,
        conversationId: String
    ): List<TeamChatReadStateDto> {
        require(organizationId.isNotBlank() && conversationId.isNotBlank())
        return client.from("chat_conversation_read_states").select {
            filter {
                eq("organization_id", organizationId)
                eq("conversation_id", conversationId)
            }
        }.decodeList<TeamChatReadStateDto>()
    }

    fun observeReadStates(
        organizationId: String,
        conversationId: String
    ): Flow<List<TeamChatReadStateDto>> {
        require(organizationId.isNotBlank() && conversationId.isNotBlank())
        return client.from("chat_conversation_read_states").selectAsFlow(
            TeamChatReadStateDto::id,
            filter = {
                eq("organization_id", organizationId)
                eq("conversation_id", conversationId)
            }
        )
    }

    /**
     * The subscription is established before Connected is emitted. The screen
     * then loads the active conversation page so inserts during that request
     * are not lost in a gap.
     */
    fun observeNewMessages(conversationId: String): Flow<TeamChatRealtimeEvent> = channelFlow {
        require(conversationId.isNotBlank())
        val channel = client.realtime.channel("chat-conversation:$conversationId")
        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "team_messages"
            filter = "conversation_id=eq.$conversationId"
        }
        val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "team_messages"
            filter = "conversation_id=eq.$conversationId"
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
        conversationId: String,
        userId: String,
        messageId: String
    ) {
        require(conversationId.isNotBlank() && userId.isNotBlank() && messageId.isNotBlank())
        client.postgrest.rpc(
            "mark_chat_conversation_read",
            buildJsonObject {
                put("p_conversation_id", conversationId)
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
        conversationId: String,
        senderId: String,
        content: String,
        replyToMessageId: String? = null,
        attachment: ChatMessageAttachment? = null
    ) {
        require(organizationId.isNotBlank() && conversationId.isNotBlank() && senderId.isNotBlank())
        client.from("team_messages").insert(
            NewTeamChatMessage(
                organizationId = organizationId,
                conversationId = conversationId,
                senderId = senderId,
                content = TeamChatRules.normalizeMessage(content),
                replyToMessageId = replyToMessageId,
                attachmentId = attachment?.id,
                attachmentPath = attachment?.storagePath,
                attachmentName = attachment?.fileName,
                attachmentMimeType = attachment?.mimeType,
                attachmentSize = attachment?.fileSize
            )
        )
    }
}

private val CHAT_JSON = Json {
    ignoreUnknownKeys = true
}
