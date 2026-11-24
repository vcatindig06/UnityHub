package com.unityhub.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppBackNavigationRulesTest {
    @Test
    fun tabChangesAreRecordedInNavigationOrder() {
        val afterSongs = AppBackNavigationRules.recordTabChange(emptyList(), 0, 1)
        val afterServices = AppBackNavigationRules.recordTabChange(afterSongs, 1, 2)
        assertEquals(listOf(0, 1), afterServices)
    }

    @Test
    fun selectingTheCurrentTabDoesNotCreateAHistoryEntry() {
        assertEquals(listOf(0), AppBackNavigationRules.recordTabChange(listOf(0), 1, 1))
    }

    @Test
    fun backReturnsToTheMostRecentlyVisitedTabAndPopsHistory() {
        val history = listOf(0, 1)
        assertEquals(1, AppBackNavigationRules.previousTab(2, history))
        assertEquals(listOf(0), AppBackNavigationRules.popHistory(history))
    }

    @Test
    fun aNonHomeTabFallsBackToHomeWhenHistoryIsEmpty() {
        assertEquals(0, AppBackNavigationRules.previousTab(4, emptyList()))
    }

    @Test
    fun homeWithNoHistoryHasNoInAppDestinationToReturnTo() {
        assertNull(AppBackNavigationRules.previousTab(0, emptyList()))
    }

    @Test
    fun historyIsBoundedToAvoidUnboundedGrowth() {
        val history = (0..29).toList()
        assertEquals((11..30).toList(), AppBackNavigationRules.recordTabChange(history, 30, 1))
    }
}
