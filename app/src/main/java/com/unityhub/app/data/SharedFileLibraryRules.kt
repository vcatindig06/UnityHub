package com.unityhub.app.data

enum class SharedFileCategory(val key: String, val label: String) {
    SONGS("songs", "Songs & charts"),
    SERVICES("services", "Services"),
    REHEARSALS("rehearsals", "Rehearsals"),
    MEDIA("media", "Media"),
    DOCUMENTS("documents", "Documents"),
    OTHER("other", "Other");

    companion object {
        fun fromKey(key: String): SharedFileCategory =
            values().firstOrNull { it.key == key } ?: OTHER
    }
}

internal object SharedFileLibraryRules {
    const val ALL_CATEGORIES = "all"
    const val SORT_NEWEST = "newest"
    const val SORT_OLDEST = "oldest"
    const val SORT_NAME = "name"

    fun filterAndSort(
        files: List<ChatSharedFileDto>,
        searchQuery: String,
        selectedCategory: String,
        sortOrder: String,
        uploaderNames: Map<String, String>
    ): List<ChatSharedFileDto> {
        val query = searchQuery.trim()
        val filtered = files.filter { file ->
            val matchesCategory = selectedCategory == ALL_CATEGORIES ||
                file.category == selectedCategory
            val matchesSearch = query.isBlank() ||
                file.fileName.contains(query, ignoreCase = true) ||
                file.mimeType.contains(query, ignoreCase = true) ||
                uploaderNames[file.uploaderId].orEmpty().contains(query, ignoreCase = true)
            matchesCategory && matchesSearch
        }

        return when (sortOrder) {
            SORT_OLDEST -> filtered.sortedWith(
                compareBy<ChatSharedFileDto> { it.createdAt }
                    .thenBy { it.fileName.lowercase() }
            )
            SORT_NAME -> filtered.sortedWith(
                compareBy<ChatSharedFileDto> { it.fileName.lowercase() }
                    .thenByDescending { it.createdAt }
            )
            else -> filtered.sortedWith(
                compareByDescending<ChatSharedFileDto> { it.createdAt }
                    .thenBy { it.fileName.lowercase() }
            )
        }
    }
}
