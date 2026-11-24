package com.unityhub.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartAutoScrollRulesTest {
    @Test
    fun speedIsClampedToSupportedRange() {
        assertEquals(ChartAutoScrollRules.MIN_SPEED_DP_PER_SECOND, ChartAutoScrollRules.clampSpeed(-5f))
        assertEquals(ChartAutoScrollRules.MAX_SPEED_DP_PER_SECOND, ChartAutoScrollRules.clampSpeed(500f))
        assertEquals(48f, ChartAutoScrollRules.clampSpeed(48f))
    }

    @Test
    fun defaultSpeedIsWithinSupportedRange() {
        assertEquals(
            true,
            ChartAutoScrollRules.DEFAULT_SPEED_DP_PER_SECOND in
                ChartAutoScrollRules.MIN_SPEED_DP_PER_SECOND..ChartAutoScrollRules.MAX_SPEED_DP_PER_SECOND
        )
    }
}
