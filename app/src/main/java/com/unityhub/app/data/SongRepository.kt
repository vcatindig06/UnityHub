package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SongDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    val title: String,
    val artist: String,
    @SerialName("key_signature") val keySignature: String,
    val tempo: Int? = null,
    @SerialName("time_signature") val timeSignature: String,
    val chordpro: String,
    val notes: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("updated_by") val updatedBy: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
private data class SongFavoriteDto(
    @SerialName("user_id") val userId: String,
    @SerialName("song_id") val songId: String,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
private data class NewSongFavoriteDto(
    @SerialName("user_id") val userId: String,
    @SerialName("song_id") val songId: String
)

@Serializable
private data class NewSong(
    @SerialName("organization_id") val organizationId: String,
    val title: String,
    val artist: String,
    @SerialName("key_signature") val keySignature: String,
    val tempo: Int?,
    @SerialName("time_signature") val timeSignature: String,
    val chordpro: String,
    val notes: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("updated_by") val updatedBy: String
)

class SongRepository(private val client: SupabaseClient) {
    suspend fun loadSongs(organizationId: String): List<SongDto> =
        client.from("songs").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<SongDto>().sortedBy { it.title.lowercase() }

    suspend fun loadFavoriteSongIds(userId: String): Set<String> {
        require(userId.isNotBlank()) { "A signed-in user is required to load favorites." }
        return client.from("song_favorites").select {
            filter { eq("user_id", userId) }
        }.decodeList<SongFavoriteDto>().mapTo(linkedSetOf()) { it.songId }
    }

    suspend fun addSongFavorite(userId: String, songId: String) {
        require(userId.isNotBlank() && songId.isNotBlank()) {
            "A signed-in user and song are required to save a favorite."
        }
        client.from("song_favorites").insert(
            NewSongFavoriteDto(userId = userId, songId = songId)
        )
    }

    suspend fun removeSongFavorite(userId: String, songId: String) {
        require(userId.isNotBlank() && songId.isNotBlank()) {
            "A signed-in user and song are required to remove a favorite."
        }
        client.from("song_favorites").delete {
            filter {
                eq("user_id", userId)
                eq("song_id", songId)
            }
        }
    }

    suspend fun saveSong(
        organizationId: String,
        userId: String,
        existingId: String?,
        title: String,
        artist: String,
        keySignature: String,
        tempo: Int?,
        timeSignature: String,
        chordpro: String,
        notes: String
    ) {
        require(title.trim().length in 1..200) { "Enter a song title (up to 200 characters)." }
        require(tempo == null || tempo in 20..300) { "Tempo must be between 20 and 300 BPM." }

        if (existingId == null) {
            client.from("songs").insert(
                NewSong(
                    organizationId = organizationId,
                    title = title.trim(),
                    artist = artist.trim(),
                    keySignature = keySignature.trim(),
                    tempo = tempo,
                    timeSignature = timeSignature.trim(),
                    chordpro = chordpro,
                    notes = notes.trim(),
                    createdBy = userId,
                    updatedBy = userId
                )
            )
        } else {
            client.from("songs").update({
                set("title", title.trim())
                set("artist", artist.trim())
                set("key_signature", keySignature.trim())
                set("tempo", tempo)
                set("time_signature", timeSignature.trim())
                set("chordpro", chordpro)
                set("notes", notes.trim())
                set("updated_by", userId)
            }) {
                filter {
                    eq("id", existingId)
                    eq("organization_id", organizationId)
                }
            }
        }
    }

    suspend fun deleteSong(songId: String, organizationId: String) {
        client.from("songs").delete {
            filter {
                eq("id", songId)
                eq("organization_id", organizationId)
            }
        }
    }
}
