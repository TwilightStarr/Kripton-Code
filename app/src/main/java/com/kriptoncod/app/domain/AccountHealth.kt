package com.kriptoncod.app.domain

/** Tek bir isteğin sonucu ve zamanı (hesap sağlığı hesabı için). */
data class HealthEvent(val at: Long, val outcome: UsageOutcome)

/**
 * Bir hesabın son 24 saatlik durumu. Veritabanı satırı değildir; [com.kriptoncod.app.data.repo.UsageRepository]
 * kullanım olaylarından her seçimde taze üretir, bu yüzden ayrıca saklanacak/eskiyecek durum yoktur.
 *
 * @property requestTimes kotaya sayılan isteklerin bitiş zamanları, eskiden yeniye.
 * @property recent en yeni sonuç BAŞTA olacak şekilde son sonuçlar (en çok [RECENT_WINDOW]).
 */
data class AccountHealth(
    val accountId: Long,
    val requestTimes: List<Long> = emptyList(),
    val recent: List<HealthEvent> = emptyList(),
) {
    companion object {
        const val RECENT_WINDOW = 10
    }
}
