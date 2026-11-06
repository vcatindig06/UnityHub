package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class AccountProfile(
    val displayName: String,
    val email: String?
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
}

/**
 * Reads and updates profile information in Supabase Auth user metadata.
 *
 * Display names are user-editable profile information only. Organization roles and
 * access decisions must continue to come from the protected organization membership
 * records and Supabase Row Level Security policies.
 */
class AccountRepository(
    private val client: SupabaseClient
) {
    fun loadProfile(): AccountProfile {
        val user = client.auth.currentUserOrNull()
            ?: error("No signed-in account was found.")

        val displayName = AccountProfileRules.displayNameFromMetadata(user.userMetadata)
            .ifBlank { user.email?.substringBefore("@").orEmpty() }
            .ifBlank { "UnityHub member" }

        return AccountProfile(
            displayName = displayName,
            email = user.email
        )
    }

    suspend fun updateDisplayName(value: String): AccountProfile {
        val displayName = AccountProfileRules.normalizeDisplayName(value)

        client.auth.updateUser {
            data {
                put("display_name", displayName)
            }
        }

        return loadProfile()
    }
}
