package com.unityhub.app.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.unityhub.app.data.RehearsalDto
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.Instant
import java.util.concurrent.TimeUnit

object RehearsalReminderScheduler {
    const val ONE_DAY_BEFORE_MINUTES = 24 * 60
    const val ONE_HOUR_BEFORE_MINUTES = 60
    const val FIFTEEN_MINUTES_BEFORE_MINUTES = 15

    private const val PREFERENCES_NAME = "unityhub_rehearsal_reminders"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_ORGANIZATION_ID = "organization_id"
    private const val KEY_REHEARSAL_ID = "rehearsal_id"
    private const val KEY_TITLE = "title"
    private const val KEY_LOCATION = "location"
    private const val KEY_START_LABEL = "start_label"
    private const val KEY_MINUTES_BEFORE = "minutes_before"

    private fun uniqueName(userId: String, rehearsalId: String) =
        "unityhub-rehearsal-reminder:$userId:$rehearsalId"

    private fun userTag(userId: String) = "unityhub-rehearsal-reminder-user:$userId"

    private fun preferenceKey(userId: String, rehearsalId: String) =
        "$userId:$rehearsalId:minutes_before"

    fun labelFor(minutesBefore: Int): String = when (minutesBefore) {
        ONE_DAY_BEFORE_MINUTES -> "1 day before"
        ONE_HOUR_BEFORE_MINUTES -> "1 hour before"
        FIFTEEN_MINUTES_BEFORE_MINUTES -> "15 minutes before"
        else -> "No reminder"
    }

    fun getMinutesBefore(
        context: Context,
        userId: String,
        rehearsalId: String
    ): Int? {
        if (userId.isBlank() || rehearsalId.isBlank()) return null
        return preferences(context).getInt(preferenceKey(userId, rehearsalId), -1)
            .takeIf { it in supportedOptions() }
    }

    fun schedule(
        context: Context,
        userId: String,
        organizationId: String,
        rehearsal: RehearsalDto,
        minutesBefore: Int,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        require(userId.isNotBlank() && organizationId.isNotBlank())
        require(rehearsal.id.isNotBlank() && rehearsal.organizationId == organizationId)
        require(minutesBefore in supportedOptions()) {
            "Choose a supported rehearsal reminder time."
        }
        require(rehearsal.status == "scheduled") {
            "Only scheduled rehearsals can have reminders."
        }

        val date = LocalDate.parse(rehearsal.rehearsalDate)
        val startsAt = LocalDateTime.of(date, LocalTime.parse(rehearsal.startTime))
            .atZone(zoneId)
            .toInstant()
        val reminderAt = startsAt.minusSeconds(minutesBefore.toLong() * 60L)
        val delayMillis = Duration.between(now, reminderAt).toMillis()
        if (delayMillis <= 0L) {
            cancel(context, userId, rehearsal.id)
            return false
        }

        val startLabel = LocalDateTime.of(date, LocalTime.parse(rehearsal.startTime))
            .format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a"))

        val input = workDataOf(
            KEY_USER_ID to userId,
            KEY_ORGANIZATION_ID to organizationId,
            KEY_REHEARSAL_ID to rehearsal.id,
            KEY_TITLE to rehearsal.title,
            KEY_LOCATION to rehearsal.location,
            KEY_START_LABEL to startLabel,
            KEY_MINUTES_BEFORE to minutesBefore
        )
        val request = OneTimeWorkRequestBuilder<RehearsalReminderWorker>()
            .setInputData(input)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(userTag(userId))
            .addTag(uniqueName(userId, rehearsal.id))
            .build()

        preferences(context).edit()
            .putInt(preferenceKey(userId, rehearsal.id), minutesBefore)
            .apply()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            uniqueName(userId, rehearsal.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
        return true
    }

    fun cancel(context: Context, userId: String, rehearsalId: String) {
        if (userId.isBlank() || rehearsalId.isBlank()) return
        preferences(context).edit()
            .remove(preferenceKey(userId, rehearsalId))
            .apply()
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(uniqueName(userId, rehearsalId))
    }

    fun cancelAllForUser(context: Context, userId: String) {
        if (userId.isBlank()) return
        val prefs = preferences(context)
        val prefix = "$userId:"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.apply()
        WorkManager.getInstance(context.applicationContext)
            .cancelAllWorkByTag(userTag(userId))
    }

    internal fun isStillScheduled(
        context: Context,
        userId: String,
        rehearsalId: String,
        minutesBefore: Int
    ): Boolean =
        getMinutesBefore(context, userId, rehearsalId) == minutesBefore

    internal fun clearDelivered(context: Context, userId: String, rehearsalId: String) {
        preferences(context).edit()
            .remove(preferenceKey(userId, rehearsalId))
            .apply()
    }

    private fun supportedOptions() = setOf(
        ONE_DAY_BEFORE_MINUTES,
        ONE_HOUR_BEFORE_MINUTES,
        FIFTEEN_MINUTES_BEFORE_MINUTES
    )

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
