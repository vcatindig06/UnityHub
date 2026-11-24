package com.unityhub.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingRehearsalAvailabilityResponseRulesTest {

    private val pending = PendingRehearsalAvailabilityResponse(
        assignmentId = "assignment-1",
        ownerUserId = "user-1",
        organizationId = "org-1",
        rehearsalId = "rehearsal-1",
        memberId = "membership-1",
        responseStatus = "available",
        queuedAt = 100L
    )

    @Test
    fun allowsOnlyTheOriginalUserAndMembershipToSubmit() {
        assertTrue(
            PendingRehearsalAvailabilityResponseRules.maySync(
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
    fun rejectsDifferentUsersOrganizationsAndMemberships() {
        assertFalse(
            PendingRehearsalAvailabilityResponseRules.maySync(
                authenticatedUserId = "user-2",
                requestedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-1",
                membershipId = "membership-1",
                pending = pending
            )
        )
        assertFalse(
            PendingRehearsalAvailabilityResponseRules.maySync(
                authenticatedUserId = "user-1",
                requestedUserId = "user-1",
                requestedOrganizationId = "org-2",
                membershipOrganizationId = "org-1",
                membershipId = "membership-1",
                pending = pending
            )
        )
        assertFalse(
            PendingRehearsalAvailabilityResponseRules.maySync(
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
    fun rejectsPendingRecordsWithMismatchedStoredScope() {
        assertFalse(
            PendingRehearsalAvailabilityResponseRules.maySync(
                authenticatedUserId = "user-1",
                requestedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-1",
                membershipId = "membership-1",
                pending = pending.copy(ownerUserId = "user-2")
            )
        )
        assertFalse(
            PendingRehearsalAvailabilityResponseRules.maySync(
                authenticatedUserId = "user-1",
                requestedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-1",
                membershipId = "membership-1",
                pending = pending.copy(organizationId = "org-2")
            )
        )
        assertFalse(
            PendingRehearsalAvailabilityResponseRules.maySync(
                authenticatedUserId = "user-1",
                requestedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-1",
                membershipId = "membership-1",
                pending = pending.copy(rehearsalId = "")
            )
        )
    }

    @Test
    fun acceptsOnlyAvailableOrUnavailableResponses() {
        assertTrue(PendingRehearsalAvailabilityResponseRules.isValidResponse("available"))
        assertTrue(PendingRehearsalAvailabilityResponseRules.isValidResponse("unavailable"))
        assertFalse(PendingRehearsalAvailabilityResponseRules.isValidResponse("pending"))
        assertFalse(PendingRehearsalAvailabilityResponseRules.isValidResponse("attended"))
        assertFalse(
            PendingRehearsalAvailabilityResponseRules.maySync(
                authenticatedUserId = "user-1",
                requestedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-1",
                membershipId = "membership-1",
                pending = pending.copy(responseStatus = "absent")
            )
        )
    }

    @Test
    fun rejectsMissingAuthenticationOrMembership() {
        assertFalse(
            PendingRehearsalAvailabilityResponseRules.maySync(
                authenticatedUserId = null,
                requestedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-1",
                membershipId = "membership-1",
                pending = pending
            )
        )
        assertFalse(
            PendingRehearsalAvailabilityResponseRules.maySync(
                authenticatedUserId = "user-1",
                requestedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = null,
                membershipId = "membership-1",
                pending = pending
            )
        )
    }
}
