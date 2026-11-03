package com.omni.wifi.audit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Exact palette from HTML prototype
val AppBg = Color(0xFF070812)
val AppBgRadialTop = Color(0xFF20134B)
val AppBgMid = Color(0xFF090A18)
val AppBgBottom = Color(0xFF05060D)

val PanelBg = Color(0xFF17192F)
val CardBgStart = Color(0xFF1A1B36)
val CardBgEnd = Color(0xFF15172B)
val CardBgAlt = Color(0xFF191A30)
val CardBorderColor = Color(0xFF383363)
val CardBorderActive = Color(0xFFA65AFF)
val LineColor = Color(0xFF3B3472)

val PurplePrimary = Color(0xFF793CFF)
val PurpleLight = Color(0xFFA65AFF)
val CyanAccent = Color(0xFF21CAFF)
val BlueAccent = Color(0xFF168CFF)
val NeonGreen = Color(0xFF28E99A)
val WarningOrange = Color(0xFFFFA94D)
val ErrorRed = Color(0xFFFF5252)

val TextPrimary = Color(0xFFF7F5FF)
val TextMuted = Color(0xFFAAAAC9)
val TextSubtle = Color(0xFFB0B1CE)

val SelectedCardStart = Color(0xFF29204F)
val SelectedCardEnd = Color(0xFF241B48)

// Gradients
val AuditGradient = Brush.linearGradient(
    colors = listOf(CyanAccent, PurplePrimary, PurpleLight)
)

val PrimaryButtonGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF6537F4), Color(0xFF843CFF))
)

val HeroCardGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF251D59), Color(0xFF151B43))
)

val BackgroundBrush = Brush.verticalGradient(
    0.0f to AppBgRadialTop,
    0.35f to AppBgMid,
    1.0f to AppBgBottom
)

val CardBrush = Brush.linearGradient(
    colors = listOf(CardBgStart, CardBgEnd)
)

val SelectedCardBrush = Brush.linearGradient(
    colors = listOf(SelectedCardStart, SelectedCardEnd)
)

val SignalBarBrush = Brush.verticalGradient(
    colors = listOf(PurpleLight, NeonGreen)
)

private val DarkColorScheme = darkColorScheme(
    primary = PurplePrimary,
    secondary = CyanAccent,
    tertiary = PurpleLight,
    background = AppBg,
    surface = CardBgStart,
    onPrimary = TextPrimary,
    onSecondary = Color(0xFF06291B),
    onTertiary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun OmniWifiAuditTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
