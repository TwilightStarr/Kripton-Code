package com.kriptoncod.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kriptoncod.app.domain.ThemePreference

private fun ThemeSpec.toColorScheme() = darkColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = raised,
    onPrimaryContainer = text,
    secondary = success,
    onSecondary = background,
    secondaryContainer = raised,
    onSecondaryContainer = text,
    tertiary = accent,
    onTertiary = onAccent,
    tertiaryContainer = raised,
    onTertiaryContainer = text,
    error = danger,
    onError = background,
    errorContainer = dangerFill,
    onErrorContainer = text,
    background = background,
    onBackground = text,
    surface = background,
    onSurface = text,
    surfaceVariant = raised,
    onSurfaceVariant = muted,
    surfaceTint = accent,
    surfaceBright = raised,
    surfaceDim = background,
    surfaceContainerLowest = background,
    surfaceContainerLow = surface,
    surfaceContainer = surface,
    surfaceContainerHigh = raised,
    surfaceContainerHighest = raised,
    outline = border,
    outlineVariant = border,
    inverseSurface = text,
    inverseOnSurface = background,
    inversePrimary = accent,
    scrim = background,
)

private val LocalKriptonColors = staticCompositionLocalOf { Palette.Graphite.toKriptonColors() }

object KriptonTheme {
    val colors: KriptonColors
        @Composable @ReadOnlyComposable get() = LocalKriptonColors.current
}

@Composable
fun KriptonCodTheme(
    preference: ThemePreference,
    content: @Composable () -> Unit,
) {
    val spec = preference.spec()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                // Tüm temalar koyu: sistem çubuğu simgeleri açık renkli olmalı.
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }
    CompositionLocalProvider(LocalKriptonColors provides spec.toKriptonColors()) {
        MaterialTheme(
            colorScheme = spec.toColorScheme(),
            typography = KriptonTypography,
            content = content,
        )
    }
}
