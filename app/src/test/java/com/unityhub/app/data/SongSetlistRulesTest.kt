package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongSetlistRulesTest {
    @Test
    fun setlistRequiresAtLeastTwoSongs() {
        assertFalse(SongSetlistRules.canCreate(0))
        assertFalse(SongSetlistRules.canCreate(1))
        assertTrue(SongSetlistRules.canCreate(2))
        assertTrue(SongSetlistRules.canCreate(12))
    }

    @Test
    fun setlistCannotBeReducedBelowTwoSongs() {
        assertFalse(SongSetlistRules.canRemoveSong(2))
        assertTrue(SongSetlistRules.canRemoveSong(3))
    }

    @Test
    fun setlistNameIsTrimmedAndWhitespaceIsNormalized() {
        assertEquals("Sunday Worship", SongSetlistRules.normalizeName("  Sunday   Worship  "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun setlistNameMustHaveAtLeastTwoCharacters() {
        SongSetlistRules.normalizeName("A")
    }

    @Test(expected = IllegalArgumentException::class)
    fun setlistDescriptionIsBounded() {
        SongSetlistRules.normalizeDescription("x".repeat(SongSetlistRules.MAX_DESCRIPTION_LENGTH + 1))
    }
}
