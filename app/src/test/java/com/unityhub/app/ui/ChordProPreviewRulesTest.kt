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
    fun previewBoundsLargeChartsWithoutChangingTheOriginalText() {
        val source = "Line\n".repeat(10_000)
        val preview = ChordProPreviewRules.previewSource(source)

        assertEquals(ChordProPreviewRules.MAX_PREVIEW_SOURCE_CHARACTERS, preview.length)
        assertTrue(ChordProPreviewRules.isTruncated(source))
        assertTrue(source.length > preview.length)
    }
}
