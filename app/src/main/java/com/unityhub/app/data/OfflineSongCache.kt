package com.unityhub.app.data

import android.content.Context
import com.unityhub.app.data.cache.CachedSongEntity
import com.unityhub.app.data.cache.UnityHubCacheDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OfflineSongSnapshot(
    val songs: List<SongDto>,
    val syncedAtMillis: Long?
)

class OfflineSongCache(context: Context) {
    private val dao = UnityHubCacheDatabase.getInstance(context).cachedSongDao()

    suspend fun saveSongs(
        userId: String,
        organizationId: String,
        songs: List<SongDto>
    ): Long = withContext(Dispatchers.IO) {
        require(userId.isNotBlank() && organizationId.isNotBlank())
        val timestamp = System.currentTimeMillis()
        val cached = songs.map { song ->
            CachedSongEntity().apply {
                id = song.id
                ownerUserId = userId
                this.organizationId = song.organizationId
                title = song.title
                artist = song.artist
                keySignature = song.keySignature
                tempo = song.tempo
                timeSignature = song.timeSignature
                chordpro = song.chordpro
                notes = song.notes
                createdBy = song.createdBy
                updatedBy = song.updatedBy
                createdAt = song.createdAt
                updatedAt = song.updatedAt
                cachedAt = timestamp
            }
        }
        dao.replaceOrganization(userId, organizationId, cached)
        timestamp
    }

    suspend fun loadSongs(userId: String, organizationId: String): OfflineSongSnapshot =
        withContext(Dispatchers.IO) {
            require(userId.isNotBlank() && organizationId.isNotBlank())
            val cached = dao.loadSongs(userId, organizationId)
            val syncedAt = dao.getLastSyncedAtMillis(userId, organizationId)
                .takeIf { it > 0L }
            OfflineSongSnapshot(
                songs = cached.map { song ->
                    SongDto(
                        id = song.id,
                        organizationId = song.organizationId,
                        title = song.title,
                        artist = song.artist,
                        keySignature = song.keySignature,
                        tempo = song.tempo,
                        timeSignature = song.timeSignature,
                        chordpro = song.chordpro,
                        notes = song.notes,
                        createdBy = song.createdBy,
                        updatedBy = song.updatedBy,
                        createdAt = song.createdAt,
                        updatedAt = song.updatedAt
                    )
                },
                syncedAtMillis = syncedAt
            )
        }

    suspend fun clearUser(userId: String) {
        if (userId.isBlank()) return
        withContext(Dispatchers.IO) { dao.clearUser(userId) }
    }
}
