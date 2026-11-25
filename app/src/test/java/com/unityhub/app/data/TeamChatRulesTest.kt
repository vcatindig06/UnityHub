package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamChatRulesTest {
    @Test
    fun trimsMessagesBeforeSending() {
        assertEquals("Praise the Lord", TeamChatRules.normalizeMessage("  Praise the Lord  "))
    }

    @Test
    fun rejectsEmptyAndOverlongMessages() {
        assertFails { TeamChatRules.normalizeMessage("  ") }
        assertFails {
            TeamChatRules.normalizeMessage("x".repeat(TeamChatRules.MAX_MESSAGE_LENGTH + 1))
        }
    }

    @Test
    fun mergesRealtimeMessagesWithoutDuplicatesInChronologicalOrder() {
        val older = message("1", "2026-11-25T08:00:00Z")
        val middle = message("2", "2026-11-25T08:01:00Z")
        val newest = message("3", "2026-11-25T08:02:00Z")

        val result = TeamChatRules.mergeMessages(
            current = listOf(older, middle),
            incoming = listOf(middle, newest)
        )

        assertEquals(listOf("1", "2", "3"), result.map { it.id })
    }

    @Test
    fun readReceiptCountsOnlyOtherCurrentOrganizationMembers() {
        val message = message("2", "2026-11-25T08:01:00Z", sender = "sender")
        val states = listOf(
            readState("sender", "2", "2026-11-25T08:01:00Z"),
            readState("member-a", "3", "2026-11-25T08:02:00Z"),
            readState("member-b", "1", "2026-11-25T08:00:00Z"),
            readState("former-member", "3", "2026-11-25T08:02:00Z")
        )

        assertEquals(
            1,
            TeamChatRules.readByCount(
                message = message,
                readStates = states,
                currentMemberIds = setOf("sender", "member-a", "member-b")
            )
        )
    }

    @Test
    fun unreadCountOnlyIncludesIncomingMessagesAfterTheMemberCursor() {
        val messages = listOf(
            message("1", "2026-11-25T08:00:00Z", sender = "other"),
            message("2", "2026-11-25T08:01:00Z", sender = "me"),
            message("3", "2026-11-25T08:02:00Z", sender = "other"),
            message("4", "2026-11-25T08:03:00Z", sender = "other")
        )

        assertEquals(
            2,
            TeamChatRules.unreadCount(
                messages = messages,
                readState = readState("me", "2", "2026-11-25T08:01:00Z"),
                currentUserId = "me"
            )
        )
    }

    @Test
    fun readCursorCoversOlderMessagesOutsideTheLoadedPage() {
        val olderHistoryMessage = message(
            "00000000-0000-0000-0000-000000000001",
            "2026-11-25T06:00:00Z"
        )
        val visibleCursorMessage = message(
            "00000000-0000-0000-0000-000000000002",
            "2026-11-25T08:00:00Z"
        )
        val cursor = readState(
            "reader",
            visibleCursorMessage.id,
            visibleCursorMessage.createdAt
        )

        assertTrue(TeamChatRules.hasReadMessage(cursor, olderHistoryMessage))
    }

    @Test
    fun equalTimestampsUseMessageIdAsStableCursorTieBreaker() {
        val first = message("00000000-0000-0000-0000-000000000001", "2026-11-25T08:00:00Z")
        val second = message("00000000-0000-0000-0000-000000000002", "2026-11-25T08:00:00Z")
        val state = readState(
            "reader",
            first.id,
            "2026-11-25T08:00:00Z"
        )

        assertTrue(TeamChatRules.hasReadMessage(state, first))
        assertFalse(TeamChatRules.hasReadMessage(state, second))
    }

    @Test
    fun initiallyPositionsChatAtLatestMessage() {
        assertTrue(
            TeamChatRules.shouldFollowLatest(
                lastVisibleItemIndex = -1,
                totalItemCount = 25,
                hasPositionedList = false
            )
        )
    }

    @Test
    fun followsNewMessagesWhenReaderIsAlreadyAtTheBottom() {
        assertTrue(
            TeamChatRules.shouldFollowLatest(
                lastVisibleItemIndex = 18,
                totalItemCount = 20,
                hasPositionedList = true
            )
        )
    }

    @Test
    fun keepsReadingPositionWhenReaderHasScrolledUp() {
        assertFalse(
            TeamChatRules.shouldFollowLatest(
                lastVisibleItemIndex = 8,
                totalItemCount = 20,
                hasPositionedList = true
            )
        )
    }

    @Test
    fun doesNotScrollAnEmptyChat() {
        assertFalse(
            TeamChatRules.shouldFollowLatest(
                lastVisibleItemIndex = -1,
                totalItemCount = 0,
                hasPositionedList = false
            )
        )
    }

    private fun message(
        id: String,
        createdAt: String,
        sender: String = "other"
    ) = TeamChatMessageDto(
        id = id,
        organizationId = "org",
        senderId = sender,
        content = "Message $id",
        createdAt = createdAt
    )

    private fun readState(
        user: String,
        messageId: String,
        readAt: String
    ) = TeamChatReadStateDto(
        id = "state-$user",
        organizationId = "org",
        userId = user,
        lastReadMessageId = messageId,
        lastReadAt = readAt
    )

    private fun assertFails(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected validation to fail.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
