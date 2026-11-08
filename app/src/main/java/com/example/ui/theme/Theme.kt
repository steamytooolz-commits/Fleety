package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF032A1D),
    primaryContainer = Color(0xFF10402F),
    onPrimaryContainer = Mint,
    secondary = Iris,
    onSecondary = Color(0xFF0B1740),
    secondaryContainer = Color(0xFF1B2A55),
    onSecondaryContainer = Color(0xFFD9E2FF),
    tertiary = Sky,
    onTertiary = Color(0xFF00220E),
    tertiaryContainer = Color(0xFF123B4A),
    onTertiaryContainer = Color(0xFFBDF0FF),
    error = Coral,
    onError = Color(0xFF3A0A0A),
    errorContainer = Color(0xFF4A1518),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Ink,
    onBackground = Paper,
    surface = SurfaceBase,
    onSurface = Paper,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = Slate,
    surfaceTint = Mint,
    outline = HairlineStrong,
    outlineVariant = Hairline,
    inverseSurface = Paper,
    inverseOnSurface = Ink,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0F8F63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F5DC),
    onPrimaryContainer = Color(0xFF00281A),
    secondary = Color(0xFF3D5BC4),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE3FF),
    onSecondaryContainer = Color(0xFF001453),
    tertiary = Color(0xFF00748F),
    onTertiary = Color.White,
    error = Color(0xFFC0392B),
    onError = Color.White,
    background = CityLightBackground,
    onBackground = TextPrimaryLight,
    surface = CityLightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = CityLightCard,
    onSurfaceVariant = TextSecondaryLight,
    outline = CityLightBorder,
    outlineVariant = Color(0xFFE2EAF3),
)

/** The console backdrop: a soft cool gradient instead of a flat fill. */
val CityBackgroundBrush = Brush.verticalGradient(
    0f to Color(0xFF0B1524),
    0.45f to Ink,
    1f to Color(0xFF04070C),
)

@Composable
fun CityFlowTheme(
    darkTheme: Boolean = true, // The console is designed dark-first: fleet ops at night.
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
        shapes = CityShapes,
        content = content,
    )
}

/** Semantic color per contract type, used by cards, chips and the ledger. */
@Composable
fun contractColor(hex: Long): Color = Color(hex)
