package com.unityhub.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat

private val UnityHubLightColors = lightColorScheme(
    primary = UnityBlue,
    onPrimary = SurfaceWhite,
    primaryContainer = ColorTokens.PrimaryContainerLight,
    onPrimaryContainer = ColorTokens.OnPrimaryContainerLight,
    secondary = UnityEmerald,
    onSecondary = SurfaceWhite,
    secondaryContainer = ColorTokens.SecondaryContainerLight,
    onSecondaryContainer = ColorTokens.OnSecondaryContainerLight,
    tertiary = UnityCrimson,
    onTertiary = SurfaceWhite,
    tertiaryContainer = ColorTokens.TertiaryContainerLight,
    onTertiaryContainer = ColorTokens.OnTertiaryContainerLight,
    background = CanvasLight,
    onBackground = InkPrimary,
    surface = SurfaceWhite,
    onSurface = InkPrimary,
    surfaceVariant = ColorTokens.SurfaceVariantLight,
    onSurfaceVariant = InkSecondary,
    outline = ColorTokens.OutlineLight,
    outlineVariant = BorderSubtle,
    inverseSurface = ColorTokens.InverseSurfaceLight,
    inverseOnSurface = ColorTokens.InverseOnSurfaceLight,
    inversePrimary = ColorTokens.InversePrimaryLight,
    error = ColorTokens.ErrorLight,
    onError = SurfaceWhite,
    errorContainer = ColorTokens.ErrorContainerLight,
    onErrorContainer = ColorTokens.OnErrorContainerLight,
    surfaceTint = UnityBlue
)

private val UnityHubDarkColors = darkColorScheme(
    primary = ColorTokens.PrimaryDark,
    onPrimary = ColorTokens.OnPrimaryDark,
    primaryContainer = ColorTokens.PrimaryContainerDark,
    onPrimaryContainer = ColorTokens.OnPrimaryContainerDark,
    secondary = ColorTokens.SecondaryDark,
    onSecondary = ColorTokens.OnSecondaryDark,
    secondaryContainer = ColorTokens.SecondaryContainerDark,
    onSecondaryContainer = ColorTokens.OnSecondaryContainerDark,
    tertiary = ColorTokens.TertiaryDark,
    onTertiary = ColorTokens.OnTertiaryDark,
    tertiaryContainer = ColorTokens.TertiaryContainerDark,
    onTertiaryContainer = ColorTokens.OnTertiaryContainerDark,
    background = CanvasDark,
    onBackground = InkPrimaryDark,
    surface = SurfaceDark,
    onSurface = InkPrimaryDark,
    surfaceVariant = ColorTokens.SurfaceVariantDark,
    onSurfaceVariant = InkSecondaryDark,
    outline = ColorTokens.OutlineDark,
    outlineVariant = BorderDark,
    inverseSurface = ColorTokens.InverseSurfaceDark,
    inverseOnSurface = ColorTokens.InverseOnSurfaceDark,
    inversePrimary = ColorTokens.InversePrimaryDark,
    error = ColorTokens.ErrorDark,
    onError = ColorTokens.OnErrorDark,
    errorContainer = ColorTokens.ErrorContainerDark,
    onErrorContainer = ColorTokens.OnErrorContainerDark,
    surfaceTint = ColorTokens.PrimaryDark
)

