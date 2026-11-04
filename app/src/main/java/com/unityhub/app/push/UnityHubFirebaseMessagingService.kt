package com.unityhub.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class UnityHubFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        getSharedPreferences(PUSH_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putString(KEY_FCM_TOKEN, token)
            .apply()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "UnityHub"
        val body = message.notification?.body
            ?: message.data["message"]
            ?: "You have a new team update."
        UnityHubNotificationManager.show(this, title, body)
    }

    companion object {
        const val PUSH_PREFERENCES = "unityhub_push_preferences"
        const val KEY_FCM_TOKEN = "fcm_token"
    }
}
