package com.unityhub.app.ui

import com.unityhub.app.domain.chordpro.ChordProParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveStageRulesTest {
    @Test
    fun previousAndNextStayWithinSetlistBounds() {
        assertEquals(0, LiveStageRules.previousIndex(0, 4))
        assertEquals(1, LiveStageRules.previousIndex(2, 4))
        assertEquals(3, LiveStageRules.nextIndex(2, 4))
        assertEquals(3, LiveStageRules.nextIndex(3, 4))
    }

    @Test
    fun navigationHandlesEmptyAndOutOfRangeIndexes() {
        assertEquals(0, LiveStageRules.previousIndex(4, 0))
        assertEquals(0, LiveStageRules.nextIndex(-2, 0))
        assertEquals(0, LiveStageRules.previousIndex(-5, 3))
        assertEquals(2, LiveStageRules.nextIndex(12, 3))
    }

    @Test
    fun autoScrollDurationReflectsDistanceDensityAndSpeed() {
        assertEquals(10_000, LiveStageRules.autoScrollDurationMillis(480, 2f, 24))
        assertEquals(20_000, LiveStageRules.autoScrollDurationMillis(960, 2f, 24))
        assertEquals(20_000, LiveStageRules.autoScrollDurationMillis(480, 2f, 12))
    }

    @Test
    fun autoScrollDurationHandlesInvalidInputs() {
        assertEquals(0, LiveStageRules.autoScrollDurationMillis(0, 2f, 24))
        assertEquals(0, LiveStageRules.autoScrollDurationMillis(480, 0f, 24))
        assertEquals(0, LiveStageRules.autoScrollDurationMillis(480, 2f, 0))
    }

    @Test
    fun chordOnlyIntroLinesRemainRenderableInLiveStage() {
        val source = listOf(
            "{start_of_intro}",
            "[G] [C]",
            "{end_of_intro}"
        ).joinToString("\n")
        val document = ChordProParser.parse(source)
        val introHeader = document.lines.firstOrNull {
            it.section == "Intro" && it.chords.isEmpty()
        }
        val chordOnlyLine = document.lines.firstOrNull {
            it.section == "Intro" && it.chords.isNotEmpty()
        }

        org.junit.Assert.assertNotNull("Intro header should be parsed from its own ChordPro line.", introHeader)
        org.junit.Assert.assertNotNull("Chord-only Intro line should remain in the Intro section.", chordOnlyLine)
        val header = requireNotNull(introHeader)
        val chords = requireNotNull(chordOnlyLine)

        assertTrue(LiveStageRules.isSectionHeader(header))
        assertFalse(LiveStageRules.isSectionHeader(chords))
        assertTrue(LiveStageRules.hasRenderableChartContent(chords))
        assertEquals(listOf("G", "C"), chords.chords.map { it.chord })

        val renderedChordRow = buildChordRow(chords, transpose = 0)
        assertTrue(renderedChordRow.contains("G"))
        assertTrue(renderedChordRow.contains("C"))
    }

    @Test
    fun stageContentKeyResetsStateWhenServiceOrSetlistChanges() {
        val original = LiveStageRules.contentKey(
            serviceTitle = "Sunday Worship",
            itemFingerprints = listOf("song-1:0:chart-a", "song-2:1:chart-b")
        )

        assertEquals(
            original,
            LiveStageRules.contentKey(
                "Sunday Worship",
                listOf("song-1:0:chart-a", "song-2:1:chart-b")
            )
        )
        assertNotEquals(
            original,
            LiveStageRules.contentKey(
                "Sunday Worship",
                listOf("song-2:0:chart-b", "song-1:1:chart-a")
            )
        )
        assertNotEquals(
            original,
            LiveStageRules.contentKey(
                "Sunday Worship",
                listOf("song-1:0:chart-updated", "song-2:1:chart-b")
            )
        )
        assertNotEquals(
            original,
            LiveStageRules.contentKey(
                "Evening Worship",
                listOf("song-1:0:chart-a", "song-2:1:chart-b")
            )
        )
    }

    @Test
    fun transposeIsLimitedToElevenSemitonesInEachDirection() {
        assertEquals(1, LiveStageRules.adjustTranspose(0, 1))
        assertEquals(-1, LiveStageRules.adjustTranspose(0, -1))
        assertEquals(11, LiveStageRules.adjustTranspose(10, 5))
        assertEquals(-11, LiveStageRules.adjustTranspose(-10, -5))
        assertEquals(0, LiveStageRules.adjustTranspose(1, -1))
    }
}
