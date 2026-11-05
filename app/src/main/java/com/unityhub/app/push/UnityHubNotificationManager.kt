package com.unityhub.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.unityhub.app.MainActivity
import com.unityhub.app.R

data class UnityHubPushNavigation(
    val notificationId: String?,
    val servicePlanId: String?
)

object UnityHubNotificationManager {
    const val CHANNEL_ID = "team_updates"
    const val EXTRA_NOTIFICATION_ID = "unityhub.notification_id"
    const val EXTRA_SERVICE_PLAN_ID = "unityhub.service_plan_id"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun show(
        context: Context,
        title: String,
        message: String,
        notificationId: String? = null,
        servicePlanId: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        createChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_SERVICE_PLAN_ID, servicePlanId)
        }
        val requestCode = notificationId?.hashCode() ?: 0
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_unityhub)
            .setContentTitle(title.ifBlank { "UnityHub" })
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        val id = notificationId?.hashCode()
            ?: (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
