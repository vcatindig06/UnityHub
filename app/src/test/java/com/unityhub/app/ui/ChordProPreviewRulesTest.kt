package com.unityhub.app.ui

import com.unityhub.app.domain.chordpro.ChordPosition
import com.unityhub.app.domain.chordpro.ChordProLine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordProPreviewRulesTest {
    @Test
    fun previewKeepsShortChartsComplete() {
        val source = "{title: Great Are You Lord}\n[G]Amazing grace"
        assertEquals(source, ChordProPreviewRules.previewSource(source))
        assertFalse(ChordProPreviewRules.isTruncated(source))
    }

    @Test
    fun renderedChordRowsHaveAResourceLimit() {
        val crowded = ChordProLine(
            lyrics = "many chords",
            chords = (0..2_000).map { ChordPosition(it, "Cmaj7") }
        )
        val row = buildChordRow(crowded, transpose = 0)

        assertTrue(row.length <= MAX_RENDERED_CHORD_ROW_CHARACTERS)
    }

    @Test
    fun previewParsingUsesTheLatestEditorTextImmediately() {
        val first = ChordProPreviewRules.parse("[G]First lyric")
        val second = ChordProPreviewRules.parse("[D]Second lyric")

        assertEquals("First lyric", first.document?.lines?.single()?.lyrics)
        assertEquals("Second lyric", second.document?.lines?.single()?.lyrics)
        assertEquals("D", second.document?.lines?.single()?.chords?.single()?.chord)
        assertFalse(second.failed)
    }

    @Test
    fun emptyPreviewDoesNotReportAParseFailure() {
        val result = ChordProPreviewRules.parse("  ")

        assertEquals(null, result.document)
        assertFalse(result.failed)
    }

    @Test
    fun rawChartFallbackRemovesControlCharactersAndBoundsOutput() {
        val source = "Line\u0000 one\n" + "x".repeat(MAX_RAW_CHART_FALLBACK_CHARACTERS + 100)
        val fallback = safeRawChartText(source)

        assertFalse(fallback.contains('\u0000'))
        assertTrue(fallback.length <= MAX_RAW_CHART_FALLBACK_CHARACTERS + 40)
        assertTrue(fallback.contains("shortened for safe display"))
    }

    @Test
    fun previewBoundsLargeChartsWithoutChangingTheOriginalText() {
        val source = "Line\n".repeat(10_000)
        val preview = ChordProPreviewRules.previewSource(source)

        assertEquals(ChordProPreviewRules.MAX_PREVIEW_SOURCE_CHARACTERS, preview.length)
        assertTrue(ChordProPreviewRules.isTruncated(source))
        assertTrue(source.length > preview.length)
    }
}
