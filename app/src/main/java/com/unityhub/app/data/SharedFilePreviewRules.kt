package com.unityhub.app.data

internal object SharedFilePreviewRules {
    private val inlineMimeTypes = setOf(
        "image/jpeg", "image/png", "image/webp",
        "application/pdf", "text/plain", "text/csv"
    )

    fun supportsInlinePreview(mimeType: String): Boolean =
        mimeType.substringBefore(';').trim().lowercase() in inlineMimeTypes

    fun canManage(file: ChatSharedFileDto, userId: String): Boolean =
        userId.isNotBlank() && file.uploaderId == userId &&
            file.conversationId == null && file.sharedToTeam
}
