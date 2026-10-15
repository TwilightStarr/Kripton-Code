package com.kriptoncod.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kriptoncod.app.domain.ThemePreference

private val LightScheme = lightColorScheme(
    primary = Palette.LightNode,
    onPrimary = Color.White,
    primaryContainer = Palette.LightNodeFill,
    onPrimaryContainer = Palette.LightText,
    secondary = Palette.LightArrow,
    onSecondary = Color.White,
    error = Palette.LightDanger,
    background = Palette.LightBackground,
    onBackground = Palette.LightText,
    surface = Palette.LightSurface,
    onSurface = Palette.LightText,
    surfaceVariant = Palette.LightNodeFill,
    onSurfaceVariant = Palette.LightMuted,
    outline = Palette.LightNodeBorder,
    outlineVariant = Palette.LightNodeBorder,
)

private val DarkScheme = darkColorScheme(
    primary = Palette.DarkNode,
    onPrimary = Palette.DarkBackground,
    primaryContainer = Palette.DarkNodeFill,
    onPrimaryContainer = Palette.DarkText,
    secondary = Palette.DarkArrow,
    onSecondary = Palette.DarkBackground,
    error = Palette.DarkDanger,
    background = Palette.DarkBackground,
    onBackground = Palette.DarkText,
    surface = Palette.DarkSurface,
    onSurface = Palette.DarkText,
    surfaceVariant = Palette.DarkNodeFill,
    onSurfaceVariant = Palette.DarkMuted,
    outline = Palette.DarkNodeBorder,
    outlineVariant = Palette.DarkNodeBorder,
)

private val LocalKriptonColors = staticCompositionLocalOf { LightKriptonColors }

object KriptonTheme {
    val colors: KriptonColors
        @Composable @ReadOnlyComposable get() = LocalKriptonColors.current
}

@Composable
fun KriptonCodTheme(
    preference: ThemePreference,
    content: @Composable () -> Unit,
) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalKriptonColors provides if (dark) DarkKriptonColors else LightKriptonColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = KriptonTypography,
            content = content,
        )
    }
}
