package com.kriptoncod.app.ui.theme

import com.kriptoncod.app.data.settings.DesktopPrefs
import kotlin.math.roundToInt

/**
 * Hareket süresi hesabı (saf mantık; birim testle doğrulanır).
 *
 * @param baseMs tasarım süresi (bkz. [Motion] sabitleri)
 * @param enabled animasyonlar açık mı (kapalıysa 0: anında)
 * @param speed kullanıcının "geçiş hızı" çarpanı (1 = normal, 2 = iki kat hızlı); geçerli aralığa çekilir
 * @param systemScale sistemin Animatör süre ölçeği (1 = normal, 0.5 = iki kat hızlı, 0 = kapalı, 2 = iki kat yavaş)
 * @return milisaniye; 0 = animasyon yok (anında geç). Açıkken en az 1 ms.
 */
fun motionDurationMs(baseMs: Int, enabled: Boolean, speed: Float, systemScale: Float): Int {
    if (!enabled || baseMs <= 0) return 0
    val sys = if (systemScale.isNaN() || systemScale.isInfinite()) 1f else systemScale.coerceIn(0f, 10f)
    if (sys <= 0f) return 0
    val v = DesktopPrefs.cleanSpeed(speed)
    return (baseMs / v * sys).roundToInt().coerceAtLeast(1)
}
