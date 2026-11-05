package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineServiceCacheRulesTest {
    private fun item(id: String, order: Int, createdAt: String) = ServicePlanItemDto(
        id = id,
        organizationId = "organization-a",
        servicePlanId = "service-a",
        songId = null,
        title = id,
        itemType = "other",
        chordproSnapshot = "",
        itemOrder = order,
        durationMinutes = null,
        notes = "",
        createdAt = createdAt
    )

    @Test
    fun cachedSetlistItemsAreOrderedByTheirServiceOrder() {
        val result = OfflineServiceCacheRules.orderedItems(
            listOf(
                item("third", 3, "2026-11-05T03:00:00Z"),
                item("first", 1, "2026-11-05T01:00:00Z"),
                item("second", 2, "2026-11-05T02:00:00Z")
            )
        )

        assertEquals(listOf("first", "second", "third"), result.map { it.id })
    }

    @Test
    fun cachedItemsWithTheSameOrderUseCreationTimeAsAStableFallback() {
        val result = OfflineServiceCacheRules.orderedItems(
            listOf(
                item("later", 1, "2026-11-05T02:00:00Z"),
                item("earlier", 1, "2026-11-05T01:00:00Z")
            )
        )

        assertEquals(listOf("earlier", "later"), result.map { it.id })
    }

    @Test
    fun emptySetlistsRemainEmpty() {
        assertEquals(emptyList<ServicePlanItemDto>(), OfflineServiceCacheRules.orderedItems(emptyList()))
    }
}
