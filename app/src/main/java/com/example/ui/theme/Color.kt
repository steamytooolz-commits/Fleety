package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------------------------
// CityFlow "Night Ops" palette
//
// Ink -> surfaces -> hairlines give the console its depth; mint/iris are the two working accents
// (mint = money & positive action, iris = navigation & selection). Amber/coral are reserved for
// states that need the operator's attention, never for decoration.
// ---------------------------------------------------------------------------------------------

val Ink = Color(0xFF05080E)
val InkRaised = Color(0xFF0A1119)
val SurfaceBase = Color(0xFF0E1622)
val SurfaceRaised = Color(0xFF142032)
val SurfaceHigh = Color(0xFF1B2A40)

val Hairline = Color(0xFF223148)
val HairlineStrong = Color(0xFF31465F)

val Mint = Color(0xFF78F2C2)
val MintDeep = Color(0xFF2BD9A2)
val Iris = Color(0xFF8FA9FF)
val Amber = Color(0xFFFFC661)
val Coral = Color(0xFFFF7D7D)
val Sky = Color(0xFF6FD2FF)
val Violet = Color(0xFFC3A6FF)

val Paper = Color(0xFFEDF2FA)
val Slate = Color(0xFF9AACC4)
val Muted = Color(0xFF63748C)

// Category accents shared by contracts, ledger rows and zone markers.
val AccentRideshare = Color(0xFF6FD2FF)
val AccentFood = Color(0xFFFFC661)
val AccentParcel = Color(0xFF78F2C2)
val AccentCargo = Color(0xFFC3A6FF)

// --- Legacy aliases kept so screens can migrate incrementally -------------------------------
val CityDarkNavy = Ink
val CitySurfaceNavy = SurfaceBase
val CityCardNavy = SurfaceRaised
val CityBorderNavy = Hairline
val CyanNeon = Mint
val CyanNeonDark = MintDeep
val AmberWarning = Amber
val AmberWarningDark = Color(0xFFE0A23A)
val EmeraldSuccess = Mint
val CrimsonError = Coral
val PurpleAccent = Violet
val TextPrimaryDark = Paper
val TextSecondaryDark = Slate
val TextMutedDark = Muted

val CityLightBackground = Color(0xFFF4F7FC)
val CityLightSurface = Color(0xFFFFFFFF)
val CityLightCard = Color(0xFFEBF1F9)
val CityLightBorder = Color(0xFFD3DEEB)
val TextPrimaryLight = Color(0xFF0B1524)
val TextSecondaryLight = Color(0xFF4A5A70)
