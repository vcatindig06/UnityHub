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
}
