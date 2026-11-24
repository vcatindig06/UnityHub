package com.unityhub.app.ui

/** Pure tab-history rules used by the workspace's Android system Back handling. */
internal object AppBackNavigationRules {
    const val HOME_TAB = 0
    private const val MAX_HISTORY_SIZE = 20

    fun recordTabChange(history: List<Int>, currentTab: Int, destinationTab: Int): List<Int> {
        if (currentTab == destinationTab) return history
        return (history + currentTab).takeLast(MAX_HISTORY_SIZE)
    }

    fun previousTab(currentTab: Int, history: List<Int>): Int? =
        history.lastOrNull() ?: if (currentTab != HOME_TAB) HOME_TAB else null

    fun popHistory(history: List<Int>): List<Int> =
        if (history.isEmpty()) history else history.dropLast(1)
}
