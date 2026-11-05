package com.unityhub.app.domain.chordpro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordProParserTest {
    @Test
    fun parsesMetadataSectionsAndInlineChords() {
        val document = ChordProParser.parse(
            """
            {title: Great Are You Lord}
            {artist: All Sons & Daughters}
            {key: G}
            {start_of_verse}
            [G]You give life [D/F#]You are love
            {end_of_verse}
            {start_of_chorus}
            [Em]It's Your [C]breath
            {end_of_chorus}
            """.trimIndent()
        )

        assertEquals("Great Are You Lord", document.title)
        assertEquals("All Sons & Daughters", document.artist)
        assertEquals("G", document.key)
        assertTrue(document.lines.any { it.section == "Verse" })
        assertTrue(document.lines.any { it.section == "Chorus" })
        val lyric = document.lines.first { it.lyrics.startsWith("You give life") }
        assertEquals(listOf("G", "D/F#"), lyric.chords.map { it.chord })
        assertEquals("You give life You are love", lyric.lyrics)
    }

    @Test
    fun parsesCommentsAndKeepsPlainLyrics() {
        val document = ChordProParser.parse("{comment: Build softly}\nAmazing grace")
        assertTrue(document.lines.first().comment)
        assertEquals("Build softly", document.lines.first().lyrics)
        assertEquals("Amazing grace", document.lines.last().lyrics)
        assertFalse(document.lines.last().comment)
    }

    @Test
    fun parsesTempoAndTimeSignatureMetadataForImport() {
        val document = ChordProParser.parse(
            """
            {tempo: 74}
            {time: 6/8}
            {start_of_intro}
            [G]Instrumental
            {end_of_intro}
            {start_of_refrain}
            [C]Sing together
            {end_of_refrain}
            """.trimIndent()
        )

        assertEquals(74, document.tempo)
        assertEquals("6/8", document.timeSignature)
        assertTrue(document.lines.any { it.section == "Intro" })
        assertTrue(document.lines.any { it.section == "Chorus" })
    }

    @Test
    fun ignoresOutOfRangeTempoAndInvalidTimeSignature() {
        val document = ChordProParser.parse("{tempo: 999}\n{time: fast}")
        assertEquals(null, document.tempo)
        assertEquals(null, document.timeSignature)
    }

    @Test
    fun transposesChordRootsAndSlashBassNotes() {
        assertEquals("A", ChordTransposer.transposeChord("G", 2))
        assertEquals("A/C#", ChordTransposer.transposeChord("G/B", 2))
        assertEquals("Bbmaj7/D", ChordTransposer.transposeChord("Abmaj7/C", 2))
        assertEquals("F#m7", ChordTransposer.transposeChord("Em7", 2))
    }

    @Test
    fun transposesKeysAndPreservesUnrecognizedText() {
        assertEquals("A", ChordTransposer.transposeKey("G", 2))
        assertEquals("Bb", ChordTransposer.transposeKey("Ab", 2))
        assertEquals("N.C.", ChordTransposer.transposeChord("N.C.", 2))
    }

    @Test
    fun zeroTransposeReturnsOriginalChord() {
        assertEquals("F#/A#", ChordTransposer.transposeChord("F#/A#", 0))
    }
}
