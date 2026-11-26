package com.group7.agriserve.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AgriGreen80,
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = AgriGreenGrey80,
    onSecondary = Color(0xFF273516),
    secondaryContainer = Color(0xFF2B3A26),
    onSecondaryContainer = Color(0xFFDCE7CF),
    tertiary = AgriBrown80,
    onTertiary = Color(0xFF3E2D16),
    tertiaryContainer = Color(0xFF4E3B2B),
    onTertiaryContainer = Color(0xFFF3E5D8),
    background = Color(0xFF0F1410),
    onBackground = Color(0xFFE6ECE4),
    surface = Color(0xFF141A15),
    onSurface = Color(0xFFE6ECE4),
    surfaceVariant = Color(0xFF263026),
    onSurfaceVariant = Color(0xFFC2C9BD),
    surfaceContainerLowest = Color(0xFF0B0F0C),
    surfaceContainerLow = Color(0xFF182019),
    surfaceContainer = Color(0xFF1D261F),
    surfaceContainerHigh = Color(0xFF242F26),
    surfaceContainerHighest = Color(0xFF2C382E),
    outline = Color(0xFF6B7769),
    outlineVariant = Color(0xFF364236),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = AgriGreen40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0D3B12),
    secondary = AgriGreenGrey40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2ECD9),
    onSecondaryContainer = Color(0xFF223015),
    tertiary = AgriBrown40,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEFEBE9),
    onTertiaryContainer = Color(0xFF3E2723),
    background = Color(0xFFF8FAF6),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFDFDF8),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = AgriLightGreen,
    onSurfaceVariant = AgriGreenGrey40,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F7F1),
    surfaceContainer = Color(0xFFEEF2EA),
    surfaceContainerHigh = Color(0xFFE8EDE4),
    surfaceContainerHighest = Color(0xFFE2E8DD),
    outline = Color(0xFF72796F),
    outlineVariant = Color(0xFFC2C9BD),
    error = AgriRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
private fun animateColor(targetValue: Color): Color {
    val animated by animateColorAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 300),
        label = "ThemeColorTransition"
    )
    return animated
}

@Composable
private fun animatedColorScheme(target: ColorScheme): ColorScheme = target.copy(
    primary = animateColor(target.primary),
    onPrimary = animateColor(target.onPrimary),
    primaryContainer = animateColor(target.primaryContainer),
    onPrimaryContainer = animateColor(target.onPrimaryContainer),
    secondary = animateColor(target.secondary),
    onSecondary = animateColor(target.onSecondary),
    secondaryContainer = animateColor(target.secondaryContainer),
    onSecondaryContainer = animateColor(target.onSecondaryContainer),
    background = animateColor(target.background),
    onBackground = animateColor(target.onBackground),
    surface = animateColor(target.surface),
    onSurface = animateColor(target.onSurface),
    surfaceVariant = animateColor(target.surfaceVariant),
    onSurfaceVariant = animateColor(target.onSurfaceVariant),
    surfaceContainerLow = animateColor(target.surfaceContainerLow),
    surfaceContainer = animateColor(target.surfaceContainer),
    surfaceContainerHigh = animateColor(target.surfaceContainerHigh),
    outlineVariant = animateColor(target.outlineVariant)
)

@Composable
fun AgriServeTheme(
    themeMode: String = "system",
    fontScale: Float = 1.0f,
    darkTheme: Boolean = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    },
    // Dynamic color is disabled by default to maintain agricultural branding
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val animatedScheme = animatedColorScheme(colorScheme)
    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = currentDensity.density,
            fontScale = (currentDensity.fontScale * fontScale).coerceIn(0.8f, 1.6f)
        )
    ) {
        MaterialTheme(
            colorScheme = animatedScheme,
            typography = Typography,
            content = content
        )
    }
}
