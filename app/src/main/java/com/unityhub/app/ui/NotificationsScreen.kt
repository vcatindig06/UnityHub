package com.unityhub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.NotificationDto
import com.unityhub.app.data.NotificationRules
import com.unityhub.app.data.NotificationsRepository
import com.unityhub.app.data.SupabaseClientProvider
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun NotificationsScreen(
    userId: String,
    currentOrganizationId: String? = null,
    onOpenServicePlan: (NotificationDto) -> Unit = {},
    onOpenRehearsal: (NotificationDto) -> Unit = {},
    onOpenTeamChat: (NotificationDto) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val repository = remember { NotificationsRepository(SupabaseClientProvider.client) }
    val scope = rememberCoroutineScope()
    var notifications by remember(userId) { mutableStateOf<List<NotificationDto>>(emptyList()) }
    var loading by remember(userId) { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var savingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var savingAll by remember { mutableStateOf(false) }
    var unreadOnly by remember(userId) { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loading = true
            errorMessage = ""
            try {
                notifications = repository.loadNotifications(userId)
            } catch (_: Exception) {
                errorMessage = "Your notifications could not be loaded. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    fun markRead(notification: NotificationDto) {
        if (!NotificationRules.isUnread(notification) || notification.id in savingIds) return
        scope.launch {
            savingIds = savingIds + notification.id
            try {
                repository.markAsRead(notification.id, userId)
                notifications = notifications.map {
                    if (it.id == notification.id) it.copy(readAt = Instant.now().toString()) else it
                }
            } catch (_: Exception) {
                errorMessage = "This notification could not be marked as read. Please try again."
            } finally {
                savingIds = savingIds - notification.id
            }
        }
    }

    fun markAllRead() {
        if (savingAll || savingIds.isNotEmpty()) return
        val unreadIds = notifications
            .filter(NotificationRules::isUnread)
            .map { it.id }
        if (unreadIds.isEmpty()) return

        scope.launch {
            savingAll = true
            errorMessage = ""
            try {
                repository.markAllAsRead(unreadIds, userId)
                val readAt = Instant.now().toString()
                val markedIds = unreadIds.toSet()
                notifications = notifications.map { notification ->
                    if (notification.id in markedIds) notification.copy(readAt = readAt)
                    else notification
                }
            } catch (_: Exception) {
                errorMessage = "Some notifications could not be marked as read. Refresh to check their latest status."
                runCatching {
                    notifications = repository.loadNotifications(userId)
                }
            } finally {
                savingAll = false
            }
        }
    }

    LaunchedEffect(userId) {
        try {
            // selectAsFlow emits the initial recipient-scoped rows before streaming changes.
            repository.observeNotifications(userId).collect { incoming ->
                notifications = NotificationRules.newestFirst(incoming)
                loading = false
                errorMessage = ""
            }
        } catch (_: Exception) {
            try {
                notifications = repository.loadNotifications(userId)
                errorMessage = "Live updates are unavailable. Notifications were loaded once; use Refresh to check again."
            } catch (_: Exception) {
                errorMessage = "Your notifications could not be loaded. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    val unreadCount = NotificationRules.unreadCount(notifications)
    val visibleNotifications = NotificationRules.filterNotifications(
        notifications = notifications,
        unreadOnly = unreadOnly
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Notifications", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { reload() }, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh notifications")
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !unreadOnly,
                        onClick = { unreadOnly = false },
                        enabled = !savingAll,
                        label = { Text("All (" + notifications.size + ")") }
                    )
                    FilterChip(
                        selected = unreadOnly,
                        onClick = { unreadOnly = true },
                        enabled = !savingAll,
                        label = { Text("Unread (" + unreadCount + ")") }
                    )
                }
                if (unreadCount > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { markAllRead() },
                            enabled = !loading && !savingAll && savingIds.isEmpty()
                        ) {
                            if (savingAll) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            else Text("Mark all read")
                        }
                    }
                }
            }
        }

        if (errorMessage.isNotBlank()) {
            item {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (loading && notifications.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text("Loading notifications", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (visibleNotifications.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (unreadOnly) "No unread notifications" else "No notifications yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else {
            items(visibleNotifications, key = { it.id }) { notification ->
                NotificationCard(
                    notification = notification,
                    saving = notification.id in savingIds,
                    bulkSaving = savingAll,
                    onMarkRead = { markRead(notification) },
                    onOpenServicePlan = { onOpenServicePlan(notification) },
                    onOpenRehearsal = { onOpenRehearsal(notification) },
                    canOpenTeamChat = notification.organizationId == currentOrganizationId,
                    onOpenTeamChat = { onOpenTeamChat(notification) }
                )
            }
        }

    }
}

@Composable
private fun NotificationCard(
    notification: NotificationDto,
    saving: Boolean,
    bulkSaving: Boolean,
    onMarkRead: () -> Unit,
    onOpenServicePlan: () -> Unit,
    onOpenRehearsal: () -> Unit,
    canOpenTeamChat: Boolean,
    onOpenTeamChat: () -> Unit
) {
    val unread = NotificationRules.isUnread(notification)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (unread) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        NotificationRules.typeLabel(notification.notificationType),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (unread) {
                    Text(
                        "Unread",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(notification.title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(notification.message, style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    NotificationRules.formatCreatedAt(notification.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (unread) {
                    TextButton(onClick = onMarkRead, enabled = !saving && !bulkSaving) {
                        if (saving) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        else {
                            Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Mark read")
                        }
                    }
                }
            }
            if (NotificationRules.hasServicePlan(notification)) {
                TextButton(onClick = onOpenServicePlan, enabled = !saving) {
                    Text("View service")
                }
            }
            if (NotificationRules.hasRehearsal(notification)) {
                TextButton(onClick = onOpenRehearsal, enabled = !saving) {
                    Text("View rehearsal")
                }
            }
            if (NotificationRules.hasTeamChatMessage(notification) && canOpenTeamChat) {
                TextButton(onClick = onOpenTeamChat, enabled = !saving && !bulkSaving) {
                    Text("Open Team Chat")
                }
            }
        }
    }
}
