package com.unityhub.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineWorkspaceCacheRulesTest {
    private val now = 1_800_000_000_000L

    @Test
    fun acceptsRecentWorkspaceForTheSameSignedInUser() {
        assertTrue(
            OfflineWorkspaceCacheRules.isUsable(
                currentUserId = "user-a",
                savedUserId = "user-a",
                savedAtMillis = now - 60_000L,
                nowMillis = now
            )
        )
    }

    @Test
    fun rejectsWorkspaceCachedForAnotherUser() {
        assertFalse(
            OfflineWorkspaceCacheRules.isUsable(
                currentUserId = "user-a",
                savedUserId = "user-b",
                savedAtMillis = now - 60_000L,
                nowMillis = now
            )
        )
    }

    @Test
    fun rejectsWorkspaceOlderThanSevenDays() {
        assertFalse(
            OfflineWorkspaceCacheRules.isUsable(
                currentUserId = "user-a",
                savedUserId = "user-a",
                savedAtMillis = now - OfflineWorkspaceCacheRules.MAX_AGE_MILLIS - 1L,
                nowMillis = now
            )
        )
    }

    @Test
    fun rejectsImplausibleFutureTimestamp() {
        assertFalse(
            OfflineWorkspaceCacheRules.isUsable(
                currentUserId = "user-a",
                savedUserId = "user-a",
                savedAtMillis = now + 10L * 60L * 1000L,
                nowMillis = now
            )
        )
    }

    @Test
    fun acceptsSmallClockSkew() {
        assertTrue(
            OfflineWorkspaceCacheRules.isUsable(
                currentUserId = "user-a",
                savedUserId = "user-a",
                savedAtMillis = now + 60_000L,
                nowMillis = now
            )
        )
    }
}
