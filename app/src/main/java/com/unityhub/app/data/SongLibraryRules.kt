package com.unityhub.app.data

internal object SongLibraryRules {
    private val editorRoles = setOf("admin", "worship_leader", "song_editor")

    fun canEditSongs(
        role: String?,
        offlineOnly: Boolean,
        usingOfflineCopy: Boolean
    ): Boolean = !offlineOnly && !usingOfflineCopy &&
        (role?.trim()?.lowercase()?.let { it in editorRoles } == true)

    fun filterSongs(
        songs: List<SongDto>,
        searchQuery: String,
        favoritesOnly: Boolean,
        favoriteSongIds: Set<String>
    ): List<SongDto> {
        val query = searchQuery.trim()
        return songs.filter { song ->
            val matchesSearch = query.isBlank() ||
                song.title.contains(query, ignoreCase = true) ||
                song.artist.contains(query, ignoreCase = true) ||
                song.keySignature.contains(query, ignoreCase = true) ||
                song.timeSignature.contains(query, ignoreCase = true)

            matchesSearch && (!favoritesOnly || song.id in favoriteSongIds)
        }
    }
}
