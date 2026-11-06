package com.unityhub.app.ui

import org.junit.Assert.assertEquals
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
    fun transposeIsLimitedToElevenSemitonesInEachDirection() {
        assertEquals(1, LiveStageRules.adjustTranspose(0, 1))
        assertEquals(-1, LiveStageRules.adjustTranspose(0, -1))
        assertEquals(11, LiveStageRules.adjustTranspose(10, 5))
        assertEquals(-11, LiveStageRules.adjustTranspose(-10, -5))
        assertEquals(0, LiveStageRules.adjustTranspose(1, -1))
    }
}
