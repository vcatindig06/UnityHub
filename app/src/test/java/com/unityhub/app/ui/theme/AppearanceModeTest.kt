package com.unityhub.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceModeTest {
    @Test
    fun missingOrUnknownPreferenceDefaultsToSystem() {
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStoredValue(null))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStoredValue("unknown"))
    }

    @Test
    fun storedPreferenceRestoresEachMode() {
        AppearanceMode.entries.forEach { mode ->
            assertEquals(mode, AppearanceMode.fromStoredValue(mode.preferenceValue))
        }
    }

    @Test
    fun systemModeFollowsDeviceAppearance() {
        assertFalse(AppearanceMode.SYSTEM.usesDarkTheme(systemIsDark = false))
        assertTrue(AppearanceMode.SYSTEM.usesDarkTheme(systemIsDark = true))
    }

    @Test
    fun lightModeAlwaysUsesLightPalette() {
        assertFalse(AppearanceMode.LIGHT.usesDarkTheme(systemIsDark = false))
        assertFalse(AppearanceMode.LIGHT.usesDarkTheme(systemIsDark = true))
    }

    @Test
    fun darkModeAlwaysUsesDarkPalette() {
        assertTrue(AppearanceMode.DARK.usesDarkTheme(systemIsDark = false))
        assertTrue(AppearanceMode.DARK.usesDarkTheme(systemIsDark = true))
    }
}
