package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
private data class DevicePushTokenUpsert(
    @SerialName("user_id") val userId: String,
    val token: String,
    val platform: String = "android",
    @SerialName("updated_at") val updatedAt: String
)

class DevicePushTokenRepository(private val client: SupabaseClient) {
    suspend fun registerToken(userId: String, token: String) {
        require(userId.isNotBlank()) { "A signed-in user is required." }
        require(token.isNotBlank()) { "The Firebase device token is empty." }
        client.from("device_push_tokens").upsert(
            DevicePushTokenUpsert(userId, token, updatedAt = Instant.now().toString())
        ) { onConflict = "token" }
    }

    suspend fun unregisterToken(userId: String, token: String) {
        require(userId.isNotBlank() && token.isNotBlank())
        client.from("device_push_tokens").delete {
            filter {
                eq("user_id", userId)
                eq("token", token)
            }
        }
    }
}
