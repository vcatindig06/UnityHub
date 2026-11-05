package com.unityhub.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineSyncStatusRulesTest {

    @Test
    fun runningTakesPriorityOverQueuedOrPreviousErrors() {
        assertEquals(
            OfflineSyncDisplayState.RUNNING,
            OfflineSyncStatusRules.resolve(
                isRunning = true,
                isImmediateWorkQueued = true,
                hasError = true,
                hasSuccessfulSync = true
            )
        )
    }

    @Test
    fun queuedImmediateSyncTakesPriorityOverPreviousError() {
        assertEquals(
            OfflineSyncDisplayState.QUEUED,
            OfflineSyncStatusRules.resolve(
                isRunning = false,
                isImmediateWorkQueued = true,
                hasError = true,
                hasSuccessfulSync = false
            )
        )
    }

    @Test
    fun failedAttemptIsShownWhenNoSyncIsCurrentlyRunning() {
        assertEquals(
            OfflineSyncDisplayState.RETRYING,
            OfflineSyncStatusRules.resolve(
                isRunning = false,
                isImmediateWorkQueued = false,
                hasError = true,
                hasSuccessfulSync = true
            )
        )
    }

    @Test
    fun successfulSyncIsShownWhenThereIsNoActiveWorkOrError() {
        assertEquals(
            OfflineSyncDisplayState.SYNCED,
            OfflineSyncStatusRules.resolve(
                isRunning = false,
                isImmediateWorkQueued = false,
                hasError = false,
                hasSuccessfulSync = true
            )
        )
    }

    @Test
    fun missingWorkAndSyncHistoryIsShownAsNotSynced() {
        assertEquals(
            OfflineSyncDisplayState.NOT_SYNCED,
            OfflineSyncStatusRules.resolve(
                isRunning = false,
                isImmediateWorkQueued = false,
                hasError = false,
                hasSuccessfulSync = false
            )
        )
    }

    @Test
    fun progressStagesHaveReadableLabels() {
        assertEquals("Refreshing songs", OfflineSyncStatusRules.progressLabel("downloading_songs"))
        assertEquals("Saving offline copies", OfflineSyncStatusRules.progressLabel("saving_offline_copy"))
        assertEquals("Syncing offline data", OfflineSyncStatusRules.progressLabel("unknown"))
    }
}
