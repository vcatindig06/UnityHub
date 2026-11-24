package com.unityhub.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.unityhub.app.data.SupabaseClientProvider
import com.unityhub.app.push.UnityHubNotificationManager
import io.github.jan.supabase.auth.auth

class RehearsalReminderWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID).orEmpty()
        val organizationId = inputData.getString(KEY_ORGANIZATION_ID).orEmpty()
        val rehearsalId = inputData.getString(KEY_REHEARSAL_ID).orEmpty()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val location = inputData.getString(KEY_LOCATION).orEmpty()
        val startLabel = inputData.getString(KEY_START_LABEL).orEmpty()
        val minutesBefore = inputData.getInt(KEY_MINUTES_BEFORE, -1)

        if (
            userId.isBlank() || organizationId.isBlank() || rehearsalId.isBlank() ||
            title.isBlank() || startLabel.isBlank() || minutesBefore <= 0
        ) return Result.failure()

        if (!RehearsalReminderScheduler.isStillScheduled(
                applicationContext,
                userId,
                rehearsalId,
                minutesBefore
            )
        ) return Result.success()

        val activeUserId = if (SupabaseClientProvider.isConfigured) {
            SupabaseClientProvider.client.auth.currentUserOrNull()?.id
        } else {
            null
        }
        if (activeUserId != userId) {
            RehearsalReminderScheduler.clearDelivered(applicationContext, userId, rehearsalId)
            return Result.success()
        }

        val timing = RehearsalReminderScheduler.labelFor(minutesBefore)
        val message = buildString {
            append("$title starts $startLabel")
            if (location.isNotBlank()) append(" · $location")
            append(" · Reminder: $timing")
        }
        UnityHubNotificationManager.show(
            context = applicationContext,
            title = "Rehearsal reminder",
            message = message,
            notificationId = "rehearsal-reminder:$userId:$rehearsalId",
            rehearsalId = rehearsalId,
            channelId = UnityHubNotificationManager.REHEARSAL_REMINDER_CHANNEL_ID,
            category = androidx.core.app.NotificationCompat.CATEGORY_REMINDER
        )
        RehearsalReminderScheduler.clearDelivered(applicationContext, userId, rehearsalId)
        return Result.success()
    }

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ORGANIZATION_ID = "organization_id"
        private const val KEY_REHEARSAL_ID = "rehearsal_id"
        private const val KEY_TITLE = "title"
        private const val KEY_LOCATION = "location"
        private const val KEY_START_LABEL = "start_label"
        private const val KEY_MINUTES_BEFORE = "minutes_before"
    }
}
