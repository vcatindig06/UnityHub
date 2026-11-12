package com.unityhub.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingServiceAssignmentResponseRulesTest {

    private val pending = PendingServiceAssignmentResponse(
        assignmentId = "assignment-1",
        ownerUserId = "user-1",
        organizationId = "org-1",
        memberId = "membership-1",
        responseStatus = "confirmed",
        responseNote = "",
        queuedAt = 100L
    )

    @Test
    fun allowsOnlyTheOriginalUserAndMembershipToSubmitAQueuedResponse() {
        assertTrue(
            PendingServiceAssignmentResponseRules.maySync(
                authenticatedUserId = "user-1",
                requestedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-1",
                membershipId = "membership-1",
                pending = pending
            )
        )
    }

    @Test
    fun rejectsADifferentSignedInUser() {
        assertFalse(
            PendingServiceAssignmentResponseRules.maySync(
                authenticatedUserId = "user-2",
                requestedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-1",
                membershipId = "membership-1",
                pending = pending
            )
        )
    }

    @Test
    fun rejectsAnotherOrganizationOrMembership() {
        assertFalse(
            PendingServiceAssignmentResponseRules.maySync(
                authenticatedUserId = "user-1",
                requestedUserId = "user-1",
                requestedOrganizationId = "org-2",
                membershipOrganizationId = "org-1",
                membershipId = "membership-1",
                pending = pending
            )
        )
        assertFalse(
            PendingServiceAssignmentResponseRules.maySync(
                authenticatedUserId = "user-1",
                requestedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-1",
                membershipId = "membership-2",
                pending = pending
            )
        )
    }

    @Test
    fun allowsAValidNoteOnlyForADeclinedResponse() {
        assertTrue(
            PendingServiceAssignmentResponseRules.maySync(
                authenticatedUserId = "user-1",
                requestedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-1",
                membershipId = "membership-1",
                pending = pending.copy(
                    responseStatus = "declined",
                    responseNote = "I have a schedule conflict."
                )
            )
        )
        assertFalse(
            PendingServiceAssignmentResponseRules.maySync(
                authenticatedUserId = "user-1",
                requestedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-1",
                membershipId = "membership-1",
                pending = pending.copy(responseNote = "This note should not accompany a confirmation.")
            )
        )
        assertFalse(
            PendingServiceAssignmentResponseRules.maySync(
                authenticatedUserId = "user-1",
                requestedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-1",
                membershipId = "membership-1",
                pending = pending.copy(
                    responseStatus = "declined",
                    responseNote = "N".repeat(301)
                )
            )
        )
    }

    @Test
    fun rejectsInvalidQueuedResponsesAndMissingScope() {
        assertFalse(
            PendingServiceAssignmentResponseRules.maySync(
                authenticatedUserId = null,
                requestedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-1",
                membershipId = "membership-1",
                pending = pending
            )
        )
        assertFalse(
            PendingServiceAssignmentResponseRules.maySync(
                authenticatedUserId = "user-1",
                requestedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = null,
                membershipId = "membership-1",
                pending = pending
            )
        )
        assertFalse(
            PendingServiceAssignmentResponseRules.maySync(
                authenticatedUserId = "user-1",
                requestedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-1",
                membershipId = "membership-1",
                pending = pending.copy(responseStatus = "pending")
            )
        )
    }
}
