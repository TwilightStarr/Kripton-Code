package com.kriptoncod.app.ui.desktop

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

/**
 * Masaüstü modunun uygulama genelindeki durumu ve eylemleri. Ayarlar ve yan menü bunu okur; böylece ekran
 * parametreleri çoğalmaz. [DesktopModeHost] sağlar.
 *
 *  - [available]: cihaz tablet mi (telefonda mod hiç sunulmaz).
 *  - [enabled]: mod açık mı (açıksa uygulama her açılışta bu modla başlar).
 */
@Immutable
class DesktopModeController(
    val available: Boolean,
    val enabled: Boolean,
    val requestEnable: () -> Unit,
    val requestExit: () -> Unit,
) {
    companion object {
        val Unavailable = DesktopModeController(available = false, enabled = false, requestEnable = {}, requestExit = {})
    }
}

val LocalDesktopMode = compositionLocalOf { DesktopModeController.Unavailable }

/** Masaüstü kabuğu dosya seçiciyi sahiplenir; içindeki ekranlar (ör. Kripton penceresindeki sohbet) kendi seçicisini kurmaz. */
val LocalFileChooserOwnedByShell = compositionLocalOf { false }
