package com.group7.agriserve.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AgriGreen80,
    secondary = AgriGreenGrey80,
    tertiary = AgriBrown80,
    background = Color(0xFF1A1C18),
    surface = Color(0xFF1A1C18),
    onPrimary = Color(0xFF00390A),
    onSecondary = Color(0xFF273516),
    onTertiary = Color(0xFF3E2D16),
    surfaceVariant = Color(0xFF323B2E),
    onSurfaceVariant = Color(0xFFC2C9BD)
)

private val LightColorScheme = lightColorScheme(
    primary = AgriGreen40,
    secondary = AgriGreenGrey40,
    tertiary = AgriBrown40,
    background = Color(0xFFFDFDF6),
    surface = Color(0xFFFDFDF6),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    surfaceVariant = AgriLightGreen,
    onSurfaceVariant = AgriGreenGrey40
)

@Composable
fun AgriServeTheme(
    themeMode: String = "system",
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
