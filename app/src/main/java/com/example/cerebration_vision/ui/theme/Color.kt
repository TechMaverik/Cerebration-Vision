package com.example.cerebration_vision.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Palette lifted from the Cerebration web templates (`:root` / `html.dark`). */
@Immutable
data class CerebrationColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val accent: Color,
    val onAccent: Color,
    val text: Color,
    val textSec: Color,
    val muted: Color,
    val btnBg: Color,
    val btnFg: Color,
    val success: Color,
    val successBg: Color,
    val successLine: Color,
    val danger: Color,
    val dangerBg: Color,
    val dangerLine: Color,
    val warning: Color,
    val warningBg: Color,
    val warningLine: Color,
    val info: Color,
)

val LightCerebrationColors = CerebrationColors(
    bg = Color(0xFFF5F5F3),
    surface = Color(0xFFFBFBFA),
    surface2 = Color(0xFFEDEDEA),
    border = Color(0xFFE3E3DE),
    accent = Color(0xFF0C0C0B),
    onAccent = Color(0xFFF5F5F3),
    text = Color(0xFF0C0C0B),
    textSec = Color(0xFF46443E),
    muted = Color(0xFF76736B),
    btnBg = Color(0xFF5C5A54),
    btnFg = Color(0xFFF5F5F3),
    success = Color(0xFF4A6B3F),
    successBg = Color(0xFFE9EDE4),
    successLine = Color(0xFFC3CDB8),
    danger = Color(0xFF9E3B2F),
    dangerBg = Color(0xFFF3E7E4),
    dangerLine = Color(0xFFD9B3AB),
    warning = Color(0xFF9A6B1F),
    warningBg = Color(0xFFF3ECDC),
    warningLine = Color(0xFFDCC89D),
    info = Color(0xFF2F6475),
)

val DarkCerebrationColors = CerebrationColors(
    bg = Color(0xFF0E0E0C),
    surface = Color(0xFF141412),
    surface2 = Color(0xFF1C1C19),
    border = Color(0xFF2A2A26),
    accent = Color(0xFFFFFFFF),
    onAccent = Color(0xFF0E0E0C),
    text = Color(0xFFFFFFFF),
    textSec = Color(0xFFD9D7D0),
    muted = Color(0xFFA39F95),
    btnBg = Color(0xFF3A3935),
    btnFg = Color(0xFFE6E4DD),
    success = Color(0xFF8FB37F),
    successBg = Color(0xFF1A221A),
    successLine = Color(0xFF33452F),
    danger = Color(0xFFD2695A),
    dangerBg = Color(0xFF2A1A17),
    dangerLine = Color(0xFF5A332C),
    warning = Color(0xFFD0A24A),
    warningBg = Color(0xFF2A2316),
    warningLine = Color(0xFF5A4A24),
    info = Color(0xFF7FB3C4),
)

val LocalCerebrationColors = staticCompositionLocalOf { LightCerebrationColors }
