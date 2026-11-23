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
    onSecondary = InkPrimary,
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

internal object ColorTokens {
    val PrimaryContainerLight = androidx.compose.ui.graphics.Color(0xFFDCE8DC)
    val OnPrimaryContainerLight = androidx.compose.ui.graphics.Color(0xFF193B2B)
    val SecondaryContainerLight = androidx.compose.ui.graphics.Color(0xFFEFE5CA)
    val OnSecondaryContainerLight = androidx.compose.ui.graphics.Color(0xFF4B3A1C)
    val TertiaryContainerLight = androidx.compose.ui.graphics.Color(0xFFF3DED4)
    val OnTertiaryContainerLight = androidx.compose.ui.graphics.Color(0xFF5C2E20)
    val SurfaceVariantLight = androidx.compose.ui.graphics.Color(0xFFEDE5D6)
    val OutlineLight = androidx.compose.ui.graphics.Color(0xFF716858)
    val InverseSurfaceLight = androidx.compose.ui.graphics.Color(0xFF283A2D)
    val InverseOnSurfaceLight = androidx.compose.ui.graphics.Color(0xFFF7F1E5)
    val InversePrimaryLight = androidx.compose.ui.graphics.Color(0xFFA9C7AE)
    val ErrorLight = androidx.compose.ui.graphics.Color(0xFFB3261E)
    val ErrorContainerLight = androidx.compose.ui.graphics.Color(0xFFF9DEDC)
    val OnErrorContainerLight = androidx.compose.ui.graphics.Color(0xFF410E0B)

    val PrimaryDark = androidx.compose.ui.graphics.Color(0xFFA9C7AE)
    val OnPrimaryDark = androidx.compose.ui.graphics.Color(0xFF173A28)
    val PrimaryContainerDark = androidx.compose.ui.graphics.Color(0xFF294A37)
    val OnPrimaryContainerDark = androidx.compose.ui.graphics.Color(0xFFE1EEE0)
    val SecondaryDark = androidx.compose.ui.graphics.Color(0xFFD5BD7A)
    val OnSecondaryDark = androidx.compose.ui.graphics.Color(0xFF3B3019)
    val SecondaryContainerDark = androidx.compose.ui.graphics.Color(0xFF4D4127)
    val OnSecondaryContainerDark = androidx.compose.ui.graphics.Color(0xFFF0E3BE)
    val TertiaryDark = androidx.compose.ui.graphics.Color(0xFFE0A089)
    val OnTertiaryDark = androidx.compose.ui.graphics.Color(0xFF512B20)
    val TertiaryContainerDark = androidx.compose.ui.graphics.Color(0xFF5B3428)
    val OnTertiaryContainerDark = androidx.compose.ui.graphics.Color(0xFFF5DDD3)
    val SurfaceVariantDark = androidx.compose.ui.graphics.Color(0xFF2D4034)
    val OutlineDark = androidx.compose.ui.graphics.Color(0xFFA9B7A8)
    val InverseSurfaceDark = androidx.compose.ui.graphics.Color(0xFFF5EBDD)
    val InverseOnSurfaceDark = androidx.compose.ui.graphics.Color(0xFF24362A)
    val InversePrimaryDark = androidx.compose.ui.graphics.Color(0xFF315C46)
    val ErrorDark = androidx.compose.ui.graphics.Color(0xFFFFB4A7)
    val OnErrorDark = androidx.compose.ui.graphics.Color(0xFF680E0A)
    val ErrorContainerDark = androidx.compose.ui.graphics.Color(0xFF8D1A12)
    val OnErrorContainerDark = androidx.compose.ui.graphics.Color(0xFFFFDAD2)
}

private val UnityHubBaseTypography = Typography()
private val UnityHubTypography = UnityHubBaseTypography.copy(
    headlineLarge = UnityHubBaseTypography.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineMedium = UnityHubBaseTypography.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineSmall = UnityHubBaseTypography.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleLarge = UnityHubBaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = UnityHubBaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
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

    MaterialTheme(colorScheme = colorScheme, typography = UnityHubTypography, shapes = UnityHubShapes, content = content)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
