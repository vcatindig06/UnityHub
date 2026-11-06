package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class AccountProfile(
    val displayName: String,
    val email: String?
)

@Serializable
private data class ProfileRecordDto(
    val id: String,
    @SerialName("display_name") val displayName: String
)

internal object AccountProfileRules {
    fun normalizeDisplayName(value: String): String {
        val normalized = value.trim().replace(Regex("\\s+"), " ")
        require(normalized.length in 2..80) {
            "Your display name must contain 2 to 80 characters."
        }
        return normalized
    }

    fun isValidDisplayName(value: String): Boolean =
        runCatching { normalizeDisplayName(value) }.isSuccess

    fun displayNameFromMetadata(metadata: JsonObject?): String {
        if (metadata == null) return ""
        return listOf("display_name", "full_name", "name")
            .firstNotNullOfOrNull { key ->
                (metadata[key] as? JsonPrimitive)
                    ?.contentOrNull
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
            }
            .orEmpty()
    }

    fun resolveDisplayName(
        profileName: String?,
        metadata: JsonObject?,
        email: String?
    ): String =
        profileName?.trim()?.takeIf(String::isNotBlank)
            ?: displayNameFromMetadata(metadata)
                .takeIf(String::isNotBlank)
            ?: email?.substringBefore("@")?.trim()?.takeIf(String::isNotBlank)
            ?: "UnityHub member"
}

/**
 * The public profiles row is the source of truth for display names shared with
 * teammates, join requests, and the Team directory. The Auth metadata written at
 * registration is only an initial value; editing a name must update this row.
 *
 * The profiles_update_self RLS policy limits this update to the signed-in user.
 * Organization roles remain separate and continue to come from organization_members.
 */
class AccountRepository(
    private val client: SupabaseClient
) {
    suspend fun loadProfile(): AccountProfile {
        val user = client.auth.currentUserOrNull()
            ?: error("No signed-in account was found.")

        val profile = loadProfileRecord(user.id)
        return AccountProfile(
            displayName = AccountProfileRules.resolveDisplayName(
                profileName = profile?.displayName,
                metadata = user.userMetadata,
                email = user.email
            ),
            email = user.email
        )
    }

    suspend fun updateDisplayName(value: String): AccountProfile {
        val displayName = AccountProfileRules.normalizeDisplayName(value)
        val user = client.auth.currentUserOrNull()
            ?: error("No signed-in account was found.")

        check(loadProfileRecord(user.id) != null) {
            "Your profile record could not be found. Sign out and sign in again, then retry."
        }

        client.from("profiles").update({
            set("display_name", displayName)
        }) {
            filter {
                eq("id", user.id)
            }
        }

        val updatedProfile = loadProfileRecord(user.id)
        check(updatedProfile?.displayName == displayName) {
            "Your display name could not be saved to your team profile."
        }

        return AccountProfile(
            displayName = updatedProfile.displayName,
            email = user.email
        )
    }

    private suspend fun loadProfileRecord(userId: String): ProfileRecordDto? =
        client.from("profiles").select {
            filter { eq("id", userId) }
        }.decodeList<ProfileRecordDto>().firstOrNull()
}
