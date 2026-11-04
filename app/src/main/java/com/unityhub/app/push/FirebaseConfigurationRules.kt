package com.unityhub.app.push

internal object FirebaseConfigurationRules {
    fun isComplete(
        apiKey: String,
        applicationId: String,
        projectId: String,
        senderId: String
    ): Boolean = listOf(apiKey, applicationId, projectId, senderId).all { it.isNotBlank() }
}
