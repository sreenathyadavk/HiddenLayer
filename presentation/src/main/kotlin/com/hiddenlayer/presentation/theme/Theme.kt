package com.hiddenlayer.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Only Dark Theme for "Hacker" aesthetic
private val LightColorScheme = lightColorScheme(
    primary = SoftBlack,
    onPrimary = PureWhite,
    primaryContainer = LightGray,
    onPrimaryContainer = SoftBlack,
    secondary = SoftGray,
    onSecondary = PureWhite,
    tertiary = AccentBlue,
    background = OffWhite,
    surface = PureWhite,
    surfaceVariant = LightGray,
    onSurface = SoftBlack,
    onSurfaceVariant = SoftGray,
    error = SoftRed,
    errorContainer = Color(0xFFFFE5E5),
    onError = PureWhite
)

@Composable
fun HiddenLayerTheme(
    darkTheme: Boolean = false, // Always light
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Make status bar match light background
            window.statusBarColor = OffWhite.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
