package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CyanNeon,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF8CEEFF),
    secondary = AmberWarning,
    onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF634000),
    onSecondaryContainer = Color(0xFFFFDF9E),
    tertiary = EmeraldSuccess,
    onTertiary = Color(0xFF00391A),
    tertiaryContainer = Color(0xFF005327),
    onTertiaryContainer = Color(0xFF6CFFA0),
    error = CrimsonError,
    onError = Color.White,
    background = CityDarkNavy,
    onBackground = TextPrimaryDark,
    surface = CitySurfaceNavy,
    onSurface = TextPrimaryDark,
    surfaceVariant = CityCardNavy,
    onSurfaceVariant = TextSecondaryDark,
    outline = CityBorderNavy
)

private val LightColorScheme = lightColorScheme(
    primary = CyanNeonDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBCE9FF),
    onPrimaryContainer = Color(0xFF001F29),
    secondary = AmberWarningDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE082),
    onSecondaryContainer = Color(0xFF261900),
    tertiary = Color(0xFF00897B),
    onTertiary = Color.White,
    error = CrimsonError,
    onError = Color.White,
    background = CityLightBackground,
    onBackground = TextPrimaryLight,
    surface = CityLightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = CityLightCard,
    onSurfaceVariant = TextSecondaryLight,
    outline = CityLightBorder
)

@Composable
fun CityFlowTheme(
    darkTheme: Boolean = true, // Default to dark command center theme for logistics simulation
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
