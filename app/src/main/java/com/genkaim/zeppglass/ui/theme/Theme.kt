package com.genkaim.zeppglass.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

// A flat, high-contrast palette: white / near-black surfaces, one green accent,
// no gradients and no shadows.
private val LightScheme = lightColorScheme(
    primary = Color(0xFF0E8A63),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4F5EE),
    onPrimaryContainer = Color(0xFF063A2A),
    secondary = Color(0xFF16181D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1F2F4),
    onSecondaryContainer = Color(0xFF16181D),
    tertiary = Color(0xFF3D5AFE),
    onTertiary = Color(0xFFFFFFFF),
    // Grey page with white cards: the flat "desk" look, cards read as paper.
    background = Color(0xFFF2F3F5),
    onBackground = Color(0xFF16181D),
    // Cards sit on the page as a clearly lighter/darker block, with no border.
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16181D),
    surfaceVariant = Color(0xFFE8EAEE),
    onSurfaceVariant = Color(0xFF5E6067),
    outline = Color(0xFFD3D6DC),
    outlineVariant = Color(0xFFE2E4E9),
    error = Color(0xFFC93C3C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE9E9),
    onErrorContainer = Color(0xFF5C1414)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF48D69B),
    onPrimary = Color(0xFF04231A),
    primaryContainer = Color(0xFF0D3327),
    onPrimaryContainer = Color(0xFFC8F5E3),
    secondary = Color(0xFFE8E9EC),
    onSecondary = Color(0xFF111214),
    secondaryContainer = Color(0xFF1A1B1E),
    onSecondaryContainer = Color(0xFFE8E9EC),
    tertiary = Color(0xFF8FA6FF),
    onTertiary = Color(0xFF091233),
    background = Color(0xFF07080A),
    onBackground = Color(0xFFECECEE),
    surface = Color(0xFF17181C),
    onSurface = Color(0xFFECECEE),
    surfaceVariant = Color(0xFF232529),
    onSurfaceVariant = Color(0xFFA0A2AA),
    outline = Color(0xFF35373D),
    outlineVariant = Color(0xFF272A2F),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF3F0D0D),
    errorContainer = Color(0xFF331416),
    onErrorContainer = Color(0xFFFFD9D9)
)

/** Flat design tokens that Material's colour scheme does not name directly. */
data class ZgPalette(
    val isDark: Boolean,
    val border: Color,
    val panel: Color,
    val panelMuted: Color,
    val accent: Color,
    val accentSoft: Color,
    val grid: Color,
    val sleepDeep: Color,
    val sleepLight: Color,
    val sleepRem: Color,
    val sleepAwake: Color,
    /**
     * Workouts on the day chart. Deliberately warm and clearly apart from the
     * sleep blue: one band is time that restored Charge, the other is time that
     * spent it, and the two must not read as the same thing.
     */
    val workout: Color
)

val LocalZgPalette = staticCompositionLocalOf {
    ZgPalette(
        isDark = false,
        border = Color(0xFFDCDCE0),
        panel = Color.White,
        panelMuted = Color(0xFFF6F6F7),
        accent = Color(0xFF0E8A63),
        accentSoft = Color(0xFFE4F5EE),
        grid = Color(0xFFEDEDF0),
        sleepDeep = Color(0xFF3B4C8A),
        sleepLight = Color(0xFF7C9BFF),
        sleepRem = Color(0xFF3FBFAE),
        sleepAwake = Color(0xFFD99A4E),
        workout = Color(0xFFE07A3F)
    )
}

private val DefaultTypography = Typography()

private fun Typography.codexStyle(): Typography {
    val tight = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)
    fun TextStyle.tuned(weight: FontWeight) = copy(
        fontFamily = FontFamily.Default,
        fontWeight = weight,
        lineHeightStyle = tight,
        letterSpacing = letterSpacing * 0.96f
    )
    return Typography(
        displayLarge = displayLarge.tuned(FontWeight.SemiBold),
        displayMedium = displayMedium.tuned(FontWeight.SemiBold),
        displaySmall = displaySmall.tuned(FontWeight.SemiBold),
        headlineLarge = headlineLarge.tuned(FontWeight.SemiBold),
        headlineMedium = headlineMedium.tuned(FontWeight.SemiBold),
        headlineSmall = headlineSmall.tuned(FontWeight.SemiBold),
        titleLarge = titleLarge.tuned(FontWeight.SemiBold),
        titleMedium = titleMedium.tuned(FontWeight.SemiBold),
        titleSmall = titleSmall.tuned(FontWeight.Medium),
        bodyLarge = bodyLarge.tuned(FontWeight.Normal),
        bodyMedium = bodyMedium.tuned(FontWeight.Normal),
        bodySmall = bodySmall.tuned(FontWeight.Normal),
        labelLarge = labelLarge.tuned(FontWeight.Medium),
        labelMedium = labelMedium.tuned(FontWeight.Medium),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
    )
}

@Composable
fun ZeppGlassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {    val scheme = if (darkTheme) DarkScheme else LightScheme
    val palette = ZgPalette(
        isDark = darkTheme,
        border = scheme.outline,
        panel = scheme.surface,
        panelMuted = scheme.surfaceVariant,
        accent = scheme.primary,
        accentSoft = scheme.primaryContainer,
        grid = scheme.outlineVariant,
        sleepDeep = if (darkTheme) Color(0xFF7E93E8) else Color(0xFF3B4C8A),
        sleepLight = if (darkTheme) Color(0xFF9DB3FF) else Color(0xFF6F8DF5),
        sleepRem = if (darkTheme) Color(0xFF5BD6C4) else Color(0xFF2FA895),
        sleepAwake = if (darkTheme) Color(0xFFE9B872) else Color(0xFFC98A3E),
        workout = if (darkTheme) Color(0xFFF2A06A) else Color(0xFFDD7A3C)
    )
    CompositionLocalProvider(LocalZgPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = DefaultTypography.codexStyle(),
            content = content
        )
    }
}
