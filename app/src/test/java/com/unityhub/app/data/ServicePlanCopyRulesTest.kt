package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicePlanCopyRulesTest {
    @Test
    fun copiesInSourceOrderAndPreservesChordProAndCustomItemFields() {
        val later = serviceItem(
            id = "later",
            order = 8,
            title = "Prayer",
            type = "prayer",
            duration = 5,
            notes = "Opening prayer"
        )
        val earlier = serviceItem(
            id = "earlier",
            order = 2,
            title = "Build My Life",
            type = "song",
            songId = "song-1",
            snapshot = "{title: Build My Life}\\n[G]Worthy of every song",
            notes = "Use the acoustic arrangement"
        )

        val result = ServicePlanCopyRules.prepareCopy(
            sourceItems = listOf(later, earlier),
            destinationItems = emptyList(),
            organizationId = "org-1",
            targetPlanId = "target-1"
        )

        assertEquals(listOf("Build My Life", "Prayer"), result.map { it.title })
        assertEquals(listOf(0, 1), result.map { it.itemOrder })
        assertEquals("target-1", result.first().servicePlanId)
        assertEquals("song-1", result.first().songId)
        assertEquals("{title: Build My Life}\\n[G]Worthy of every song", result.first().chordproSnapshot)
        assertEquals("Use the acoustic arrangement", result.first().notes)
        assertEquals(5, result.last().durationMinutes)
    }

    @Test
    fun copiedItemsAreAppendedAfterTheHighestExistingOrder() {
        val existing = listOf(
            serviceItem("existing-1", 0, "Current intro", planId = "target-1"),
            serviceItem("existing-2", 4, "Current song", planId = "target-1")
        )
        val source = listOf(
            serviceItem("source-1", 0, "Copied song"),
            serviceItem("source-2", 1, "Copied prayer")
        )

        val result = ServicePlanCopyRules.prepareCopy(source, existing, "org-1", "target-1")

        assertEquals(listOf(5, 6), result.map { it.itemOrder })
    }

    @Test
    fun anEmptyDestinationStartsTheCopiedSetlistAtZero() {
        val result = ServicePlanCopyRules.prepareCopy(
            sourceItems = listOf(serviceItem("source-1", 3, "Song")),
            destinationItems = emptyList(),
            organizationId = "org-1",
            targetPlanId = "target-1"
        )

        assertEquals(0, result.single().itemOrder)
    }

    @Test
    fun emptySourceAndCrossOrganizationItemsAreRejected() {
        assertTrue(
            runCatching {
                ServicePlanCopyRules.prepareCopy(emptyList(), emptyList(), "org-1", "target-1")
            }.isFailure
        )
        assertTrue(
            runCatching {
                ServicePlanCopyRules.prepareCopy(
                    listOf(serviceItem("foreign", 0, "Foreign song", organizationId = "org-2")),
                    emptyList(),
                    "org-1",
                    "target-1"
                )
            }.isFailure
        )
    }

    @Test
    fun destinationItemsMustBelongToTheTargetService() {
        assertTrue(
            runCatching {
                ServicePlanCopyRules.prepareCopy(
                    listOf(serviceItem("source", 0, "Song")),
                    listOf(serviceItem("destination", 0, "Other", planId = "wrong-target")),
                    "org-1",
                    "target-1"
                )
            }.isFailure
        )
    }

    private fun serviceItem(
        id: String,
        order: Int,
        title: String,
        type: String = "song",
        songId: String? = null,
        snapshot: String = "",
        duration: Int? = null,
        notes: String = "",
        organizationId: String = "org-1",
        planId: String = "source-1"
    ) = ServicePlanItemDto(
        id = id,
        organizationId = organizationId,
        servicePlanId = planId,
        songId = songId,
        title = title,
        itemType = type,
        chordproSnapshot = snapshot,
        itemOrder = order,
        durationMinutes = duration,
        notes = notes,
        createdAt = "2026-11-06T00:00:00Z"
    )
}
