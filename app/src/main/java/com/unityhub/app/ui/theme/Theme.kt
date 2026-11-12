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

internal object ColorTokens {
    val PrimaryContainerLight = androidx.compose.ui.graphics.Color(0xFFFFE1E5)
    val OnPrimaryContainerLight = androidx.compose.ui.graphics.Color(0xFF5E1020)
    val SecondaryContainerLight = androidx.compose.ui.graphics.Color(0xFFFFF0D9)
    val OnSecondaryContainerLight = androidx.compose.ui.graphics.Color(0xFF553400)
    val TertiaryContainerLight = androidx.compose.ui.graphics.Color(0xFFFFE5DE)
    val OnTertiaryContainerLight = androidx.compose.ui.graphics.Color(0xFF621E15)
    val SurfaceVariantLight = androidx.compose.ui.graphics.Color(0xFFF4E9E6)
    val OutlineLight = androidx.compose.ui.graphics.Color(0xFF74605A)
    val InverseSurfaceLight = androidx.compose.ui.graphics.Color(0xFF30211F)
    val InverseOnSurfaceLight = androidx.compose.ui.graphics.Color(0xFFF8EFEB)
    val InversePrimaryLight = androidx.compose.ui.graphics.Color(0xFFFF8798)
    val ErrorLight = androidx.compose.ui.graphics.Color(0xFFB3261E)
    val ErrorContainerLight = androidx.compose.ui.graphics.Color(0xFFF9DEDC)
    val OnErrorContainerLight = androidx.compose.ui.graphics.Color(0xFF410E0B)

    val PrimaryDark = androidx.compose.ui.graphics.Color(0xFFFF8798)
    val OnPrimaryDark = androidx.compose.ui.graphics.Color(0xFF350811)
    val PrimaryContainerDark = androidx.compose.ui.graphics.Color(0xFF5A1F2D)
    val OnPrimaryContainerDark = androidx.compose.ui.graphics.Color(0xFFFFD9DF)
    val SecondaryDark = androidx.compose.ui.graphics.Color(0xFFFFD083)
    val OnSecondaryDark = androidx.compose.ui.graphics.Color(0xFF402300)
    val SecondaryContainerDark = androidx.compose.ui.graphics.Color(0xFF4C3518)
    val OnSecondaryContainerDark = androidx.compose.ui.graphics.Color(0xFFFFE6B5)
    val TertiaryDark = androidx.compose.ui.graphics.Color(0xFFFF9B84)
    val OnTertiaryDark = androidx.compose.ui.graphics.Color(0xFF44120B)
    val TertiaryContainerDark = androidx.compose.ui.graphics.Color(0xFF5A231B)
    val OnTertiaryContainerDark = androidx.compose.ui.graphics.Color(0xFFFFDAD2)
    val SurfaceVariantDark = androidx.compose.ui.graphics.Color(0xFF302525)
    val OutlineDark = androidx.compose.ui.graphics.Color(0xFFBBA6A1)
    val InverseSurfaceDark = androidx.compose.ui.graphics.Color(0xFFF0E7E3)
    val InverseOnSurfaceDark = androidx.compose.ui.graphics.Color(0xFF30211F)
    val InversePrimaryDark = androidx.compose.ui.graphics.Color(0xFF74152B)
    val ErrorDark = androidx.compose.ui.graphics.Color(0xFFFFB4A7)
    val OnErrorDark = androidx.compose.ui.graphics.Color(0xFF680E0A)
    val ErrorContainerDark = androidx.compose.ui.graphics.Color(0xFF8D1A12)
    val OnErrorContainerDark = androidx.compose.ui.graphics.Color(0xFFFFDAD2)
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
