package com.unityhub.app.ui

import kotlin.math.ceil

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

    fun autoScrollDurationMillis(
        distancePx: Int,
        density: Float,
        speedDpPerSecond: Int
    ): Int {
        if (distancePx <= 0 || density <= 0f || speedDpPerSecond <= 0) return 0
        return ceil(distancePx / density / speedDpPerSecond * 1_000.0)
            .toInt()
            .coerceAtLeast(1)
    }
}
