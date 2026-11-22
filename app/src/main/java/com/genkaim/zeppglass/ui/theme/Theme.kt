package com.genkaim.zeppglass.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    /** Hybrid Charge 精力: blue, fixed whatever the reading is. */
    val chargeMental: Color,
    /** Hybrid Charge 体力: a lighter blue, so the two sides read as one family. */
    val chargePhysical: Color,
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
    val workout: Color,
    /** 不良 / 偏高: the middle warning step, warmer than the accent, softer than red. */
    val warn: Color,
    /** 太低 / 严重: the one colour that means "this reading needs attention". */
    val critical: Color
)

val LocalZgPalette = staticCompositionLocalOf {
    ZgPalette(
        isDark = false,
        border = Color(0xFFDCDCE0),
        panel = Color.White,
        panelMuted = Color(0xFFF6F6F7),
        accent = Color(0xFF0E8A63),
        accentSoft = Color(0xFFE4F5EE),
        chargeMental = Color(0xFF3D5AFE),
        chargePhysical = Color(0xFF7C9BFF),
        grid = Color(0xFFEDEDF0),
        sleepDeep = Color(0xFF3B4C8A),
        sleepLight = Color(0xFF7C9BFF),
        sleepRem = Color(0xFF3FBFAE),
        sleepAwake = Color(0xFFD99A4E),
        workout = Color(0xFFE07A3F),
        warn = Color(0xFFE08A2B),
        critical = Color(0xFFC93C3C)
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
        chargeMental = if (darkTheme) Color(0xFF8FA6FF) else Color(0xFF3D5AFE),
        chargePhysical = if (darkTheme) Color(0xFFB8C8FF) else Color(0xFF7C9BFF),
        grid = scheme.outlineVariant,
        sleepDeep = if (darkTheme) Color(0xFF7E93E8) else Color(0xFF3B4C8A),
        sleepLight = if (darkTheme) Color(0xFF9DB3FF) else Color(0xFF6F8DF5),
        sleepRem = if (darkTheme) Color(0xFF5BD6C4) else Color(0xFF2FA895),
        sleepAwake = if (darkTheme) Color(0xFFE9B872) else Color(0xFFC98A3E),
        workout = if (darkTheme) Color(0xFFF2A06A) else Color(0xFFDD7A3C),
        warn = if (darkTheme) Color(0xFFF0A94E) else Color(0xFFE08A2B),
        critical = if (darkTheme) Color(0xFFFF8A8A) else Color(0xFFC93C3C)
    )
    CompositionLocalProvider(LocalZgPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = DefaultTypography.codexStyle(),
        ) {
            Box(Modifier.fillMaxSize()) {
                content()
                ThemeChangeFade(darkTheme)
            }
        }
    }
}

/**
 * A theme switch redraws every surface in one frame, which reads as a hard
 * cut. This lays the *previous* page colour over the new one for a moment and
 * fades it away, so the switch looks like a dip through the background instead
 * of a flash. It never takes touches.
 */
@Composable
private fun ThemeChangeFade(dark: Boolean) {
    var lastDark by remember { mutableStateOf(dark) }
    var fadeColor by remember { mutableStateOf(Color.Transparent) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(dark) {
        if (lastDark != dark) {
            fadeColor = if (lastDark) Color(0xFF07080A) else Color(0xFFF2F3F5)
            lastDark = dark
            alpha.snapTo(1f)
            alpha.animateTo(0f, animationSpec = tween(320))
        }
    }
    if (alpha.value > 0f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(fadeColor.copy(alpha = alpha.value * 0.9f))
        )
    }
}
