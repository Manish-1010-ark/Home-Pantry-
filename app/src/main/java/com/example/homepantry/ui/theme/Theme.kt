package com.example.homepantry.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = RosePink,
    onPrimary = Color.White,
    primaryContainer = RoseLight,
    onPrimaryContainer = DarkText,

    secondary = TealFresh,
    onSecondary = Color.White,
    secondaryContainer = TealLight,
    onSecondaryContainer = DarkText,

    tertiary = BerryAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF8BBD0),
    onTertiaryContainer = DarkText,

    background = BlushBackground,
    onBackground = DarkText,

    surface = PearlSurface,
    onSurface = DarkText,
    surfaceVariant = Color(0xFFFFF0F5),
    onSurfaceVariant = Color(0xFF5D4E54),

    outline = Color(0xFFE6D0D8),
    outlineVariant = Color(0xFFF5E8ED),

    error = Color(0xFFD32F2F),
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = RoseDark,
    onPrimary = DarkText,
    primaryContainer = Color(0xFF8B4F6B),
    onPrimaryContainer = RoseLight,

    secondary = TealDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF00695C),
    onSecondaryContainer = TealLight,

    tertiary = BerryDark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF7B3A54),
    onTertiaryContainer = Color(0xFFF8BBD0),

    background = DarkBackground,
    onBackground = LightText,

    surface = DarkSurface,
    onSurface = LightText,
    surfaceVariant = Color(0xFF3A3A3A),
    onSurfaceVariant = Color(0xFFCFCFCF),

    outline = Color(0xFF5A5A5A),
    outlineVariant = Color(0xFF404040),

    error = Color(0xFFEF5350),
    onError = Color.White,
)

@Composable
fun HomePantryTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}