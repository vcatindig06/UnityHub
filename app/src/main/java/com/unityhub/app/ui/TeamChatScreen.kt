package com.unityhub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.NotificationsRepository
import com.unityhub.app.data.ServiceAssignmentRepository
import com.unityhub.app.data.ServiceMemberOption
import com.unityhub.app.data.SupabaseClientProvider
import com.unityhub.app.data.TeamChatMessageDto
import com.unityhub.app.data.TeamChatReadStateDto
import com.unityhub.app.data.TeamChatRealtimeEvent
import com.unityhub.app.data.TeamChatRepository
import com.unityhub.app.data.TeamChatRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TeamChatScreen(
    organizationId: String,
    organizationName: String,
    userId: String,
    modifier: Modifier = Modifier
) {
    val client = remember { SupabaseClientProvider.client }
    val repository = remember { TeamChatRepository(client) }
    val memberRepository = remember { ServiceAssignmentRepository(client) }
    val notificationsRepository = remember { NotificationsRepository(client) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember(organizationId) { mutableStateOf<List<TeamChatMessageDto>>(emptyList()) }
    var readStates by remember(organizationId) { mutableStateOf<List<TeamChatReadStateDto>>(emptyList()) }
    var membersByUserId by remember(organizationId) { mutableStateOf<Map<String, ServiceMemberOption>>(emptyMap()) }
    var draft by remember(organizationId) { mutableStateOf("") }
    var loading by remember(organizationId) { mutableStateOf(true) }
    var loadingOlder by remember(organizationId) { mutableStateOf(false) }
    var hasMoreOlder by remember(organizationId) { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var hasPositionedChatList by remember(organizationId) { mutableStateOf(false) }
    var pendingAnchorMessageId by remember { mutableStateOf<String?>(null) }
    var pendingAnchorOffset by remember { mutableStateOf(0) }
    var lastMarkedMessageId by remember(organizationId) { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            errorMessage = ""
            try {
                val hadLoadedMessages = messages.isNotEmpty()
                val page = repository.loadLatestMessages(organizationId)
                messages = TeamChatRules.mergeMessages(messages, page.messages)
                if (!hadLoadedMessages) hasMoreOlder = page.hasMore
                readStates = repository.loadReadStates(organizationId)
                membersByUserId = memberRepository.loadTeamMembers(organizationId)
                    .associateBy { it.userId }
            } catch (_: Exception) {
                errorMessage = "Team Chat could not load. Check your connection and make sure the Team Chat database migration has been applied."
            } finally {
                loading = false
            }
        }
    }

    fun loadOlderMessages() {
        val oldest = messages.firstOrNull() ?: return
        if (loadingOlder || !hasMoreOlder) return

        val visibleMessage = listState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { item ->
            val id = item.key as? String
            messages.firstOrNull { it.id == id }?.let { item to it }
        }
        pendingAnchorMessageId = visibleMessage?.second?.id
        pendingAnchorOffset = visibleMessage?.first?.let { item ->
            if (item.index == listState.firstVisibleItemIndex) {
                listState.firstVisibleItemScrollOffset
            } else {
                -item.offset
            }
        } ?: 0

        scope.launch {
            loadingOlder = true
            errorMessage = ""
            try {
                val page = repository.loadOlderMessages(organizationId, oldest)
                messages = TeamChatRules.mergeMessages(page.messages, messages)
                hasMoreOlder = page.hasMore
            } catch (_: Exception) {
                pendingAnchorMessageId = null
                errorMessage = "Older messages could not be loaded. Check your connection and try again."
            } finally {
                loadingOlder = false
            }
        }
    }

    fun sendDraft() {
        val content = draft.trim()
        if (
            content.isEmpty() ||
            content.length > TeamChatRules.MAX_MESSAGE_LENGTH ||
            sending
        ) return

        scope.launch {
            sending = true
            errorMessage = ""
            try {
                repository.sendMessage(organizationId, userId, content)
                draft = ""
                // Realtime supplies the inserted message. Refresh is only a
                // fallback and must not turn a successful send into an error.
                runCatching {
                    repository.loadLatestMessages(organizationId)
                }.onSuccess { page ->
                    messages = TeamChatRules.mergeMessages(messages, page.messages)
                }
            } catch (_: Exception) {
                errorMessage = "Your message could not be sent. Check your connection and try again."
            } finally {
                sending = false
            }
        }
    }

    LaunchedEffect(organizationId) {
        try {
            repository.observeNewMessages(organizationId).collect { event ->
                when (event) {
                    TeamChatRealtimeEvent.Connected -> reload()
                    is TeamChatRealtimeEvent.MessageReceived -> {
                        messages = TeamChatRules.mergeMessages(messages, listOf(event.message))
                        loading = false
                        errorMessage = ""
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (messages.isEmpty()) {
                errorMessage = "Live updates are unavailable. Loading messages once without the live connection."
            } else {
                errorMessage = "Live updates are temporarily unavailable. Your loaded messages remain available."
            }
            reload()
        }
    }

    LaunchedEffect(organizationId) {
        try {
            repository.observeReadStates(organizationId).collect { incoming ->
                readStates = incoming
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            runCatching { repository.loadReadStates(organizationId) }
                .onSuccess { readStates = it }
        }
    }

    LaunchedEffect(organizationId, messages.size, messages.lastOrNull()?.id, hasMoreOlder) {
        if (messages.isEmpty()) return@LaunchedEffect

        val anchorId = pendingAnchorMessageId
        if (anchorId != null) {
            val messageIndex = messages.indexOfFirst { it.id == anchorId }
            if (messageIndex >= 0) {
                listState.scrollToItem(
                    index = messageIndex + if (hasMoreOlder) 1 else 0,
                    scrollOffset = pendingAnchorOffset
                )
            }
            pendingAnchorMessageId = null
            hasPositionedChatList = true
            return@LaunchedEffect
        }

        val messageById = messages.withIndex().associate { it.value.id to it.index }
        val lastVisibleMessageIndex = listState.layoutInfo.visibleItemsInfo
            .mapNotNull { item -> (item.key as? String)?.let(messageById::get) }
            .maxOrNull() ?: -1
        if (
            TeamChatRules.shouldFollowLatest(
                lastVisibleItemIndex = lastVisibleMessageIndex,
                totalItemCount = messages.size,
                hasPositionedList = hasPositionedChatList
            )
        ) {
            listState.animateScrollToItem(messages.lastIndex + if (hasMoreOlder) 1 else 0)
        }
        hasPositionedChatList = true
    }

    // The highest chronological message currently visible becomes the member's
    // read cursor. The database rejects backward cursor movement.
    LaunchedEffect(organizationId, userId) {
        snapshotFlow {
            val messageById = messages.associateBy { it.id }
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { item -> (item.key as? String)?.let(messageById::get) }
                .maxWithOrNull(compareBy<TeamChatMessageDto> { it.createdAt }.thenBy { it.id })
                ?.id
        }.distinctUntilChanged().collect { visibleMessageId ->
            val visibleMessage = messages.firstOrNull { it.id == visibleMessageId } ?: return@collect
            if (visibleMessage.id == lastMarkedMessageId) return@collect

            val ownCursor = readStates.firstOrNull { it.userId == userId }
            if (ownCursor != null && TeamChatRules.hasReadMessage(ownCursor, visibleMessage)) {
                lastMarkedMessageId = visibleMessage.id
                return@collect
            }

            runCatching {
                repository.markAsRead(organizationId, userId, visibleMessage.id)
            }.onSuccess {
                lastMarkedMessageId = visibleMessage.id
                val visibleIndex = messages.indexOfFirst { it.id == visibleMessage.id }
                if (visibleIndex >= 0) {
                    val messagesRead = messages.take(visibleIndex + 1)
                        .filter { it.senderId != userId }
                        .map { it.id }
                    runCatching {
                        notificationsRepository.markTeamChatMessagesAsRead(messagesRead, userId)
                    }
                }
            }
        }
    }

    val normalizedDraft = draft.trim()
    val currentUserReadState = readStates.firstOrNull { it.userId == userId }
    val recentUnreadCount = TeamChatRules.unreadCount(messages, currentUserReadState, userId)
    val currentMemberIds = membersByUserId.keys

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Team conversation", style = MaterialTheme.typography.titleMedium)
                Text(
                    organizationName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { reload() }, enabled = !loading) { Text("Refresh") }
        }

        if (recentUnreadCount > 0) {
            Text(
                "$recentUnreadCount unread messages in the loaded history",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (errorMessage.isNotBlank()) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (loading && messages.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Text("Loading messages", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (messages.isEmpty()) {
            Card(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Start the conversation", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Share an update, ask a question, or encourage your worship team.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasMoreOlder) {
                    item(key = "load-older-messages") {
                        TextButton(
                            onClick = { loadOlderMessages() },
                            enabled = !loadingOlder,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            if (loadingOlder) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(8.dp))
                                Text("Loading older messages")
                            } else {
                                Text("Load older messages")
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { message ->
                    TeamChatMessageBubble(
                        message = message,
                        isOwnMessage = message.senderId == userId,
                        senderName = if (message.senderId == userId) {
                            "You"
                        } else {
                            membersByUserId[message.senderId]?.displayName ?: "Team member"
                        },
                        readByCount = TeamChatRules.readByCount(
                            message = message,
                            readStates = readStates,
                            currentMemberIds = currentMemberIds
                        )
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= TeamChatRules.MAX_MESSAGE_LENGTH) draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Write a message…") },
                    label = { Text("Message") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { sendDraft() }),
                    minLines = 1,
                    maxLines = 4,
                    enabled = !sending
                )
                Button(
                    onClick = { sendDraft() },
                    modifier = Modifier.heightIn(min = 48.dp),
                    enabled = normalizedDraft.isNotEmpty() &&
                        normalizedDraft.length <= TeamChatRules.MAX_MESSAGE_LENGTH &&
                        !sending
                ) {
                    Text(if (sending) "Sending" else "Send")
                }
            }
            Text(
                "${normalizedDraft.length} of ${TeamChatRules.MAX_MESSAGE_LENGTH} characters",
                modifier = Modifier.align(Alignment.End),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TeamChatMessageBubble(
    message: TeamChatMessageDto,
    isOwnMessage: Boolean,
    senderName: String,
    readByCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomEnd = if (isOwnMessage) 4.dp else 16.dp,
                bottomStart = if (isOwnMessage) 16.dp else 4.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isOwnMessage) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isOwnMessage) {
                    Text(senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(message.content, style = MaterialTheme.typography.bodyMedium)
                val receipt = if (isOwnMessage && readByCount > 0) {
                    "${formatChatTime(message.createdAt)} · Seen by $readByCount"
                } else {
                    formatChatTime(message.createdAt)
                }
                Text(
                    receipt,
                    modifier = Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatChatTime(value: String): String {
    val instant = runCatching { Instant.parse(value) }.getOrNull() ?: return value
    return DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
