package com.unityhub.app.ui.theme

/**
 * Controls which UnityHub color palette is active.
 *
 * SYSTEM remains the default so existing installations continue to follow
 * Android's appearance setting until the user chooses otherwise.
 */
enum class AppearanceMode(
    val preferenceValue: String,
    val label: String,
    val description: String
) {
    SYSTEM(
        preferenceValue = "system",
        label = "System default",
        description = "Follow your Android device's appearance setting."
    ),
    LIGHT(
        preferenceValue = "light",
        label = "Light",
        description = "Always use UnityHub's light palette."
    ),
    DARK(
        preferenceValue = "dark",
        label = "Dark",
        description = "Always use UnityHub's dark palette."
    );

    fun usesDarkTheme(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        const val PREFERENCES_NAME = "unityhub_appearance"
        const val PREFERENCE_KEY = "appearance_mode"

        fun fromStoredValue(value: String?): AppearanceMode =
            entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM
    }
}