private object ColorTokens {
    val PrimaryContainerLight = androidx.compose.ui.graphics.Color(0xFFDCEAF2)
    val OnPrimaryContainerLight = androidx.compose.ui.graphics.Color(0xFF18394D)
    val SecondaryContainerLight = androidx.compose.ui.graphics.Color(0xFFD9EEE6)
    val OnSecondaryContainerLight = androidx.compose.ui.graphics.Color(0xFF174A3B)
    val TertiaryContainerLight = androidx.compose.ui.graphics.Color(0xFFF5DDE1)
    val OnTertiaryContainerLight = androidx.compose.ui.graphics.Color(0xFF64232E)
    val SurfaceVariantLight = androidx.compose.ui.graphics.Color(0xFFE8EEF2)
    val OutlineLight = androidx.compose.ui.graphics.Color(0xFF64737D)
    val InverseSurfaceLight = androidx.compose.ui.graphics.Color(0xFF253541)
    val InverseOnSurfaceLight = androidx.compose.ui.graphics.Color(0xFFEAF0F4)
    val InversePrimaryLight = androidx.compose.ui.graphics.Color(0xFFA9CCE2)
    val ErrorLight = androidx.compose.ui.graphics.Color(0xFFB3261E)
    val ErrorContainerLight = androidx.compose.ui.graphics.Color(0xFFF9DEDC)
    val OnErrorContainerLight = androidx.compose.ui.graphics.Color(0xFF410E0B)

    val PrimaryDark = androidx.compose.ui.graphics.Color(0xFFA9CCE2)
    val OnPrimaryDark = androidx.compose.ui.graphics.Color(0xFF0C2738)
    val PrimaryContainerDark = androidx.compose.ui.graphics.Color(0xFF1D3E51)
    val OnPrimaryContainerDark = androidx.compose.ui.graphics.Color(0xFFDBEFF9)
    val SecondaryDark = androidx.compose.ui.graphics.Color(0xFF98D2BB)
    val OnSecondaryDark = androidx.compose.ui.graphics.Color(0xFF0A3024)
    val SecondaryContainerDark = androidx.compose.ui.graphics.Color(0xFF194B3C)
    val OnSecondaryContainerDark = androidx.compose.ui.graphics.Color(0xFFC9F0E2)
    val TertiaryDark = androidx.compose.ui.graphics.Color(0xFFF0ADB6)
    val OnTertiaryDark = androidx.compose.ui.graphics.Color(0xFF451822)
    val TertiaryContainerDark = androidx.compose.ui.graphics.Color(0xFF54222B)
    val OnTertiaryContainerDark = androidx.compose.ui.graphics.Color(0xFFFFE2E6)
    val SurfaceVariantDark = androidx.compose.ui.graphics.Color(0xFF273640)
    val OutlineDark = androidx.compose.ui.graphics.Color(0xFF84949F)
    val InverseSurfaceDark = androidx.compose.ui.graphics.Color(0xFFE6EDF2)
    val InverseOnSurfaceDark = androidx.compose.ui.graphics.Color(0xFF25333D)
    val InversePrimaryDark = androidx.compose.ui.graphics.Color(0xFF315B78)
    val ErrorDark = androidx.compose.ui.graphics.Color(0xFFFFB4AB)
    val OnErrorDark = androidx.compose.ui.graphics.Color(0xFF690005)
    val ErrorContainerDark = androidx.compose.ui.graphics.Color(0xFF93000A)
    val OnErrorContainerDark = androidx.compose.ui.graphics.Color(0xFFFFDAD6)
}

private val UnityHubBaseTypography = Typography()
private val UnityHubTypography = UnityHubBaseTypography.copy(
    headlineLarge = UnityHubBaseTypography.headlineLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = UnityHubBaseTypography.headlineMedium.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp
    ),
    headlineSmall = UnityHubBaseTypography.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    titleLarge = UnityHubBaseTypography.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    titleMedium = UnityHubBaseTypography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = UnityHubBaseTypography.bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = UnityHubBaseTypography.bodyMedium.copy(lineHeight = 22.sp),
    bodySmall = UnityHubBaseTypography.bodySmall.copy(lineHeight = 19.sp),
    labelLarge = UnityHubBaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

private val UnityHubShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun UnityHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) UnityHubDarkColors else UnityHubLightColors
    val view = LocalView.current

    SideEffect {
        val window = view.context.findActivity()?.window
        if (window != null) {
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = UnityHubTypography,
        shapes = UnityHubShapes,
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
