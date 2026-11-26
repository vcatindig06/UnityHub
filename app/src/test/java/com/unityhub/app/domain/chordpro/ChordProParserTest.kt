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
    fun parsesTheExactVerseSampleUsedInSongEditor() {
        val document = ChordProParser.parse(
            "{start_of_verse}\n[D]Amazing\n{end_of_verse}"
        )

        assertEquals("Verse", document.lines.first().section)
        val lyric = document.lines.first { it.lyrics == "Amazing" }
        assertEquals("Verse", lyric.section)
        assertEquals(listOf("D"), lyric.chords.map { it.chord })
        assertFalse(document.isTruncated)
    }

    @Test
    fun normalizesUnicodeLineSeparatorsAndLeadingBom() {
        val document = ChordProParser.parse(
            "\uFEFF{start_of_verse}\u2028[D]Amazing\u2029{end_of_verse}"
        )

        assertTrue(document.lines.any { it.section == "Verse" })
        assertTrue(document.lines.any { it.lyrics == "Amazing" && it.chords.singleOrNull()?.chord == "D" })
    }

    @Test
    fun parsesNumberedVerseChorusAndBridgeDirectives() {
        val document = ChordProParser.parse(
            """
            {start_of_verse_1}
            [D]Verse one
            {end_of_verse_1}
            {start_of_chorus_1}
            [G]Chorus
            {end_of_chorus_1}
            {start_of_bridge_1}
            [A]Bridge
            {end_of_bridge_1}
            """.trimIndent()
        )

        assertTrue(document.lines.any { it.section == "Verse" })
        assertTrue(document.lines.any { it.section == "Chorus" })
        assertTrue(document.lines.any { it.section == "Bridge" })
        assertTrue(document.lines.any { it.lyrics == "Verse one" && it.section == "Verse" })
        assertTrue(document.lines.any { it.lyrics == "Chorus" && it.section == "Chorus" })
        assertTrue(document.lines.any { it.lyrics == "Bridge" && it.section == "Bridge" })
        assertFalse(document.lines.any { it.lyrics.contains("start_of_") })
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
    fun removesUnsupportedControlCharactersAndBoundsChordPositions() {
        val plain = ChordProParser.parse("Amazing\u0000 grace\u0001\n")
        assertEquals("Amazing grace", plain.lines.first().lyrics)

        val crowdedLine = (1..(ChordProParser.MAX_CHORDS_PER_LINE + 50))
            .joinToString("") { "[C]word " }
        val crowded = ChordProParser.parse(crowdedLine).lines.single()
        assertEquals(ChordProParser.MAX_CHORDS_PER_LINE, crowded.chords.size)
        assertFalse(crowded.lyrics.contains("["))
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
        val introLine = document.lines.first { it.lyrics == "Instrumental" }
        assertEquals("Intro", introLine.section)
        assertEquals(listOf("G"), introLine.chords.map { it.chord })
        assertTrue(document.lines.any { it.section == "Chorus" })
    }

    @Test
    fun parsesCapoDirectiveAndRejectsOutOfRangeValues() {
        assertEquals(5, ChordProParser.parse("{capo: 5}\n[G]Amazing").capo)
        assertEquals(null, ChordProParser.parse("{capo: 15}").capo)
        assertEquals(null, ChordProParser.parse("{capo: no}").capo)
    }

    @Test
    fun parsesSongCategoryMetadata() {
        assertEquals("Worship", ChordProParser.parse("{category: Worship}\\n[G]Amazing").category)
        assertEquals("Praise", ChordProParser.parse("{category: Praise}").category)
    }

    @Test
    fun ignoresOutOfRangeTempoAndInvalidTimeSignature() {
        val document = ChordProParser.parse("{tempo: 999}\n{time: fast}")
        assertEquals(null, document.tempo)
        assertEquals(null, document.timeSignature)
    }

    @Test
    fun limitsRenderedLineCountForVeryLargeSavedCharts() {
        val source = (1..(ChordProParser.MAX_DISPLAY_LINES + 250))
            .joinToString("\n") { "Amazing grace" }

        val document = ChordProParser.parse(source)

        assertEquals(ChordProParser.MAX_DISPLAY_LINES, document.lines.size)
        assertTrue(document.isTruncated)
        assertEquals("Amazing grace", document.lines.first().lyrics)
    }

    @Test
    fun limitsIndividualLyricsRowsWithoutChangingTheSavedSource() {
        val longLyrics = "x".repeat(ChordProParser.MAX_LINE_CHARACTERS + 500)
        val document = ChordProParser.parse(longLyrics)

        assertEquals(ChordProParser.MAX_LINE_CHARACTERS, document.lines.single().lyrics.length)
        assertTrue(document.isTruncated)
        assertEquals(ChordProParser.MAX_LINE_CHARACTERS + 500, longLyrics.length)
    }

    @Test
    fun normalizesWindowsAndLegacyMacLineEndingsBeforeApplyingDisplayLimits() {
        val document = ChordProParser.parse("First line\rSecond line\r\nThird line")

        assertEquals(
            listOf("First line", "Second line", "Third line"),
            document.lines.map { it.lyrics }
        )
        assertFalse(document.isTruncated)
    }

    @Test
    fun marksNormalChartsAsNotTruncated() {
        val document = ChordProParser.parse("[G]Amazing grace")

        assertFalse(document.isTruncated)
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
