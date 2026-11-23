package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongLibraryRulesTest {

    @Test
    fun songEditorLimitsKeepSmallValuesAndBoundLargePastes() {
        assertEquals(
            "Build My Life",
            SongEditorInputLimits.limit("Build My Life", SongEditorInputLimits.MAX_TITLE_CHARACTERS)
        )
        assertEquals(
            SongEditorInputLimits.MAX_TITLE_CHARACTERS,
            SongEditorInputLimits.limit(
                "T".repeat(SongEditorInputLimits.MAX_TITLE_CHARACTERS + 50),
                SongEditorInputLimits.MAX_TITLE_CHARACTERS
            ).length
        )
        assertEquals(
            SongEditorInputLimits.MAX_CHORDPRO_CHARACTERS,
            SongEditorInputLimits.limit(
                "C".repeat(SongEditorInputLimits.MAX_CHORDPRO_CHARACTERS + 1),
                SongEditorInputLimits.MAX_CHORDPRO_CHARACTERS
            ).length
        )
        assertEquals(
            SongEditorInputLimits.MAX_NOTES_CHARACTERS,
            SongEditorInputLimits.limit(
                "N".repeat(SongEditorInputLimits.MAX_NOTES_CHARACTERS + 100),
                SongEditorInputLimits.MAX_NOTES_CHARACTERS
            ).length
        )
    }

    @Test
    fun supportedEditorRolesCanManageSongsWhenOnline() {
        listOf("admin", "worship_leader", "song_editor").forEach { role ->
            assertTrue(SongLibraryRules.canEditSongs(role, offlineOnly = false, usingOfflineCopy = false))
        }
        assertTrue(SongLibraryRules.canEditSongs(" ADMIN ", offlineOnly = false, usingOfflineCopy = false))
    }

    @Test
    fun regularMembersAndViewersCannotManageSharedSongs() {
        listOf("team_member", "viewer", null, "").forEach { role ->
            assertFalse(SongLibraryRules.canEditSongs(role, offlineOnly = false, usingOfflineCopy = false))
        }
    }

    @Test
    fun songManagementIsDisabledForOfflineLibraryCopies() {
        assertFalse(SongLibraryRules.canEditSongs("admin", offlineOnly = true, usingOfflineCopy = false))
        assertFalse(SongLibraryRules.canEditSongs("admin", offlineOnly = false, usingOfflineCopy = true))
    }

    @Test
    fun searchIncludesChordProLyricsChordsAndSongNotes() {
        val songs = listOf(
            testSong(
                id = "1",
                title = "Great Are You Lord",
                artist = "All Sons & Daughters",
                key = "A",
                time = "4/4",
                chordpro = "{title: Great Are You Lord}\\n[G]You give life [D/F#]You are love",
                notes = "Start softly and build into the chorus"
            ),
            testSong("2", "Build My Life", "Pat Barrett", "E", "6/8")
        )

        listOf("you give life", "D/F#", "BUILD INTO THE CHORUS").forEach { query ->
            assertEquals(
                "Search should find chart or note content for query: $query",
                listOf("1"),
                SongLibraryRules.filterSongs(
                    songs,
                    query,
                    favoritesOnly = false,
                    favoriteSongIds = emptySet()
                ).map { it.id }
            )
        }
    }

    @Test
    fun searchAndFavoritesFiltersCanBeCombined() {
        val songs = listOf(
            testSong("1", "Great Are You Lord", "All Sons & Daughters", "A", "4/4"),
            testSong("2", "Build My Life", "Pat Barrett", "E", "6/8"),
            testSong("3", "Holy Forever", "Chris Tomlin", "A", "4/4")
        )

        assertEquals(
            listOf("2"),
            SongLibraryRules.filterSongs(
                songs,
                "pat barrett",
                favoritesOnly = false,
                favoriteSongIds = emptySet()
            ).map { it.id }
        )
        assertEquals(
            listOf("1", "3"),
            SongLibraryRules.filterSongs(
                songs,
                "4/4",
                favoritesOnly = false,
                favoriteSongIds = emptySet()
            ).map { it.id }
        )
        assertEquals(
            listOf("2"),
            SongLibraryRules.filterSongs(
                songs,
                "",
                favoritesOnly = true,
                favoriteSongIds = setOf("2")
            ).map { it.id }
        )
    }

    private fun testSong(
        id: String,
        title: String,
        artist: String,
        key: String,
        time: String,
        chordpro: String = "",
        notes: String = ""
    ) = SongDto(
        id = id,
        organizationId = "org-1",
        title = title,
        artist = artist,
        keySignature = key,
        tempo = null,
        timeSignature = time,
        chordpro = chordpro,
        notes = notes,
        createdBy = "member-1",
        updatedBy = "member-1",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z"
    )
}
