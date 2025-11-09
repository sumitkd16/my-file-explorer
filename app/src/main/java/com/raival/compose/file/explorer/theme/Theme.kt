package com.raival.compose.file.explorer.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.screen.preferences.constant.ThemePreference

// ⚡ CYBERPUNK AMOLED THEME - Electric Blue & Vivid Purple
private val ElectricCyan = Color(0xFF00F5FF)          // Bright electric cyan
private val NeonBlue = Color(0xFF0099FF)              // Vivid neon blue
private val VividPurple = Color(0xFF8B5CF6)           // Vibrant purple (not pinkish)
private val DeepPurple = Color(0xFF6366F1)            // Rich deep purple
private val UltraViolet = Color(0xFF7C3AED)           // Ultra violet accent

// Dark backgrounds with subtle gradients
private val DeepSpace = Color(0xFF000000)             // Pure black for AMOLED
private val DarkGradient1 = Color(0xFF0A0A14)         // Dark blue-black
private val DarkGradient2 = Color(0xFF0D0D1F)         // Deeper blue-black
private val DarkGradient3 = Color(0xFF121225)         // Blue-tinted surface
private val RichDarkBlue = Color(0xFF0F1629)          // Dark blue surface
private val RichDarkPurple = Color(0xFF1A0F2E)        // Dark purple surface

// Bright text colors
private val PureWhite = Color(0xFFFFFFFF)
private val BrightText = Color(0xFFF0F4FF)            // Slightly blue-tinted white
private val CyanText = Color(0xFF88F0FF)              // Cyan-tinted text

// Stunning Cyberpunk Dark Color Scheme
private val CyberpunkDarkColorScheme = darkColorScheme(
    // Primary - Electric Cyan dominates
    primary = ElectricCyan,
    onPrimary = DeepSpace,
    primaryContainer = RichDarkBlue,
    onPrimaryContainer = CyanText,

    // Secondary - Vivid Purple accents
    secondary = VividPurple,
    onSecondary = PureWhite,
    secondaryContainer = RichDarkPurple,
    onSecondaryContainer = Color(0xFFD4BBFF),

    // Tertiary - Neon Blue highlights
    tertiary = NeonBlue,
    onTertiary = DeepSpace,
    tertiaryContainer = Color(0xFF001F3D),
    onTertiaryContainer = Color(0xFFB3E0FF),

    // Backgrounds - Pure black with gradient surfaces
    background = DeepSpace,                            // Pure AMOLED black
    onBackground = BrightText,

    // Surfaces - Dark gradients with blue/purple tints
    surface = DarkGradient2,
    onSurface = BrightText,
    surfaceVariant = DarkGradient3,
    onSurfaceVariant = Color(0xFFB8C5FF),

    surfaceTint = ElectricCyan,
    inverseSurface = Color(0xFFF0F4FF),
    inverseOnSurface = DarkGradient1,

    // Error - Bright but not pink
    error = Color(0xFFFF5252),
    onError = DeepSpace,
    errorContainer = Color(0xFF5C0000),
    onErrorContainer = Color(0xFFFFD6D6),

    // Outlines - Colorful borders
    outline = DeepPurple,
    outlineVariant = Color(0xFF2A2A5C),
    scrim = DeepSpace,

    // Surface containers - Progressive gradient depth
    surfaceBright = Color(0xFF1F1F3A),
    surfaceDim = DarkGradient1,
    surfaceContainer = DarkGradient2,
    surfaceContainerHigh = DarkGradient3,
    surfaceContainerHighest = Color(0xFF1A1A35),
    surfaceContainerLow = Color(0xFF050508),
    surfaceContainerLowest = DeepSpace,
)

private val LightColorScheme = lightColorScheme()

@Composable
fun FileExplorerTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val manager = globalClass.preferencesManager
    val darkTheme: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        if (manager.theme == ThemePreference.SYSTEM.ordinal) {
            isSystemInDarkTheme()
        } else manager.theme == ThemePreference.DARK.ordinal
    } else {
        manager.theme == ThemePreference.DARK.ordinal
    }

    fun getTheme(): ColorScheme {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (manager.theme) {
                ThemePreference.LIGHT.ordinal -> dynamicLightColorScheme(context)
                ThemePreference.DARK.ordinal -> CyberpunkDarkColorScheme
                else -> if (darkTheme) CyberpunkDarkColorScheme else dynamicLightColorScheme(
                    context
                )
            }
        } else {
            when (manager.theme) {
                ThemePreference.LIGHT.ordinal -> LightColorScheme
                ThemePreference.DARK.ordinal -> CyberpunkDarkColorScheme
                else -> if (darkTheme) CyberpunkDarkColorScheme else LightColorScheme
            }
        }
    }

    var colorScheme by remember {
        mutableStateOf(getTheme())
    }

    LaunchedEffect(manager.theme) {
        colorScheme = getTheme()
    }

    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let {
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !darkTheme
            }

        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}