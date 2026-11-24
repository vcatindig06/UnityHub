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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.ServiceAssignmentRepository
import com.unityhub.app.data.ServiceMemberOption
import com.unityhub.app.data.SupabaseClientProvider
import com.unityhub.app.data.TeamChatMessageDto
import com.unityhub.app.data.TeamChatRepository
import com.unityhub.app.data.TeamChatRules
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
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember(organizationId) { mutableStateOf<List<TeamChatMessageDto>>(emptyList()) }
    var membersByUserId by remember(organizationId) { mutableStateOf<Map<String, ServiceMemberOption>>(emptyMap()) }
    var draft by remember(organizationId) { mutableStateOf("") }
    var loading by remember(organizationId) { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var hasPositionedChatList by remember(organizationId) { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loading = true
            errorMessage = ""
            try {
                messages = repository.loadMessages(organizationId)
                val members = memberRepository.loadTeamMembers(organizationId)
                membersByUserId = members.associateBy { it.userId }
            } catch (_: Exception) {
                errorMessage = "Team Chat could not load. Check your connection and make sure the Team Chat database migration has been applied."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(organizationId) {
        reload()
        try {
            repository.observeMessages(organizationId).collect { incoming ->
                messages = TeamChatRules.latestMessages(incoming)
                loading = false
                errorMessage = ""
            }
        } catch (_: Exception) {
            // Keep the last loaded messages visible. The refresh action remains
            // available when Realtime is temporarily disconnected.
            if (messages.isEmpty()) {
                errorMessage = "Live updates are unavailable. Use Refresh to check for new messages."
            }
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.id) {
        if (messages.isEmpty()) return@LaunchedEffect

        val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (
            TeamChatRules.shouldFollowLatest(
                lastVisibleItemIndex = lastVisibleItemIndex,
                totalItemCount = messages.size,
                hasPositionedList = hasPositionedChatList
            )
        ) {
            listState.animateScrollToItem(messages.lastIndex)
        }
        hasPositionedChatList = true
    }

    val normalizedDraft = draft.trim()
    val canSend = normalizedDraft.isNotEmpty() &&
        normalizedDraft.length <= TeamChatRules.MAX_MESSAGE_LENGTH &&
        !sending

    fun sendDraft() {
        if (!canSend) return
        val content = normalizedDraft
        scope.launch {
            sending = true
            errorMessage = ""
            try {
                repository.sendMessage(organizationId, userId, content)
                draft = ""
                // Sending is successful even if this optional refresh fails.
                // Realtime normally supplies the new row; Refresh remains a
                // fallback when the stream is temporarily unavailable.
                runCatching { repository.loadMessages(organizationId) }
                    .onSuccess { messages = TeamChatRules.latestMessages(it) }
            } catch (_: Exception) {
                errorMessage = "Your message could not be sent. Check your connection and try again."
            } finally {
                sending = false
            }
        }
    }

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
                    "Messages are shared with members of $organizationName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { reload() }, enabled = !loading) { Text("Refresh") }
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
                items(messages, key = { it.id }) { message ->
                    TeamChatMessageBubble(
                        message = message,
                        isOwnMessage = message.senderId == userId,
                        senderName = if (message.senderId == userId) {
                            "You"
                        } else {
                            membersByUserId[message.senderId]?.displayName ?: "Team member"
                        }
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
                    enabled = canSend
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
    senderName: String
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
                Text(
                    formatChatTime(message.createdAt),
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
