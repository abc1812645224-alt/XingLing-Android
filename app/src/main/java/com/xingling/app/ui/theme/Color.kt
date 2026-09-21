/*
 * 星灵 (XingLing) - UI 语义色（iOS 风格）
 */

package com.xingling.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── iOS 27 Color Palette (Light) ──
private val LightBlue = Color(0xFF007AFF)
private val LightGreen = Color(0xFF34C759)
private val LightRed = Color(0xFFFF3B30)
private val LightOrange = Color(0xFFFF9500)

private val LightLabel = Color(0xFF000000)
private val LightSecondaryLabel = Color(0xFF3C3C43).copy(alpha = 0.6f)
private val LightNavUnselected = Color(0xFF555557)

private val LightBackground = Color(0xFFF2F4F7)
private val LightCardBackground = Color(0xFFFFFFFF)
private val LightSeparator = Color(0xFFE5E5EA)
private val LightFill = Color(0xFFF2F4F7)
private val LightGroupHeader = Color(0xFF8E8E93)

private val LightGlassWhite = Color.White
private val LightGlassBorder = Color(0xFFEDEEF0)

// ── iOS 27 Color Palette (Dark) ──
private val DarkBlue = Color(0xFF0A84FF)
private val DarkGreen = Color(0xFF30D158)
private val DarkRed = Color(0xFFFF453A)
private val DarkOrange = Color(0xFFFF9F0A)

private val DarkLabel = Color.White
private val DarkSecondaryLabel = Color(0xFFEBEBF5).copy(alpha = 0.6f)
private val DarkNavUnselected = Color(0xFF98989D)

private val DarkBackground = Color(0xFF000000)
private val DarkCardBackground = Color(0xFF1C1C1E)
private val DarkSeparator = Color(0xFF38383A)
private val DarkFill = Color(0xFF2C2C2E)

private val DarkGlassWhite = Color(0xFF1C1C1E)
private val DarkGlassBorder = Color(0xFF38383A)

// ── Composable color getters (automatically follow system dark mode) ──
val iOSBlue: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkBlue else LightBlue
val iOSGreen: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkGreen else LightGreen
val iOSRed: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkRed else LightRed
val iOSOrange: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkOrange else LightOrange

val iOSLabel: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkLabel else LightLabel
val iOSSecondaryLabel: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkSecondaryLabel else LightSecondaryLabel

val iOSNavUnselected: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkNavUnselected else LightNavUnselected

val iOSBackground: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkBackground else LightBackground
val iOSCardBackground: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkCardBackground else LightCardBackground
val iOSSeparator: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkSeparator else LightSeparator
val iOSFill: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkFill else LightFill

val iOSGlassWhite: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkGlassWhite else LightGlassWhite
val iOSGlassBorder: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkGlassBorder else LightGlassBorder

val iOSGroupHeader: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF98989D) else LightGroupHeader
