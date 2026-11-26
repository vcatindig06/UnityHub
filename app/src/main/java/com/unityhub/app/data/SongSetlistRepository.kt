package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class SongSetlistDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    val name: String,
    val description: String = "",
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class SongSetlistItemDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("setlist_id") val setlistId: String,
    @SerialName("song_id") val songId: String? = null,
    val title: String,
    val artist: String = "",
    @SerialName("key_signature") val keySignature: String = "",
    @SerialName("chordpro_snapshot") val chordproSnapshot: String = "",
    @SerialName("item_order") val itemOrder: Int,
    @SerialName("created_at") val createdAt: String
)

@Serializable
private data class NewSongSetlist(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    val name: String,
    val description: String,
    @SerialName("created_by") val createdBy: String
)

@Serializable
private data class NewSongSetlistItem(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("setlist_id") val setlistId: String,
    @SerialName("song_id") val songId: String?,
    val title: String,
    val artist: String,
    @SerialName("key_signature") val keySignature: String,
    @SerialName("chordpro_snapshot") val chordproSnapshot: String,
    @SerialName("item_order") val itemOrder: Int
)

internal object SongSetlistRules {
    const val MIN_SONGS = 2
    const val MAX_NAME_LENGTH = 100
    const val MAX_DESCRIPTION_LENGTH = 500

    fun normalizeName(value: String): String {
        val normalized = value.trim().replace(Regex("\\s+"), " ")
        require(normalized.length in 2..MAX_NAME_LENGTH) {
            "A setlist name must contain 2 to $MAX_NAME_LENGTH characters."
        }
        return normalized
    }

    fun normalizeDescription(value: String): String {
        val normalized = value.trim()
        require(normalized.length <= MAX_DESCRIPTION_LENGTH) {
            "Setlist notes must be $MAX_DESCRIPTION_LENGTH characters or fewer."
        }
        return normalized
    }

    fun canCreate(songCount: Int): Boolean = songCount >= MIN_SONGS

    fun canRemoveSong(songCount: Int): Boolean = songCount > MIN_SONGS
}

class SongSetlistRepository(private val client: SupabaseClient) {
    suspend fun loadSetlists(organizationId: String): List<SongSetlistDto> {
        require(organizationId.isNotBlank())
        return client.from("song_setlists").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<SongSetlistDto>()
            .sortedWith(compareByDescending<SongSetlistDto> { it.updatedAt }
                .thenBy { it.name.lowercase() })
    }

    suspend fun loadAllItems(organizationId: String): List<SongSetlistItemDto> {
        require(organizationId.isNotBlank())
        return client.from("song_setlist_items").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<SongSetlistItemDto>()
            .sortedWith(compareBy<SongSetlistItemDto> { it.setlistId }
                .thenBy { it.itemOrder }
                .thenBy { it.createdAt }
                .thenBy { it.id })
    }

    suspend fun loadItems(
        setlistId: String,
        organizationId: String
    ): List<SongSetlistItemDto> =
        loadAllItems(organizationId).filter { it.setlistId == setlistId }
            .sortedWith(compareBy<SongSetlistItemDto> { it.itemOrder }
                .thenBy { it.createdAt }
                .thenBy { it.id })

    suspend fun createSetlist(
        organizationId: String,
        userId: String,
        name: String,
        description: String,
        songs: List<SongDto>
    ): SongSetlistDto {
        require(SongSetlistRules.canCreate(songs.distinctBy { it.id }.size)) {
            "Choose at least two songs to create a setlist."
        }
        require(organizationId.isNotBlank() && userId.isNotBlank())
        val normalizedName = SongSetlistRules.normalizeName(name)
        val normalizedDescription = SongSetlistRules.normalizeDescription(description)
        val setlistId = UUID.randomUUID().toString()

        client.from("song_setlists").insert(
            NewSongSetlist(
                id = setlistId,
                organizationId = organizationId,
                name = normalizedName,
                description = normalizedDescription,
                createdBy = userId
            )
        )

        try {
            insertItems(
                organizationId = organizationId,
                setlistId = setlistId,
                items = songs.distinctBy { it.id }.mapIndexed { index, song ->
                    NewSongSetlistItem(
                        organizationId = organizationId,
                        setlistId = setlistId,
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        keySignature = song.keySignature,
                        chordproSnapshot = song.chordpro,
                        itemOrder = index
                    )
                }
            )
        } catch (exception: Exception) {
            runCatching { deleteSetlist(setlistId, organizationId) }
            throw exception
        }

        return loadSetlists(organizationId).firstOrNull { it.id == setlistId }
            ?: error("The setlist was saved but could not be loaded. Refresh the Songs page.")
    }

    suspend fun addSongs(
        setlistId: String,
        organizationId: String,
        songs: List<SongDto>
    ) {
        require(setlistId.isNotBlank() && organizationId.isNotBlank())
        val existing = loadItems(setlistId, organizationId)
        val existingSongIds = existing.mapNotNullTo(mutableSetOf()) { it.songId }
        val additions = songs.distinctBy { it.id }
            .filterNot { it.id in existingSongIds }
        if (additions.isEmpty()) return

        insertItems(
            organizationId = organizationId,
            setlistId = setlistId,
            items = additions.mapIndexed { index, song ->
                NewSongSetlistItem(
                    organizationId = organizationId,
                    setlistId = setlistId,
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    keySignature = song.keySignature,
                    chordproSnapshot = song.chordpro,
                    itemOrder = existing.size + index
                )
            }
        )
        touchSetlist(setlistId, organizationId)
    }

