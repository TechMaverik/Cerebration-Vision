package com.example.cerebration_vision.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private fun CerebrationColors.toScheme(dark: Boolean) =
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = btnBg,
        onPrimary = btnFg,
        secondary = accent,
        onSecondary = onAccent,
        tertiary = info,
        background = bg,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surface2,
        onSurfaceVariant = textSec,
        outline = border,
        outlineVariant = border,
        error = danger,
    )

/** Dark is the default, as in the Cerebration web app; the header toggle switches to light. */
@Composable
fun CerebrationVisionTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkCerebrationColors else LightCerebrationColors
    CompositionLocalProvider(LocalCerebrationColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toScheme(darkTheme),
            typography = Typography,
            content = content
        )
    }
}
