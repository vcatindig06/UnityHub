package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TeamChatRulesTest {
    @Test
    fun trimsMessagesBeforeSending() {
        assertEquals("Praise the Lord", TeamChatRules.normalizeMessage("  Praise the Lord  "))
    }

    @Test
    fun rejectsEmptyAndOverlongMessages() {
        assertThrows(IllegalArgumentException::class.java) {
            TeamChatRules.normalizeMessage("  ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TeamChatRules.normalizeMessage("x".repeat(TeamChatRules.MAX_MESSAGE_LENGTH + 1))
        }
    }

    @Test
    fun keepsOnlyTheLatestOneHundredMessagesInChronologicalOrder() {
        val messages = (1..150).map { index ->
            TeamChatMessageDto(
                id = index.toString(),
                organizationId = "org",
                senderId = "member",
                content = "Message $index",
                createdAt = "2026-11-24T00:${(index / 60).toString().padStart(2, '0')}:${(index % 60).toString().padStart(2, '0')}Z"
            )
        }.reversed()

        val latest = TeamChatRules.latestMessages(messages)

        assertEquals(TeamChatRules.MESSAGE_HISTORY_LIMIT, latest.size)
        assertEquals("51", latest.first().id)
        assertEquals("150", latest.last().id)
    }
}
