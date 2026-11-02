package com.unityhub.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val UnityHubColors = lightColorScheme(
    primary = UnityBlue,
    onPrimary = SurfaceWhite,
    secondary = UnityEmerald,
    onSecondary = SurfaceWhite,
    tertiary = UnityCrimson,
    background = CanvasLight,
    onBackground = InkPrimary,
    surface = SurfaceWhite,
    onSurface = InkPrimary,
    surfaceVariant = BorderSubtle,
    onSurfaceVariant = InkSecondary
)

@Composable
fun UnityHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = UnityHubColors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