    suspend fun renameSetlist(
        setlistId: String,
        organizationId: String,
        name: String,
        description: String
    ) {
        require(setlistId.isNotBlank() && organizationId.isNotBlank())
        val normalizedName = SongSetlistRules.normalizeName(name)
        val normalizedDescription = SongSetlistRules.normalizeDescription(description)
        client.from("song_setlists").update({
            set("name", normalizedName)
            set("description", normalizedDescription)
            set("updated_at", Instant.now().toString())
        }) {
            filter {
                eq("id", setlistId)
                eq("organization_id", organizationId)
            }
        }
    }

    suspend fun removeItem(
        itemId: String,
        setlistId: String,
        organizationId: String
    ) {
        val items = loadItems(setlistId, organizationId)
        require(SongSetlistRules.canRemoveSong(items.size)) {
            "A setlist must contain at least two songs."
        }
        client.from("song_setlist_items").delete {
            filter {
                eq("id", itemId)
                eq("setlist_id", setlistId)
                eq("organization_id", organizationId)
            }
        }
        normalizeOrder(setlistId, organizationId)
        touchSetlist(setlistId, organizationId)
    }

    suspend fun reorderItems(
        setlistId: String,
        organizationId: String,
        orderedItemIds: List<String>
    ) {
        require(setlistId.isNotBlank() && organizationId.isNotBlank())
        val current = loadItems(setlistId, organizationId)
        require(orderedItemIds.size == current.size && orderedItemIds.toSet() == current.map { it.id }.toSet()) {
            "The setlist order changed. Refresh and try again."
        }
        val byId = current.associateBy { it.id }
        orderedItemIds.forEachIndexed { index, id ->
            val item = byId[id] ?: return@forEachIndexed
            if (item.itemOrder != index) updateItemOrder(item.id, setlistId, organizationId, index)
        }
        touchSetlist(setlistId, organizationId)
    }

    suspend fun moveItem(
        setlistId: String,
        organizationId: String,
        index: Int,
        direction: Int
    ) {
        require(direction == -1 || direction == 1)
        val items = loadItems(setlistId, organizationId)
        val target = index + direction
        if (index !in items.indices || target !in items.indices) return
        val currentItem = items[index]
        val targetItem = items[target]
        updateItemOrder(currentItem.id, setlistId, organizationId, targetItem.itemOrder)
        updateItemOrder(targetItem.id, setlistId, organizationId, currentItem.itemOrder)
        touchSetlist(setlistId, organizationId)
    }

    suspend fun duplicateSetlist(
        sourceSetlistId: String,
        organizationId: String,
        userId: String,
        newName: String
    ): SongSetlistDto {
        val source = loadSetlists(organizationId).firstOrNull { it.id == sourceSetlistId }
            ?: error("The selected setlist could not be found.")
        val sourceItems = loadItems(sourceSetlistId, organizationId)
        require(SongSetlistRules.canCreate(sourceItems.size)) {
            "The source setlist needs at least two songs before it can be duplicated."
        }
        val normalizedName = SongSetlistRules.normalizeName(newName)
        val newId = UUID.randomUUID().toString()
        client.from("song_setlists").insert(
            NewSongSetlist(
                id = newId,
                organizationId = organizationId,
                name = normalizedName,
                description = source.description,
                createdBy = userId
            )
        )
        try {
            insertItems(
                organizationId = organizationId,
                setlistId = newId,
                items = sourceItems.mapIndexed { index, item ->
                    NewSongSetlistItem(
                        organizationId = organizationId,
                        setlistId = newId,
                        songId = item.songId,
                        title = item.title,
                        artist = item.artist,
                        keySignature = item.keySignature,
                        chordproSnapshot = item.chordproSnapshot,
                        itemOrder = index
                    )
                }
            )
        } catch (exception: Exception) {
            runCatching { deleteSetlist(newId, organizationId) }
            throw exception
        }
        return loadSetlists(organizationId).firstOrNull { it.id == newId }
            ?: error("The duplicate was saved but could not be loaded.")
    }

    suspend fun deleteSetlist(setlistId: String, organizationId: String) {
        client.from("song_setlists").delete {
            filter {
                eq("id", setlistId)
                eq("organization_id", organizationId)
            }
        }
    }

    private suspend fun insertItems(
        organizationId: String,
        setlistId: String,
        items: List<NewSongSetlistItem>
    ) {
        if (items.isEmpty()) return
        client.from("song_setlist_items").insert(items)
    }

    private suspend fun updateItemOrder(
        itemId: String,
        setlistId: String,
        organizationId: String,
        newOrder: Int
    ) {
        client.from("song_setlist_items").update({
            set("item_order", newOrder)
        }) {
            filter {
                eq("id", itemId)
                eq("setlist_id", setlistId)
                eq("organization_id", organizationId)
            }
        }
    }

    private suspend fun normalizeOrder(setlistId: String, organizationId: String) {
        loadItems(setlistId, organizationId).forEachIndexed { index, item ->
            if (item.itemOrder != index) {
                updateItemOrder(item.id, setlistId, organizationId, index)
            }
        }
    }

    private suspend fun touchSetlist(setlistId: String, organizationId: String) {
        client.from("song_setlists").update({
            set("updated_at", Instant.now().toString())
        }) {
            filter {
                eq("id", setlistId)
                eq("organization_id", organizationId)
            }
        }
    }
}
