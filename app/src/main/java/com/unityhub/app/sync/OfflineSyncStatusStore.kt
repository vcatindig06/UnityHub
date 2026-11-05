package com.unityhub.app.sync

import android.content.Context

data class OfflineSyncStatusSnapshot(
    val lastSuccessfulSyncAtMillis: Long?,
    val lastError: String?
)

object OfflineSyncStatusStore {
    private const val PREFERENCES_NAME = "unityhub_offline_sync_status"
    private const val SUFFIX_LAST_SUCCESS = "last_success"
    private const val SUFFIX_LAST_ERROR = "last_error"

    fun read(
        context: Context,
        userId: String,
        organizationId: String
    ): OfflineSyncStatusSnapshot {
        if (userId.isBlank() || organizationId.isBlank()) {
            return OfflineSyncStatusSnapshot(null, null)
        }
        val preferences = preferences(context)
        return OfflineSyncStatusSnapshot(
            lastSuccessfulSyncAtMillis = preferences.getLong(
                key(userId, organizationId, SUFFIX_LAST_SUCCESS), 0L
            ).takeIf { it > 0L },
            lastError = preferences.getString(
                key(userId, organizationId, SUFFIX_LAST_ERROR), null
            )?.takeIf(String::isNotBlank)
        )
    }

    fun markStarted(context: Context, userId: String, organizationId: String) {
        if (userId.isBlank() || organizationId.isBlank()) return
        preferences(context).edit()
            .remove(key(userId, organizationId, SUFFIX_LAST_ERROR))
            .apply()
    }

    fun markSuccess(
        context: Context,
        userId: String,
        organizationId: String,
        syncedAtMillis: Long = System.currentTimeMillis()
    ) {
        if (userId.isBlank() || organizationId.isBlank() || syncedAtMillis <= 0L) return
        preferences(context).edit()
            .putLong(key(userId, organizationId, SUFFIX_LAST_SUCCESS), syncedAtMillis)
            .remove(key(userId, organizationId, SUFFIX_LAST_ERROR))
            .apply()
    }

    fun markFailure(
        context: Context,
        userId: String,
        organizationId: String,
        message: String
    ) {
        if (userId.isBlank() || organizationId.isBlank() || message.isBlank()) return
        preferences(context).edit()
            .putString(key(userId, organizationId, SUFFIX_LAST_ERROR), message.take(240))
            .apply()
    }

    fun clearUser(context: Context, userId: String) {
        if (userId.isBlank()) return
        val prefs = preferences(context)
        val prefix = "$userId:"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun key(userId: String, organizationId: String, suffix: String) =
        "$userId:$organizationId:$suffix"
}
