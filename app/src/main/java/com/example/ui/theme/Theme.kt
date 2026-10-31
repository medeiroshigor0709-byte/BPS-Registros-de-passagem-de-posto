package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = BpsGold,
    onPrimary = Color(0xFF1E1400),
    primaryContainer = Color(0xFF423100),
    onPrimaryContainer = BpsGoldLight,
    secondary = BpsSilver,
    onSecondary = Color(0xFF1E293B),
    secondaryContainer = Color(0xFF2A364F),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = WhatsAppGreen,
    onTertiary = Color.Black,
    background = BpsDarkBg,
    onBackground = Color(0xFFF1F5F9),
    surface = BpsDarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = BpsDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = BpsCardBorder,
    error = StatusError,
    onError = Color.White
  )

private val LightColorScheme =
  lightColorScheme(
    primary = BpsGoldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFF2C2),
    onPrimaryContainer = Color(0xFF382700),
    secondary = Color(0xFF334155),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = WhatsAppGreenDark,
    onTertiary = Color.White,
    background = BpsLightBg,
    onBackground = Color(0xFF0F172A),
    surface = BpsLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = BpsLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFFCBD5E1),
    error = StatusError,
    onError = Color.White
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Keep BPS brand colors consistent
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
