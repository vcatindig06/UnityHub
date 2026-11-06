package com.unityhub.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun lightThemeTextPairsMeetWcagAaNormalTextContrast() {
        val pairs = listOf(
            "primary button" to (SurfaceWhite to UnityBlue),
            "secondary button" to (SurfaceWhite to UnityEmerald),
            "tertiary button" to (SurfaceWhite to UnityCrimson),
            "main text" to (InkPrimary to CanvasLight),
            "secondary text" to (InkSecondary to ColorTokens.SurfaceVariantLight),
            "primary container" to (ColorTokens.OnPrimaryContainerLight to ColorTokens.PrimaryContainerLight),
            "secondary container" to (ColorTokens.OnSecondaryContainerLight to ColorTokens.SecondaryContainerLight),
            "tertiary container" to (ColorTokens.OnTertiaryContainerLight to ColorTokens.TertiaryContainerLight),
            "error container" to (ColorTokens.OnErrorContainerLight to ColorTokens.ErrorContainerLight)
        )

        pairs.forEach { (name, colors) ->
            assertContrastAtLeastAa(name, colors.first, colors.second)
        }
    }

    @Test
    fun darkThemeTextPairsMeetWcagAaNormalTextContrast() {
        val pairs = listOf(
            "main text" to (InkPrimaryDark to CanvasDark),
            "secondary text" to (InkSecondaryDark to ColorTokens.SurfaceVariantDark),
            "primary action" to (ColorTokens.OnPrimaryDark to ColorTokens.PrimaryDark),
            "secondary action" to (ColorTokens.OnSecondaryDark to ColorTokens.SecondaryDark),
            "tertiary action" to (ColorTokens.OnTertiaryDark to ColorTokens.TertiaryDark),
            "primary container" to (ColorTokens.OnPrimaryContainerDark to ColorTokens.PrimaryContainerDark),
            "secondary container" to (ColorTokens.OnSecondaryContainerDark to ColorTokens.SecondaryContainerDark),
            "tertiary container" to (ColorTokens.OnTertiaryContainerDark to ColorTokens.TertiaryContainerDark),
            "error action" to (ColorTokens.OnErrorDark to ColorTokens.ErrorDark),
            "error container" to (ColorTokens.OnErrorContainerDark to ColorTokens.ErrorContainerDark)
        )

        pairs.forEach { (name, colors) ->
            assertContrastAtLeastAa(name, colors.first, colors.second)
        }
    }

    private fun assertContrastAtLeastAa(name: String, foreground: Color, background: Color) {
        val contrast = contrastRatio(foreground, background)
        assertTrue("$name contrast was $contrast:1; expected at least 4.5:1", contrast >= 4.5)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun linearize(channel: Float): Double {
            val value = channel.toDouble()
            return if (value <= 0.04045) value / 12.92
            else Math.pow((value + 0.055) / 1.055, 2.4)
        }

        return 0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)
    }
}
