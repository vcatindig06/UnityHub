package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class NotificationDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("recipient_user_id") val recipientUserId: String,
    @SerialName("notification_type") val notificationType: String,
    val title: String,
    val message: String,
    @SerialName("service_plan_id") val servicePlanId: String? = null,
    @SerialName("assignment_id") val assignmentId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null
)

internal object NotificationRules {
    fun isUnread(notification: NotificationDto): Boolean = notification.readAt == null

    fun hasServicePlan(notification: NotificationDto): Boolean =
        !notification.servicePlanId.isNullOrBlank()

    fun filterNotifications(
        notifications: List<NotificationDto>,
        unreadOnly: Boolean
    ): List<NotificationDto> =
        if (unreadOnly) notifications.filter(::isUnread) else notifications

    fun typeLabel(type: String): String = when (type) {
        "assignment_created" -> "New assignment"
        "assignment_reminder" -> "Response reminder"
        "assignment_response" -> "Team response"
        else -> "Notification"
    }

    fun formatCreatedAt(value: String): String {
        val instant = runCatching { Instant.parse(value) }.getOrNull() ?: return value
        val formatter = java.time.format.DateTimeFormatter
            .ofPattern("MMM d, yyyy · h:mm a", java.util.Locale.getDefault())
            .withZone(java.time.ZoneId.systemDefault())
        return formatter.format(instant)
    }
}

class NotificationsRepository(private val client: SupabaseClient) {

    suspend fun loadNotifications(userId: String): List<NotificationDto> {
        require(userId.isNotBlank()) { "No signed-in user was found." }
        return client.from("notifications").select {
            filter { eq("recipient_user_id", userId) }
        }.decodeList<NotificationDto>()
            .sortedByDescending { it.createdAt }
    }

    suspend fun markAsRead(notificationId: String, userId: String) {
        require(notificationId.isNotBlank() && userId.isNotBlank())
        client.from("notifications").update({
            set("read_at", Instant.now().toString())
        }) {
            filter {
                eq("id", notificationId)
                eq("recipient_user_id", userId)
            }
        }
    }

    suspend fun markAllAsRead(notificationIds: List<String>, userId: String) {
        require(userId.isNotBlank()) { "No signed-in user was found." }
        val ids = notificationIds.asSequence()
            .filter(String::isNotBlank)
            .distinct()
            .toList()
        if (ids.isEmpty()) return

        val readAt = Instant.now().toString()
        for (notificationId in ids) {
            client.from("notifications").update({
                set("read_at", readAt)
            }) {
                filter {
                    eq("id", notificationId)
                    eq("recipient_user_id", userId)
                }
            }
        }
    }
}
