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

    private fun assignmentEntry(
        id: String,
        role: String,
        createdAt: String,
        responseStatus: String,
        isCurrentUser: Boolean,
        memberDisplayName: String
    ) = OfflineServiceAssignmentEntry(
        assignment = ServiceAssignmentDto(
            id = id,
            organizationId = "organization-a",
            servicePlanId = "service-a",
            memberId = "member-$id",
            roleLabel = role,
            responseStatus = responseStatus,
            responseNote = "",
            assignedBy = "",
            respondedAt = if (responseStatus == "pending") null else createdAt,
            createdAt = createdAt
        ),
        isCurrentUser = isCurrentUser,
        memberDisplayName = memberDisplayName
    )

    @Test
    fun cachedAssignmentsAreOrderedByRoleAndThenCreationTime() {
        val result = OfflineServiceCacheRules.orderedAssignments(
            listOf(
                assignmentEntry("later", "Drums", "2026-11-05T02:00:00Z", "pending", false, "Casey"),
                assignmentEntry("vocal", "Main Vocal", "2026-11-05T01:00:00Z", "confirmed", true, "Jordan"),
                assignmentEntry("earlier", "Drums", "2026-11-05T01:00:00Z", "declined", false, "Alex")
            )
        )

        assertEquals(listOf("earlier", "later", "vocal"), result.map { it.assignment.id })
    }

    @Test
    fun cachedAssignmentsRetainLastKnownResponseAndMemberIdentity() {
        val result = OfflineServiceCacheRules.orderedAssignments(
            listOf(
                assignmentEntry("a", "Keys", "2026-11-05T01:00:00Z", "confirmed", false, "Alex"),
                assignmentEntry("b", "Drums", "2026-11-05T02:00:00Z", "declined", true, "Jordan"),
                assignmentEntry("c", "Bass", "2026-11-05T03:00:00Z", "pending", false, "Casey")
            )
        ).associateBy { it.assignment.id }

        assertEquals("confirmed", result.getValue("a").assignment.responseStatus)
        assertEquals("declined", result.getValue("b").assignment.responseStatus)
        assertEquals("pending", result.getValue("c").assignment.responseStatus)
        assertEquals(true, result.getValue("b").isCurrentUser)
        assertEquals("Jordan", result.getValue("b").memberDisplayName)
    }

    @Test
    fun emptyAssignmentSnapshotsRemainEmpty() {
        assertEquals(
            emptyList<OfflineServiceAssignmentEntry>(),
            OfflineServiceCacheRules.orderedAssignments(emptyList())
        )
    }
}
