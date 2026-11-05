package com.unityhub.app.data

import android.content.Context

data class OfflineWorkspaceSnapshot(
    val organizationId: String,
    val organizationName: String
)

internal object OfflineWorkspaceCacheRules {
    const val MAX_AGE_MILLIS = 7L * 24L * 60L * 60L * 1000L
    private const val MAX_FUTURE_SKEW_MILLIS = 5L * 60L * 1000L

    fun isUsable(
        currentUserId: String,
        savedUserId: String?,
        savedAtMillis: Long,
        nowMillis: Long
    ): Boolean {
        if (currentUserId.isBlank() || savedUserId != currentUserId) return false
        if (savedAtMillis <= 0L || savedAtMillis > nowMillis + MAX_FUTURE_SKEW_MILLIS) return false
        return nowMillis - savedAtMillis <= MAX_AGE_MILLIS
    }
}

class OfflineWorkspaceCache(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun save(userId: String, organizationId: String, organizationName: String) {
        if (userId.isBlank() || organizationId.isBlank() || organizationName.isBlank()) return
        preferences.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_ORGANIZATION_ID, organizationId)
            .putString(KEY_ORGANIZATION_NAME, organizationName)
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun load(
        userId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): OfflineWorkspaceSnapshot? {
        val savedUserId = preferences.getString(KEY_USER_ID, null)
        val savedAt = preferences.getLong(KEY_SAVED_AT, 0L)
        if (!OfflineWorkspaceCacheRules.isUsable(userId, savedUserId, savedAt, nowMillis)) {
            return null
        }
        val organizationId = preferences.getString(KEY_ORGANIZATION_ID, null)
            ?.takeIf(String::isNotBlank) ?: return null
        val organizationName = preferences.getString(KEY_ORGANIZATION_NAME, null)
            ?.takeIf(String::isNotBlank) ?: return null
        return OfflineWorkspaceSnapshot(organizationId, organizationName)
    }

    fun clear(userId: String) {
        if (userId.isNotBlank() && preferences.getString(KEY_USER_ID, null) == userId) {
            preferences.edit().clear().apply()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "unityhub_offline_workspace"
        const val KEY_USER_ID = "user_id"
        const val KEY_ORGANIZATION_ID = "organization_id"
        const val KEY_ORGANIZATION_NAME = "organization_name"
        const val KEY_SAVED_AT = "saved_at"
    }
}
