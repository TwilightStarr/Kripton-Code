package com.kriptoncod.app.data.serial

/**
 * Seri mod ilerlemesinin tek hesap yeri.
 *
 * Eski gösterim "Prompt (index+1) / toplam" idi: süren prompt henüz bitmediği hâlde bitmiş gibi sayılıyor,
 * CI/düzeltme aşaması görünmüyor, tamamlanınca bile son prompta kadar %100 çıkmıyordu.
 * Yeni hesap: biten prompt sayısı + süren promptun aşaması (prompt → commit → CI → düzeltme) kesirli sayılır.
 */
data class SerialProgress(
    /** Tamamen biten prompt sayısı. */
    val completed: Int,
    val total: Int,
    /** Süren promptun sırası (1 tabanlı); bitince/boşken null. */
    val currentNumber: Int?,
    /** 0..1 */
    val fraction: Float,
) {
    val percent: Int get() = (fraction * 100f).toInt().coerceIn(0, 100)

    companion object {
        private const val PROMPT_AT = 0.10f
        private const val COMMIT_AT = 0.55f
        private const val CI_AT = 0.70f
        private const val FIX_AT = 0.80f

        /** Aşamaya göre süren promptun kesri; düzeltme turu arttıkça ilerler ama %95'i geçmez. */
        fun stepFraction(phase: SerialPhase, fixRound: Int): Float {
            val base = when (phase) {
                SerialPhase.PROMPT -> PROMPT_AT
                SerialPhase.COMMIT -> COMMIT_AT
                SerialPhase.CI -> CI_AT
                SerialPhase.FIX -> FIX_AT
            }
            // Her düzeltme turu, kalan boşluğun yarısını kapatır: hiçbir zaman 1'e varmaz (bitmedi).
            val gap = 0.95f - base
            var bonus = 0f
            var left = gap
            repeat(fixRound.coerceIn(0, 6)) {
                left /= 2f
                bonus += left
            }
            return (base + if (phase == SerialPhase.CI || phase == SerialPhase.FIX) bonus else 0f).coerceAtMost(0.95f)
        }

        fun of(s: SerialState): SerialProgress {
            val total = s.total
            if (total <= 0) return SerialProgress(0, 0, null, 0f)
            if (s.status == SerialStatus.DONE) return SerialProgress(total, total, null, 1f)
            val completed = s.index.coerceIn(0, total)
            if (completed >= total) return SerialProgress(total, total, null, if (s.status.isFinished) 1f else 0.99f)
            val frac = (completed + stepFraction(s.phase, s.fixRound)) / total
            return SerialProgress(completed, total, completed + 1, frac.coerceIn(0f, 0.99f))
        }
    }
}
