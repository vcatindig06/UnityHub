package com.unityhub.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedFilePreviewRulesTest {
    @Test fun supportsImagesPdfAndText() {
        assertTrue(SharedFilePreviewRules.supportsInlinePreview("image/jpeg"))
        assertTrue(SharedFilePreviewRules.supportsInlinePreview("application/pdf"))
        assertTrue(SharedFilePreviewRules.supportsInlinePreview("text/plain; charset=utf-8"))
        assertTrue(SharedFilePreviewRules.supportsInlinePreview("text/csv"))
    }

    @Test fun routesOtherTypesToInstalledViewers() {
        assertFalse(SharedFilePreviewRules.supportsInlinePreview("audio/mpeg"))
        assertFalse(SharedFilePreviewRules.supportsInlinePreview("video/mp4"))
        assertFalse(SharedFilePreviewRules.supportsInlinePreview("application/vnd.ms-excel"))
    }

    @Test fun managementIsLimitedToUploaderAndLibraryRows() {
        val own = file("me", null)
        assertTrue(SharedFilePreviewRules.canManage(own, "me"))
        assertFalse(SharedFilePreviewRules.canManage(own, "other"))
        assertFalse(SharedFilePreviewRules.canManage(file("me", "conversation"), "me"))
    }

    private fun file(uploaderId: String, conversationId: String?) = ChatSharedFileDto(
        id = "f", organizationId = "org", conversationId = conversationId,
        uploaderId = uploaderId, storagePath = "org/library/$uploaderId/file",
        fileName = "guide.pdf", mimeType = "application/pdf", fileSize = 1024,
        sharedToTeam = true, createdAt = "2026-11-27T00:00:00Z", category = "documents"
    )
}
