package com.unityhub.app.ui.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthPasswordRulesTest {
    @Test
    fun acceptsPasswordsAtTheMinimumLength() {
        assertTrue(isValidUnityHubPassword("UnityHubPass1"))
    }

    @Test
    fun rejectsPasswordsShorterThanTwelveCharacters() {
        assertFalse(isValidUnityHubPassword("ShortPass1"))
    }

    @Test
    fun rejectsPasswordsOverSeventyTwoUtf8Bytes() {
        assertFalse(isValidUnityHubPassword("あ".repeat(25)))
    }

    @Test
    fun acceptsMultibytePasswordsWithinTheByteLimit() {
        assertTrue(isValidUnityHubPassword("あ".repeat(12)))
    }

    @Test
    fun rejectsEmptyPasswords() {
        assertFalse(isValidUnityHubPassword(""))
    }

    @Test
    fun givesClearFeedbackForInvalidEmailOrPassword() {
        val message = authFailureMessage("Invalid login credentials", creatingAccount = false)
        assertTrue(message.contains("email or password is incorrect", ignoreCase = true))
    }

    @Test
    fun explainsWhenEmailConfirmationIsRequired() {
        val message = authFailureMessage("Email not confirmed", creatingAccount = false)
        assertTrue(message.contains("has not been confirmed", ignoreCase = true))
    }

    @Test
    fun handlesRateLimitsWithAUsefulRetryMessage() {
        val message = authFailureMessage("Too many requests", creatingAccount = false)
        assertTrue(message.contains("wait", ignoreCase = true))
    }

    @Test
    fun givesSignupSpecificFeedback() {
        val message = authFailureMessage("Unknown registration error", creatingAccount = true)
        assertTrue(message.contains("create your account", ignoreCase = true))
    }
}
