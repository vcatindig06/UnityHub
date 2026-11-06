package com.unityhub.app.ui

internal object LiveStageRules {
    fun previousIndex(currentIndex: Int, itemCount: Int): Int {
        if (itemCount <= 0) return 0
        return currentIndex.coerceIn(0, itemCount - 1).minus(1).coerceAtLeast(0)
    }

    fun nextIndex(currentIndex: Int, itemCount: Int): Int {
        if (itemCount <= 0) return 0
        return currentIndex.coerceIn(0, itemCount - 1).plus(1).coerceAtMost(itemCount - 1)
    }

    fun adjustTranspose(currentTranspose: Int, semitoneDelta: Int): Int =
        (currentTranspose + semitoneDelta).coerceIn(-11, 11)
}
