package com.unityhub.app.sync

internal enum class OfflineSyncDisplayState {
    RUNNING,
    QUEUED,
    RETRYING,
    SYNCED,
    NOT_SYNCED
}

internal object OfflineSyncStatusRules {
    fun resolve(
        isRunning: Boolean,
        isImmediateWorkQueued: Boolean,
        hasError: Boolean,
        hasSuccessfulSync: Boolean
    ): OfflineSyncDisplayState = when {
        isRunning -> OfflineSyncDisplayState.RUNNING
        isImmediateWorkQueued -> OfflineSyncDisplayState.QUEUED
        hasError -> OfflineSyncDisplayState.RETRYING
        hasSuccessfulSync -> OfflineSyncDisplayState.SYNCED
        else -> OfflineSyncDisplayState.NOT_SYNCED
    }

    fun progressLabel(stage: String?): String = when (stage) {
        "checking_access" -> "Checking account access"
        "downloading_songs" -> "Refreshing songs"
        "downloading_services" -> "Refreshing service plans and setlists"
        "downloading_assignments" -> "Refreshing team assignments"
        "saving_offline_copy" -> "Saving offline copies"
        else -> "Syncing offline data"
    }
}
