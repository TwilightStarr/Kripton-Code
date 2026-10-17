package com.kriptoncod.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import com.kriptoncod.app.domain.RunStatus

/**
 * Animasyonlar açık mı? Ayarlar > Görünüm > "Animasyonlar" anahtarı ile sistemin "animasyonları kaldır"
 * seçeneğinin birleşimidir. Tüm hareketli bileşenler (logo, yükleme göstergesi, ekran geçişleri) bunu okur.
 */
val LocalAnimationsEnabled = compositionLocalOf { true }

/** Arka planda bir çalışma sürüyor mu? Logo bunu görünce "çalışıyor" animasyonunu oynatır. */
val LocalAppBusy = compositionLocalOf { false }

/** Kullanıcı açısından "iş yürüyor" sayılan çalışma durumları (duraklatılmış/bitmiş değil). */
fun RunStatus.isInProgress(): Boolean = when (this) {
    RunStatus.PREPARING, RunStatus.RUNNING, RunStatus.PUSHING, RunStatus.CI_WAITING, RunStatus.FIXING -> true
    RunStatus.PAUSED, RunStatus.DONE, RunStatus.FAILED, RunStatus.CANCELLED -> false
}
