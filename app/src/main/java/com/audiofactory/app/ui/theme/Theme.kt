package com.audiofactory.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AudioFactoryDarkColorScheme = darkColorScheme(
    primary = StudioBlue,
    onPrimary = StudioTextPrimary,
    primaryContainer = StudioSurfaceVariant,
    onPrimaryContainer = StudioBlueLight,
    secondary = StudioPurple,
    onSecondary = StudioTextPrimary,
    tertiary = StudioGreen,
    background = StudioBackground,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = StudioTextSecondary,
    outline = StudioCardBorder,
    error = StudioRose,
    onError = StudioTextPrimary
)

@Composable
fun AudioFactoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Audio Factory is an audio engineering studio that uses the dark aesthetic by default
    val colorScheme = AudioFactoryDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = StudioBackground.toArgb()
                window.navigationBarColor = StudioBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
