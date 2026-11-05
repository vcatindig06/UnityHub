package com.unityhub.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.unityhub.app.data.DevicePushTokenRepository
import com.unityhub.app.data.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UnityHubFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        getSharedPreferences(PUSH_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putString(KEY_FCM_TOKEN, token)
            .apply()

        if (SupabaseClientProvider.isConfigured) {
            serviceScope.launch {
                runCatching {
                    val client = SupabaseClientProvider.client
                    val userId = client.auth.currentUserOrNull()?.id ?: return@runCatching
                    DevicePushTokenRepository(client).registerToken(userId, token)
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "UnityHub"
        val body = message.notification?.body
            ?: message.data["message"]
            ?: "You have a new team update."

        UnityHubNotificationManager.show(
            context = this,
            title = title,
            message = body,
            notificationId = message.data["notification_id"],
            servicePlanId = message.data["service_plan_id"]
        )
    }

    companion object {
        const val PUSH_PREFERENCES = "unityhub_push_preferences"
        const val KEY_FCM_TOKEN = "fcm_token"
    }
}
