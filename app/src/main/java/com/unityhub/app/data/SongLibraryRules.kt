package com.unityhub.app.data

/**
 * Keeps song-editor input bounded so large pastes cannot grow Compose state
 * or ChordPro preview allocations without limit.
 */
internal object SongEditorInputLimits {
    const val MAX_TITLE_CHARACTERS = 200
    const val MAX_ARTIST_CHARACTERS = 120
    const val MAX_KEY_SIGNATURE_CHARACTERS = 16
    const val MAX_TEMPO_CHARACTERS = 3
    const val MAX_TIME_SIGNATURE_CHARACTERS = 16
    const val MAX_CHORDPRO_CHARACTERS = 1_048_576
    const val MAX_CHORDPRO_BYTES = 1_048_576
    const val MAX_NOTES_CHARACTERS = 2_000

    fun limit(value: String, maximumCharacters: Int): String =
        value.take(maximumCharacters.coerceAtLeast(0))
}

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
