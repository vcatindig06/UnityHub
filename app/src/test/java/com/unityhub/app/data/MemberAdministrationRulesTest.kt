package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberAdministrationRulesTest {

    @Test
    fun allDatabaseSupportedAccessRolesAreAvailable() {
        assertEquals(
            listOf("worship_leader", "song_editor", "team_member", "viewer", "admin"),
            TeamAccessRules.availableRoles
        )
    }

    @Test
    fun roleNormalizationTrimsAndLowercases() {
        assertEquals("worship_leader", TeamAccessRules.normalizeRole("  WORSHIP_LEADER "))
    }

    @Test
    fun unsupportedRoleIsRejectedBeforeRpcCall() {
        assertThrows(IllegalArgumentException::class.java) {
            TeamAccessRules.normalizeRole("super_admin")
        }
    }

    @Test
    fun roleLabelsAreReadable() {
        assertEquals("Administrator", TeamAccessRules.roleLabel("admin"))
        assertEquals("Worship Leader", TeamAccessRules.roleLabel("worship_leader"))
        assertEquals("Song Editor", TeamAccessRules.roleLabel("song_editor"))
        assertEquals("Viewer", TeamAccessRules.roleLabel("viewer"))
    }

    @Test
    fun onlyAdminsCanManageOtherMembersWhoAreNotTheOwner() {
        assertTrue(
            TeamAccessRules.canManageMember(
                isAdmin = true,
                currentUserId = "admin-1",
                organizationOwnerId = "owner-1",
                targetUserId = "member-1"
            )
        )
        assertFalse(
            TeamAccessRules.canManageMember(
                isAdmin = false,
                currentUserId = "leader-1",
                organizationOwnerId = "owner-1",
                targetUserId = "member-1"
            )
        )
        assertFalse(
            TeamAccessRules.canManageMember(
                isAdmin = true,
                currentUserId = "admin-1",
                organizationOwnerId = "owner-1",
                targetUserId = "owner-1"
            )
        )
        assertFalse(
            TeamAccessRules.canManageMember(
                isAdmin = true,
                currentUserId = "admin-1",
                organizationOwnerId = "owner-1",
                targetUserId = "admin-1"
            )
        )
    }

    @Test
    fun onlyCurrentOwnerCanTransferToAnotherExistingUser() {
        assertTrue(
            TeamAccessRules.canTransferOwnership(
                currentUserId = "owner-1",
                organizationOwnerId = "owner-1",
                targetUserId = "member-1"
            )
        )
        assertFalse(
            TeamAccessRules.canTransferOwnership(
                currentUserId = "admin-1",
                organizationOwnerId = "owner-1",
                targetUserId = "member-1"
            )
        )
        assertFalse(
            TeamAccessRules.canTransferOwnership(
                currentUserId = "owner-1",
                organizationOwnerId = "owner-1",
                targetUserId = "owner-1"
            )
        )
    }
}
