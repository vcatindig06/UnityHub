package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ServiceTemplateRulesTest {
    @Test
    fun normalizeNameTrimsAndRejectsInvalidLengths() {
        assertEquals("Sunday Worship", ServiceTemplateRules.normalizeName("  Sunday Worship  "))
        assertThrows(IllegalArgumentException::class.java) {
            ServiceTemplateRules.normalizeName(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServiceTemplateRules.normalizeName("x".repeat(101))
        }
    }

    @Test
    fun normalizeDescriptionTrimsAndRejectsMoreThanFiveHundredCharacters() {
        assertEquals("Weekly order", ServiceTemplateRules.normalizeDescription(" Weekly order "))
        assertThrows(IllegalArgumentException::class.java) {
            ServiceTemplateRules.normalizeDescription("x".repeat(501))
        }
    }

    @Test
    fun saveTemplatePreservesOrderedItemsAndChartSnapshots() {
        val prepared = ServiceTemplateRules.prepareTemplateItems(
            sourceItems = listOf(
                serviceItem(id = "later", order = 2, title = "Prayer"),
                serviceItem(id = "first", order = 0, title = "Opening Song", chordpro = "{title: Song}\\n[G]Grace"),
                serviceItem(id = "middle", order = 1, title = "Scripture", type = "reading")
            ),
            organizationId = ORG
        )

        assertEquals(listOf("Opening Song", "Scripture", "Prayer"), prepared.map { it.title })
        assertEquals(listOf(0, 1, 2), prepared.map { it.itemOrder })
        assertEquals("{title: Song}\\n[G]Grace", prepared.first().chordproSnapshot)
        assertEquals("reading", prepared[1].itemType)
    }

    @Test
    fun applyAppendsAfterExistingItemsAndKeepsSongAndCustomItemMetadata() {
        val prepared = ServiceTemplateRules.prepareItemsForService(
            templateItems = listOf(
                templateItem(id = "two", order = 1, title = "Welcome", songId = null, type = "other", duration = 3, notes = "Host"),
                templateItem(id = "one", order = 0, title = "Opening Song", songId = "song-1", chordpro = "{title: A}\\n[A]Amazing", duration = 5)
            ),
            destinationItems = listOf(
                serviceItem(id = "existing", order = 0, title = "Existing")
            ),
            organizationId = ORG,
            targetPlanId = PLAN
        )

        assertEquals(listOf("Opening Song", "Welcome"), prepared.map { it.title })
        assertEquals(listOf(1, 2), prepared.map { it.itemOrder })
        assertEquals("song-1", prepared.first().songId)
        assertEquals("{title: A}\\n[A]Amazing", prepared.first().chordproSnapshot)
        assertNull(prepared.last().songId)
        assertEquals("other", prepared.last().itemType)
        assertEquals(3, prepared.last().durationMinutes)
        assertEquals("Host", prepared.last().notes)
        assertTrueAllTarget( prepared )
    }

    @Test
    fun rejectsEmptyOrCrossOrganizationTemplateItems() {
        assertThrows(IllegalArgumentException::class.java) {
            ServiceTemplateRules.prepareTemplateItems(emptyList(), ORG)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServiceTemplateRules.prepareTemplateItems(
                listOf(serviceItem(organizationId = "other-org")),
                ORG
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServiceTemplateRules.prepareItemsForService(
                templateItems = listOf(templateItem(organizationId = "other-org")),
                destinationItems = emptyList(),
                organizationId = ORG,
                targetPlanId = PLAN
            )
        }
    }

    @Test
    fun rejectsTemplateItemsFromMultipleTemplatesAndWrongDestination() {
        assertThrows(IllegalArgumentException::class.java) {
            ServiceTemplateRules.prepareItemsForService(
                templateItems = listOf(
                    templateItem(id = "one", templateId = "template-1"),
                    templateItem(id = "two", templateId = "template-2")
                ),
                destinationItems = emptyList(),
                organizationId = ORG,
                targetPlanId = PLAN
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServiceTemplateRules.prepareItemsForService(
                templateItems = listOf(templateItem()),
                destinationItems = listOf(serviceItem(servicePlanId = "other-plan")),
                organizationId = ORG,
                targetPlanId = PLAN
            )
        }
    }

    private fun assertTrueAllTarget(items: List<PreparedServicePlanItemFromTemplate>) {
        items.forEach {
            org.junit.Assert.assertEquals(ORG, it.organizationId)
            org.junit.Assert.assertEquals(PLAN, it.servicePlanId)
        }
    }

    private fun serviceItem(
        id: String = "item",
        organizationId: String = ORG,
        servicePlanId: String = PLAN,
        order: Int = 0,
        title: String = "Song",
        type: String = "song",
        songId: String? = "song-1",
        chordpro: String = "",
        duration: Int? = null,
        notes: String = ""
    ) = ServicePlanItemDto(
        id = id,
        organizationId = organizationId,
        servicePlanId = servicePlanId,
        songId = songId,
        title = title,
        itemType = type,
        chordproSnapshot = chordpro,
        itemOrder = order,
        durationMinutes = duration,
        notes = notes,
        createdAt = "2026-11-01T10:00:00Z"
    )

    private fun templateItem(
        id: String = "template-item",
        organizationId: String = ORG,
        templateId: String = "template",
        order: Int = 0,
        title: String = "Song",
        type: String = "song",
        songId: String? = "song-1",
        chordpro: String = "",
        duration: Int? = null,
        notes: String = ""
    ) = ServiceTemplateItemDto(
        id = id,
        organizationId = organizationId,
        templateId = templateId,
        songId = songId,
        title = title,
        itemType = type,
        chordproSnapshot = chordpro,
        itemOrder = order,
        durationMinutes = duration,
        notes = notes,
        createdAt = "2026-11-01T10:00:00Z"
    )

    companion object {
        private const val ORG = "organization-1"
        private const val PLAN = "plan-1"
    }
}
