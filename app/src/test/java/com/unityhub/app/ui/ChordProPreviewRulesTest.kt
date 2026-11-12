package com.unityhub.app.ui

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
    fun previewBoundsLargeChartsWithoutChangingTheOriginalText() {
        val source = "Line\n".repeat(10_000)
        val preview = ChordProPreviewRules.previewSource(source)

        assertEquals(ChordProPreviewRules.MAX_PREVIEW_SOURCE_CHARACTERS, preview.length)
        assertTrue(ChordProPreviewRules.isTruncated(source))
        assertTrue(source.length > preview.length)
    }
}
