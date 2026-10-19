package com.omni.wifi.audit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val BlueAccent = Color(0xFF03B6F7)
val PurpleAccent = Color(0xFF5F6BF7)
val PinkAccent = Color(0xFFB14BF7)

val AuditGradient = Brush.linearGradient(
    colors = listOf(BlueAccent, PurpleAccent, PinkAccent)
)

private val DarkColorScheme = darkColorScheme(
    primary = BlueAccent,
    secondary = PurpleAccent,
    tertiary = PinkAccent,
    background = Color(0xFF0F0F12),
    surface = Color(0xFF1A1A1E),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun OmniWifiAuditTheme(
    darkTheme: Boolean = true, // Force dark theme as requested
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
