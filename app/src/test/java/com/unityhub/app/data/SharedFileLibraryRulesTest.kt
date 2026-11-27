package com.unityhub.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedFileLibraryRulesTest {
    @Test
    fun searchesByFileNameMimeTypeAndUploader() {
        val files = listOf(
            file("1", "Sunday setlist.pdf", "application/pdf", "documents", "2026-11-20T09:00:00Z", "alex"),
            file("2", "Worship intro.mp3", "audio/mpeg", "media", "2026-11-21T09:00:00Z", "maria"),
            file("3", "Rehearsal notes.txt", "text/plain", "rehearsals", "2026-11-22T09:00:00Z", "alex")
        )
        val names = mapOf("alex" to "Alex Rivera", "maria" to "Maria Santos")

        assertEquals(
            listOf("1"),
            SharedFileLibraryRules.filterAndSort(
                files, "setlist", SharedFileLibraryRules.ALL_CATEGORIES,
                SharedFileLibraryRules.SORT_NEWEST, names
            ).map { it.id }
        )
        assertEquals(
            listOf("2"),
            SharedFileLibraryRules.filterAndSort(
                files, "audio", SharedFileLibraryRules.ALL_CATEGORIES,
                SharedFileLibraryRules.SORT_NEWEST, names
            ).map { it.id }
        )
        assertEquals(
            listOf("3", "1"),
            SharedFileLibraryRules.filterAndSort(
                files, "alex", SharedFileLibraryRules.ALL_CATEGORIES,
                SharedFileLibraryRules.SORT_NEWEST, names
            ).map { it.id }
        )
    }

    @Test
    fun filtersByCategoryBeforeSortingNewestFirst() {
        val files = listOf(
            file("1", "Older song.pdf", "application/pdf", "songs", "2026-11-20T09:00:00Z"),
            file("2", "Latest rehearsal.pdf", "application/pdf", "rehearsals", "2026-11-23T09:00:00Z"),
            file("3", "Latest song.chopro", "text/plain", "songs", "2026-11-24T09:00:00Z")
        )

        val result = SharedFileLibraryRules.filterAndSort(
            files, "", "songs", SharedFileLibraryRules.SORT_NEWEST, emptyMap()
        )

        assertEquals(listOf("3", "1"), result.map { it.id })
    }

    @Test
    fun supportsOldestAndAlphabeticalSortOrders() {
        val files = listOf(
            file("1", "Zebra.pdf", "application/pdf", "documents", "2026-11-21T09:00:00Z"),
            file("2", "Alpha.pdf", "application/pdf", "documents", "2026-11-23T09:00:00Z"),
            file("3", "Middle.pdf", "application/pdf", "documents", "2026-11-20T09:00:00Z")
        )

        assertEquals(
            listOf("3", "1", "2"),
            SharedFileLibraryRules.filterAndSort(
                files, "", SharedFileLibraryRules.ALL_CATEGORIES,
                SharedFileLibraryRules.SORT_OLDEST, emptyMap()
            ).map { it.id }
        )
        assertEquals(
            listOf("2", "3", "1"),
            SharedFileLibraryRules.filterAndSort(
                files, "", SharedFileLibraryRules.ALL_CATEGORIES,
                SharedFileLibraryRules.SORT_NAME, emptyMap()
            ).map { it.id }
        )
    }

    @Test
    fun unknownCategoryKeysFallBackToOther() {
        assertEquals(SharedFileCategory.OTHER, SharedFileCategory.fromKey("not-a-category"))
    }

    private fun file(
        id: String,
        name: String,
        mime: String,
        category: String,
        createdAt: String,
        uploaderId: String = "uploader"
    ) = ChatSharedFileDto(
        id = id,
        organizationId = "org",
        conversationId = null,
        uploaderId = uploaderId,
        storagePath = "org/library/uploader/$id-$name",
        fileName = name,
        mimeType = mime,
        fileSize = 1024,
        sharedToTeam = true,
        createdAt = createdAt,
        category = category
    )
}
