package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongLibraryRulesTest {

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
        time: String
    ) = SongDto(
        id = id,
        organizationId = "org-1",
        title = title,
        artist = artist,
        keySignature = key,
        tempo = null,
        timeSignature = time,
        chordpro = "",
        notes = "",
        createdBy = "member-1",
        updatedBy = "member-1",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z"
    )
}
