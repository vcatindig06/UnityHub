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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
    modifier: Modifier = Modifier
) {
    val repository = remember { NotificationsRepository(SupabaseClientProvider.client) }
    val scope = rememberCoroutineScope()
    var notifications by remember(userId) { mutableStateOf<List<NotificationDto>>(emptyList()) }
    var loading by remember(userId) { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var savingIds by remember { mutableStateOf<Set<String>>(emptySet()) }

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

    LaunchedEffect(userId) { reload() }

    val unreadCount = notifications.count(NotificationRules::isUnread)

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
                    Text("Notifications", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (unreadCount == 0) "You're all caught up."
                        else "Unread notifications: $unreadCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { reload() }, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh notifications")
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
        } else if (notifications.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("No notifications yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "You'll see service assignments, response reminders, and team responses here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(notifications, key = { it.id }) { notification ->
                NotificationCard(
                    notification = notification,
                    saving = notification.id in savingIds,
                    onMarkRead = { markRead(notification) }
                )
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Text(
                "Notifications are saved to your UnityHub account and remain available when you sign in again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationDto,
    saving: Boolean,
    onMarkRead: () -> Unit
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
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = if (unread) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        NotificationRules.typeLabel(notification.notificationType),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (unread) {
                    Text(
                        "NEW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(notification.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                    TextButton(onClick = onMarkRead, enabled = !saving) {
                        if (saving) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        else {
                            Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Mark read")
                        }
                    }
                } else {
                    Text(
                        "Read",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
