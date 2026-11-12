package com.unityhub.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.unityhub.app.BuildConfig
import com.unityhub.app.data.AppUpdateManager
import com.unityhub.app.data.AppUpdateRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

class AppUpdateWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val manager = AppUpdateManager(applicationContext)
        if (!manager.isAutoDownloadEnabled()) return@withContext Result.success()

        try {
            val manifest = manager.fetchManifest()
            val latest = AppUpdateRules.latestRelease(manifest)
                ?: return@withContext Result.success()
            if (latest.versionCode <= BuildConfig.VERSION_CODE) {
                return@withContext Result.success()
            }
            if (!AppUpdateRules.hasValidDownloadMetadata(latest)) {
                return@withContext Result.failure()
            }
            if (manager.findVerifiedDownload(latest) != null) {
                return@withContext Result.success()
            }

            manager.downloadRelease(latest)
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }
}

object AppUpdateWorkScheduler {
    private const val UNIQUE_WORK_NAME = "unityhub-periodic-app-update-check"

    fun setEnabled(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
