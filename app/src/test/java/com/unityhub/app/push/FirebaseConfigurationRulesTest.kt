package com.unityhub.app.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseConfigurationRulesTest {
    @Test
    fun incompleteFirebaseConfigurationKeepsPushDisabled() {
        assertFalse(
            FirebaseConfigurationRules.isComplete(
                apiKey = "",
                applicationId = "app-id",
                projectId = "project-id",
                senderId = "123456"
            )
        )
    }

    @Test
    fun completeFirebaseConfigurationEnablesInitialization() {
        assertTrue(
            FirebaseConfigurationRules.isComplete(
                apiKey = "public-api-key",
                applicationId = "1:123456:android:abcdef",
                projectId = "unityhub-project",
                senderId = "123456"
            )
        )
    }
}
