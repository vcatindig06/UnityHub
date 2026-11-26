package com.unityhub.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.unityhub.app.data.AccountRepository
import com.unityhub.app.data.NotificationsRepository
import com.unityhub.app.data.ServiceAssignmentRepository
import com.unityhub.app.data.ServiceMemberOption
import com.unityhub.app.data.SupabaseClientProvider
import com.unityhub.app.data.TeamChatMessageDto
import com.unityhub.app.data.TeamChatReadStateDto
import com.unityhub.app.data.TeamChatReactionDto
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
    onOpenMemberProfile: (ServiceMemberOption) -> Unit = {},
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
    var reactions by remember(organizationId) { mutableStateOf<List<TeamChatReactionDto>>(emptyList()) }
    var replyToMessageId by remember(organizationId) { mutableStateOf<String?>(null) }
    var editingMessageId by remember(organizationId) { mutableStateOf<String?>(null) }
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
                reactions = repository.loadReactions(organizationId)
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
                val activeEditId = editingMessageId
                if (activeEditId != null) {
                    repository.editMessage(
                        organizationId = organizationId,
                        messageId = activeEditId,
                        senderId = userId,
                        content = content
                    )
                } else {
                    repository.sendMessage(
                        organizationId = organizationId,
                        senderId = userId,
                        content = content,
                        replyToMessageId = replyToMessageId
                    )
                }
                draft = ""
                editingMessageId = null
                replyToMessageId = null
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
                    is TeamChatRealtimeEvent.MessageUpdated -> {
                        messages = TeamChatRules.mergeMessages(messages, listOf(event.message))
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
            repository.observeReactions(organizationId).collect { incoming ->
                reactions = incoming
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            runCatching { repository.loadReactions(organizationId) }
                .onSuccess { reactions = it }
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
            val readThroughMessageId = if (
                ownCursor != null && TeamChatRules.hasReadMessage(ownCursor, visibleMessage)
            ) {
                // Use the persisted cursor itself when it is already ahead of
                // the visible row, so upgrade cleanup reaches every stale alert.
                ownCursor.lastReadMessageId
            } else {
                val readResult = runCatching {
                    repository.markAsRead(organizationId, userId, visibleMessage.id)
                }
                if (readResult.isFailure) return@collect
                visibleMessage.id
            }

            // Also reconcile notifications when an existing cursor is already
            // ahead of this message; older app versions could leave alerts for
            // history that was outside their loaded page.
            lastMarkedMessageId = visibleMessage.id
            runCatching {
                notificationsRepository.markTeamChatNotificationsReadThrough(
                    organizationId = organizationId,
                    throughMessageId = readThroughMessageId,
                    userId = userId
                )
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

        messages.lastOrNull { it.pinnedAt != null }?.let { pinnedMessage ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Pinned", style = MaterialTheme.typography.labelMedium)
                    Column(Modifier.weight(1f)) {
                        Text(pinnedMessage.content, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
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
                    val member = membersByUserId[message.senderId]
                    TeamChatMessageBubble(
                        message = message,
                        isOwnMessage = message.senderId == userId,
                        senderName = if (message.senderId == userId) "You" else member?.displayName ?: "Team member",
                        avatarUrl = AccountRepository(client).publicMediaUrl(member?.avatarPath),
                        readByCount = TeamChatRules.readByCount(
                            message = message,
                            readStates = readStates,
                            currentMemberIds = currentMemberIds
                        ),
                        reactions = reactions.filter { it.messageId == message.id },
                        currentUserId = userId,
                        replyPreview = message.replyToMessageId?.let { replyId ->
                            messages.firstOrNull { it.id == replyId }?.let { parent ->
                                "${membersByUserId[parent.senderId]?.displayName ?: "Team member"}: ${parent.content}"
                            } ?: "Earlier message"
                        },
                        onOpenProfile = { member?.let(onOpenMemberProfile) },
                        onReply = {
                            editingMessageId = null
                            replyToMessageId = message.id
                        },
                        onEdit = {
                            editingMessageId = message.id
                            replyToMessageId = null
                            draft = message.content
                        },
                        onDelete = {
                            scope.launch {
                                runCatching {
                                    repository.deleteMessage(organizationId, message.id, userId)
                                    val page = repository.loadLatestMessages(organizationId)
                                    messages = TeamChatRules.mergeMessages(messages, page.messages)
                                }.onFailure {
                                    errorMessage = "This message could not be deleted. Try again."
                                }
                            }
                        },
                        onPin = { shouldPin ->
                            scope.launch {
                                runCatching {
                                    repository.setPinned(organizationId, message.id, userId, shouldPin)
                                    val page = repository.loadLatestMessages(organizationId)
                                    messages = TeamChatRules.mergeMessages(messages, page.messages)
                                }.onFailure { error ->
                                    errorMessage = error.message ?: "The pin change could not be saved."
                                }
                            }
                        },
                        onReact = { emoji ->
                            scope.launch {
                                runCatching {
                                    repository.toggleReaction(organizationId, message.id, userId, emoji)
                                }.onFailure {
                                    errorMessage = "Your reaction could not be saved. Try again."
                                }
                            }
                        }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (editingMessageId != null || replyToMessageId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (editingMessageId != null) "Editing your message"
                        else "Replying to ${messages.firstOrNull { it.id == replyToMessageId }?.let { "message from " + (membersByUserId[it.senderId]?.displayName ?: "team member") } ?: "an earlier message"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = {
                        editingMessageId = null
                        replyToMessageId = null
                        draft = ""
                    }) { Text("Cancel") }
                }
            }
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
                    label = { Text(if (editingMessageId != null) "Edit message" else "Message") },
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
                    Text(if (sending) "Saving" else if (editingMessageId != null) "Save" else "Send")
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TeamChatMessageBubble(
    message: TeamChatMessageDto,
    isOwnMessage: Boolean,
    senderName: String,
    avatarUrl: String?,
    readByCount: Int,
    reactions: List<TeamChatReactionDto>,
    currentUserId: String,
    replyPreview: String?,
    onOpenProfile: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPin: (Boolean) -> Unit,
    onReact: (String) -> Unit
) {
    var menuExpanded by remember(message.id) { mutableStateOf(false) }
    var showEmojiPicker by remember(message.id) { mutableStateOf(false) }
    var showDeleteConfirmation by remember(message.id) { mutableStateOf(false) }
    var customEmoji by remember(message.id) { mutableStateOf("") }
    val isDeleted = message.deletedAt != null
    val isPinned = message.pinnedAt != null
    val groupedReactions = reactions.groupBy { it.emoji }.toSortedMap()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isOwnMessage) {
            Box(
                modifier = Modifier
                    .padding(end = 7.dp, bottom = 3.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "${senderName} profile photo",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(senderName.take(1).uppercase(), color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Box {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .pointerInput(message.id) {
                        var horizontalDrag = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                horizontalDrag += dragAmount
                                change.consume()
                            },
                            onDragEnd = {
                                if (kotlin.math.abs(horizontalDrag) > 72f && !isDeleted) onReply()
                                horizontalDrag = 0f
                            },
                            onDragCancel = { horizontalDrag = 0f }
                        )
                    }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { menuExpanded = true }
                        ),
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
                            Text(
                                senderName,
                                modifier = Modifier.clickable(onClick = onOpenProfile),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (!replyPreview.isNullOrBlank()) {
                            Text(
                                "↪ $replyPreview",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 2
                            )
                        }
                        Text(
                            message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDeleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        val receipt = buildString {
                            append(formatChatTime(message.createdAt))
                            if (message.editedAt != null && !isDeleted) append(" · Edited")
                            if (isOwnMessage && readByCount > 0) append(" · Seen by $readByCount")
                            if (isPinned) append(" · Pinned")
                        }
                        Text(
                            receipt,
                            modifier = Modifier.align(Alignment.End),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (groupedReactions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        groupedReactions.forEach { (emoji, rows) ->
                            TextButton(
                                onClick = { onReact(emoji) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                                modifier = Modifier.heightIn(min = 28.dp)
                            ) {
                                Text("$emoji ${rows.size}", style = MaterialTheme.typography.labelSmall)
                                if (rows.any { it.userId == currentUserId }) {
                                    Text(" ✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                if (!isDeleted) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf("❤️", "😆", "😯", "😥", "😡", "👍").forEach { emoji ->
                                TextButton(
                                    onClick = {
                                        onReact(emoji)
                                        menuExpanded = false
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) { Text(emoji) }
                            }
                            TextButton(
                                onClick = {
                                    showEmojiPicker = true
                                    menuExpanded = false
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) { Text("+") }
                        }
                    }
                    DropdownMenuItem(
                        text = { Text("Reply") },
                        onClick = { menuExpanded = false; onReply() }
                    )
                    if (isOwnMessage) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { menuExpanded = false; showDeleteConfirmation = true }
                        )
                    }
                    if (!isPinned) {
                        DropdownMenuItem(
                            text = { Text("Pin message") },
                            onClick = { menuExpanded = false; onPin(true) }
                        )
                    } else if (message.pinnedBy == currentUserId) {
                        DropdownMenuItem(
                            text = { Text("Unpin message") },
                            onClick = { menuExpanded = false; onPin(false) }
                        )
                    }
                }
            }
        }
    }

    if (showEmojiPicker) {
        AlertDialog(
            onDismissRequest = { showEmojiPicker = false },
            title = { Text("Add a reaction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Choose any emoji using your device's emoji keyboard.")
                    OutlinedTextField(
                        value = customEmoji,
                        onValueChange = { value ->
                            if (value.length <= 16) customEmoji = value
                        },
                        singleLine = true,
                        label = { Text("Emoji") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = customEmoji.trim()
                        if (value.isNotEmpty()) onReact(value)
                        showEmojiPicker = false
                    },
                    enabled = customEmoji.trim().isNotEmpty()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showEmojiPicker = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete message?") },
            text = { Text("This message will be replaced with a deleted-message notice for everyone in the conversation.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatChatTime(value: String): String {
    val instant = runCatching { Instant.parse(value) }.getOrNull() ?: return value
    return DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
