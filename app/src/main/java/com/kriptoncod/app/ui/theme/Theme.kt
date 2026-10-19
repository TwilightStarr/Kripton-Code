package com.kriptoncod.app.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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

private fun ThemeSpec.toColorScheme() = (if (isDark) darkColorScheme() else lightColorScheme()).copy(
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
    onError = if (isDark) background else Color.White,
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
    scrim = Color.Black,
)

/** Tema değişince renkler 350 ms'de yumuşakça geçer (animasyonlar kapalıysa anında). */
@Composable
private fun ThemeSpec.animated(animate: Boolean): ThemeSpec {
    if (!animate) return this
    val spec = tween<Color>(durationMillis = 350)

    @Composable fun Color.a() = animateColorAsState(this, spec, label = "theme").value
    return ThemeSpec(
        isDark = isDark,
        background = background.a(),
        surface = surface.a(),
        raised = raised.a(),
        border = border.a(),
        text = text.a(),
        muted = muted.a(),
        accent = accent.a(),
        onAccent = onAccent.a(),
        success = success.a(),
        successFill = successFill.a(),
        danger = danger.a(),
        dangerFill = dangerFill.a(),
    )
}

private val LocalKriptonColors = staticCompositionLocalOf { Palette.Dark.toKriptonColors() }

object KriptonTheme {
    val colors: KriptonColors
        @Composable @ReadOnlyComposable
        get() = LocalKriptonColors.current
}

@Composable
fun KriptonCodTheme(
    preference: ThemePreference,
    /** Tema geçişi renk animasyonu; Ayarlar'daki "Animasyonlar" anahtarını ve sistem ayarını yansıtır. */
    animate: Boolean = true,
    content: @Composable () -> Unit,
) {
    val spec = preference.spec(isSystemInDarkTheme()).animated(animate)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                // Açık temada sistem çubuğu simgeleri koyu, koyu temada açık olmalı.
                controller.isAppearanceLightStatusBars = !spec.isDark
                controller.isAppearanceLightNavigationBars = !spec.isDark
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
