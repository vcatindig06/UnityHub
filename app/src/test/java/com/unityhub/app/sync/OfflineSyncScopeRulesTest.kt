package com.unityhub.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSyncScopeRulesTest {

    @Test
    fun allowsOnlyTheSameSignedInUserAndActiveOrganization() {
        assertTrue(
            OfflineSyncScopeRules.maySync(
                requestedUserId = "user-1",
                authenticatedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-1"
            )
        )
    }

    @Test
    fun rejectsAnotherSignedInUser() {
        assertFalse(
            OfflineSyncScopeRules.maySync(
                requestedUserId = "user-1",
                authenticatedUserId = "user-2",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-1"
            )
        )
    }

    @Test
    fun rejectsMembershipFromAnotherOrganization() {
        assertFalse(
            OfflineSyncScopeRules.maySync(
                requestedUserId = "user-1",
                authenticatedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-2"
            )
        )
    }

    @Test
    fun rejectsMissingSessionMembershipAndBlankIdentifiers() {
        assertFalse(
            OfflineSyncScopeRules.maySync(
                requestedUserId = "user-1",
                authenticatedUserId = null,
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-1"
            )
        )
        assertFalse(
            OfflineSyncScopeRules.maySync(
                requestedUserId = "user-1",
                authenticatedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = null
            )
        )
        assertFalse(
            OfflineSyncScopeRules.maySync(
                requestedUserId = "",
                authenticatedUserId = "user-1",
                requestedOrganizationId = "org-1",
                membershipOrganizationId = "org-1"
            )
        )
    }
}
