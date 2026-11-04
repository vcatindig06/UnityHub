package com.unityhub.app.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.unityhub.app.BuildConfig

object UnityHubFirebase {
    val isConfigured: Boolean
        get() = FirebaseConfigurationRules.isComplete(
            apiKey = BuildConfig.FIREBASE_API_KEY,
            applicationId = BuildConfig.FIREBASE_APPLICATION_ID,
            projectId = BuildConfig.FIREBASE_PROJECT_ID,
            senderId = BuildConfig.FIREBASE_SENDER_ID
        )

    fun initialize(context: Context): Boolean {
        if (!isConfigured) return false

        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build()
            FirebaseApp.initializeApp(context.applicationContext, options)
        }
        return FirebaseApp.getApps(context).isNotEmpty()
    }
}
