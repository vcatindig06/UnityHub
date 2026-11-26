package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data class UserActivityDto(
    val id: String,
    @SerialName("actor_user_id") val actorUserId: String,
    @SerialName("organization_id") val organizationId: String? = null,
    @SerialName("event_key") val eventKey: String,
    val title: String,
    val description: String = "",
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String? = null,
    @SerialName("created_at") val createdAt: String
)

class ActivityHistoryRepository(private val client: SupabaseClient) {
    suspend fun loadOwnHistory(
        userId: String,
        organizationId: String,
        limit: Int = 30
    ): List<UserActivityDto> {
        require(userId.isNotBlank() && organizationId.isNotBlank()) {
            "A signed-in member and active team are required to load activity history."
        }
        return client.from("user_activity").select {
            filter {
                eq("actor_user_id", userId)
                eq("organization_id", organizationId)
            }
            order("created_at", Order.DESCENDING)
            limit(limit.coerceIn(1, 100).toLong())
        }.decodeList<UserActivityDto>()
    }
}

internal object ActivityHistoryRules {
    fun formatTimestamp(value: String): String {
        val instant = runCatching { Instant.parse(value) }.getOrNull() ?: return value
        return DateTimeFormatter
            .ofPattern("MMM d, yyyy · h:mm a", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }
}
