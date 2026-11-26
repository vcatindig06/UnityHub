package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

data class AccountProfile(
    val displayName: String,
    val email: String?,
    val avatarPath: String? = null,
    val coverPath: String? = null,
    val bio: String = "",
    val socialLinks: Map<String, String> = emptyMap()
)

@Serializable
private data class ProfileRecordDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("cover_path") val coverPath: String? = null,
    val bio: String = "",
    @SerialName("social_links") val socialLinks: JsonObject = JsonObject(emptyMap())
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
            email = user.email,
            avatarPath = profile?.avatarPath,
            coverPath = profile?.coverPath,
            bio = profile?.bio.orEmpty(),
            socialLinks = profile?.socialLinks?.mapNotNull { (key, value) ->
                (value as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)?.let { key to it }
            }?.toMap().orEmpty()
        )
    }

    suspend fun loadPublicProfile(userId: String): AccountProfile {
        require(userId.isNotBlank()) { "Choose a valid team member." }
        val profile = loadProfileRecord(userId)
            ?: error("This member's profile is unavailable.")
        return AccountProfile(
            displayName = AccountProfileRules.resolveDisplayName(
                profileName = profile.displayName,
                metadata = null,
                email = null
            ),
            email = null,
            avatarPath = profile.avatarPath,
            coverPath = profile.coverPath,
            bio = profile.bio,
            socialLinks = profile.socialLinks.mapNotNull { (key, value) ->
                (value as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)?.let { key to it }
            }.toMap()
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
            ?: error("Your profile record could not be loaded after saving.")
        check(updatedProfile.displayName == displayName) {
            "Your display name could not be saved to your team profile."
        }

        return loadProfile()
    }

    suspend fun updateProfileDetails(
        bio: String,
        socialLinks: Map<String, String>
    ): AccountProfile {
        val user = client.auth.currentUserOrNull()
            ?: error("No signed-in account was found.")
        val normalizedBio = bio.trim()
        require(normalizedBio.length <= 300) { "Bio must be 300 characters or fewer." }
        val safeLinks = socialLinks.mapValues { (_, value) -> value.trim().take(250) }
            .filterValues(String::isNotBlank)
        val jsonLinks = buildJsonObject {
            safeLinks.forEach { (key, value) -> put(key, value) }
        }
        client.from("profiles").update({
            set("bio", normalizedBio)
            set("social_links", jsonLinks)
        }) {
            filter { eq("id", user.id) }
        }
        return loadProfile()
    }

    suspend fun uploadProfileImage(
        kind: String,
        bytes: ByteArray,
        mimeType: String
    ): String {
        require(kind == "avatar" || kind == "cover") { "Unsupported profile image type." }
        require(bytes.isNotEmpty() && bytes.size <= 8 * 1024 * 1024) {
            "Choose an image smaller than 8 MB."
        }
        val uploadContentType = when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> ContentType.Image.JPEG
            "image/png" -> ContentType.Image.PNG
            "image/webp" -> ContentType("image", "webp")
            else -> error("Choose a JPG, PNG or WebP image.")
        }
        val extension = when {
            uploadContentType == ContentType.Image.PNG -> "png"
            mimeType.equals("image/webp", ignoreCase = true) -> "webp"
            else -> "jpg"
        }
        val user = client.auth.currentUserOrNull()
            ?: error("No signed-in account was found.")
        val path = "${user.id}/$kind/${UUID.randomUUID()}.$extension"
        client.storage["profile-media"].upload(path, bytes) {
            upsert = false
            contentType = uploadContentType
        }
        client.from("profiles").update({
            if (kind == "avatar") set("avatar_path", path) else set("cover_path", path)
        }) {
            filter { eq("id", user.id) }
        }
        return path
    }

    fun publicMediaUrl(path: String?): String? =
        path?.takeIf(String::isNotBlank)?.let { client.storage["profile-media"].publicUrl(it) }

    private suspend fun loadProfileRecord(userId: String): ProfileRecordDto? =
        client.from("profiles").select {
            filter { eq("id", userId) }
        }.decodeList<ProfileRecordDto>().firstOrNull()
}
