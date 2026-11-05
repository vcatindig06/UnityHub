package com.unityhub.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

object OfflineSyncScheduler {
    private const val PERIODIC_INTERVAL_HOURS = 12L
    private const val BACKOFF_MINUTES = 30L
    private const val KEY_USER_ID = "user_id"
    private const val KEY_ORGANIZATION_ID = "organization_id"

    private fun periodicName(userId: String) = "unityhub-offline-sync-periodic:$userId"
    private fun immediateName(userId: String) = "unityhub-offline-sync-now:$userId"
    fun userTagFor(userId: String) = "unityhub-offline-sync-user:$userId"
    fun immediateTagFor(userId: String) = "unityhub-offline-sync-immediate:$userId"

    fun observeWork(context: Context, userId: String): Flow<List<WorkInfo>> =
        WorkManager.getInstance(context.applicationContext)
            .getWorkInfosByTagFlow(userTagFor(userId))

    fun schedule(context: Context, userId: String, organizationId: String) {
        if (userId.isBlank() || organizationId.isBlank()) return

        val manager = WorkManager.getInstance(context.applicationContext)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val input = workDataOf(
            KEY_USER_ID to userId,
            KEY_ORGANIZATION_ID to organizationId
        )
        val userTag = userTagFor(userId)

        val periodicRequest = PeriodicWorkRequestBuilder<OfflineSyncWorker>(
            PERIODIC_INTERVAL_HOURS,
            TimeUnit.HOURS
        )
            .setInputData(input)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .addTag(userTag)
            .addTag("unityhub-offline-sync-periodic:$userId")
            .build()

        manager.enqueueUniquePeriodicWork(
            periodicName(userId),
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )

        enqueueImmediate(manager, userId, input, constraints)
    }

    fun syncNow(context: Context, userId: String, organizationId: String) {
        if (userId.isBlank() || organizationId.isBlank()) return
        val manager = WorkManager.getInstance(context.applicationContext)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val input = workDataOf(
            KEY_USER_ID to userId,
            KEY_ORGANIZATION_ID to organizationId
        )
        enqueueImmediate(manager, userId, input, constraints)
    }

    private fun enqueueImmediate(
        manager: WorkManager,
        userId: String,
        input: androidx.work.Data,
        constraints: Constraints
    ) {
        val immediateRequest = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
            .setInputData(input)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .addTag(userTagFor(userId))
            .addTag(immediateTagFor(userId))
            .build()

        manager.enqueueUniqueWork(
            immediateName(userId),
            ExistingWorkPolicy.REPLACE,
            immediateRequest
        )
    }

    fun cancel(context: Context, userId: String) {
        if (userId.isBlank()) return
        WorkManager.getInstance(context.applicationContext)
            .cancelAllWorkByTag(userTagFor(userId))
    }
}
