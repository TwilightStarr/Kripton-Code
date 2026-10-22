package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.domain.AccountHealth
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.UsageOutcome

/**
 * Bir iş için hangi hesabın kullanılacağına karar veren saf (durumsuz) politika. Veritabanına, saate ya da
 * ağa dokunmaz: her şey parametre olarak gelir, bu yüzden birim testle tam doğrulanır.
 *
 * Bir hesap şu durumlarda o an HAVUZDA DEĞİLDİR (ne zaman döneceği hesaplanır, işi bekletmek için kullanılır):
 *  - sağlayıcı sınırı: durum WAITING ve bekleme bitmemiş,
 *  - günlük kota: kayan 24 saatteki istek sayısı [AiAccount.dailyLimit]'e ulaşmış,
 *  - devre kesici: art arda [BREAKER_STREAK] hesap/sağlayıcı arızası (TRANSIENT/UNAUTHORIZED) görülmüş;
 *    [BREAKER_COOLDOWN_MS] sonra hesap tek bir deneme için yeniden açılır.
 * ERROR durumundaki hesap hiçbir zaman seçilmez (kullanıcı ya da sağlık kontrolü düzeltene kadar).
 *
 * Havuzdaki hesaplar şu sırayla seçilir: birincil hesaplar yedeklerden önce; son [HEALTH_WINDOW_MS]'de sağlıklı
 * olanlar sağlıksızlardan önce;
 * güçlü model (powerRank) önce; eşit güçte en uzun süredir kullanılmayan (yük dengeleme); son olarak kimlik.
 */
object AccountSelector {
    const val DAY_MS = 24L * 60 * 60 * 1000

    /** Devre kesiciyi açan art arda arıza sayısı. */
    const val BREAKER_STREAK = 3

    /** Devre açıldıktan sonra hesabın dinlendiği süre. */
    const val BREAKER_COOLDOWN_MS = 10L * 60 * 1000

    /** Son sonuçlarda başarı oranı bunun altındaysa hesap "sağlıksız" sayılır ve sona itilir. */
    const val UNHEALTHY_BELOW = 0.5

    /**
     * Sağlık puanına yalnız bu kadar yakın sonuçlar girer. Kısa bir kesinti, güçlü bir hesabı bir gün boyunca
     * geri plana atmasın diye eski sonuçlar unutulur.
     */
    const val HEALTH_WINDOW_MS = 30L * 60 * 1000

    sealed interface Choice {
        data class Use(val account: AiAccount) : Choice
        data class WaitUntil(val untilMs: Long) : Choice
        data object None : Choice
    }

    /** Hesap şu an istek almaya uygun mu (yalnız durum alanına göre; kota/devre ayrıca bakılır). */
    fun isUsable(account: AiAccount, t: Long): Boolean = when (account.status) {
        AccountStatus.READY -> true
        AccountStatus.WAITING -> (account.waitUntil ?: 0L) <= t
        AccountStatus.ERROR -> false
    }

    /** Günlük kota dolduysa yeniden açılma zamanı; dolmadıysa (ya da sınır yoksa) null. */
    fun quotaResetAt(account: AiAccount, health: AccountHealth?, t: Long): Long? {
        val limit = account.dailyLimit?.takeIf { it > 0 } ?: return null
        val times = health?.requestTimes.orEmpty().filter { it > t - DAY_MS }.sorted()
        if (times.size < limit) return null
        // Sayaç, en eski (size - limit). isteğin 24 saati dolunca limitin altına iner.
        return times[times.size - limit] + DAY_MS
    }

    /** Devre kesici açıksa yeniden açılma zamanı; kapalıysa null. */
    fun breakerReopenAt(health: AccountHealth?, t: Long): Long? {
        val streak = health?.recent.orEmpty().take(BREAKER_STREAK)
        if (streak.size < BREAKER_STREAK) return null
        if (!streak.all { it.outcome == UsageOutcome.TRANSIENT || it.outcome == UsageOutcome.UNAUTHORIZED }) return null
        return (streak.first().at + BREAKER_COOLDOWN_MS).takeIf { it > t }
    }

    /**
     * Yalnız kota ve devre kesici engeli (sağlayıcı beklemesi hariç); MANUAL atamada kullanılır:
     * engel varsa iş atanan hesapta takılı kalmaz, havuza geçer.
     */
    fun poolBlockUntil(account: AiAccount, health: AccountHealth?, t: Long): Long? =
        listOfNotNull(quotaResetAt(account, health, t), breakerReopenAt(health, t)).maxOrNull()

    /** Hesap havuzda değilse ne zamana kadar; havuzdaysa null. ERROR hesaplar için çağrılmaz. */
    private fun blockedUntil(account: AiAccount, health: AccountHealth?, t: Long): Long? {
        val providerWait = if (account.status == AccountStatus.WAITING) account.waitUntil?.takeIf { it > t } else null
        return listOfNotNull(providerWait, quotaResetAt(account, health, t), breakerReopenAt(health, t)).maxOrNull()
    }

    /**
     * Son [HEALTH_WINDOW_MS] içindeki sonuçların başarı oranı (0..1). 429 hesabın bozukluğu değildir, sayılmaz.
     * Veri yoksa 1.0.
     */
    fun healthScore(health: AccountHealth?, t: Long): Double {
        val counted = health?.recent.orEmpty()
            .filter { it.outcome != UsageOutcome.RATE_LIMITED && it.at > t - HEALTH_WINDOW_MS }
        if (counted.isEmpty()) return 1.0
        return counted.count { it.outcome == UsageOutcome.SUCCESS }.toDouble() / counted.size
    }

    /**
     * @param enabled açık hesaplar (kapalılar önceden elenmiş olmalı).
     * @param tried bu iş için bu turda zaten denenmiş hesaplar.
     * @param preferredId varsa ve havuzdaysa öncelikli hesap (planlama sırasında güce göre seçilmiş düğüm hesabı).
     */
    fun choose(
        enabled: List<AiAccount>,
        health: Map<Long, AccountHealth>,
        tried: Set<Long>,
        preferredId: Long?,
        t: Long,
    ): Choice {
        val candidates = enabled.filter { it.status != AccountStatus.ERROR }
        val blocked = candidates.associate { it.id to blockedUntil(it, health[it.id], t) }
        val usable = candidates.filter { it.id !in tried && blocked[it.id] == null }

        if (usable.isNotEmpty()) {
            usable.firstOrNull { it.id == preferredId }?.let { return Choice.Use(it) }
            return Choice.Use(usable.sortedWith(ordering(health, t)).first())
        }
        val earliest = blocked.values.filterNotNull().minOrNull()
        return if (earliest != null) Choice.WaitUntil(earliest) else Choice.None
    }

    /** Havuzdaki hesapların seçilme sırası; [PoolStatus] da aynı sıralamayı gösterir. */
    internal fun ordering(health: Map<Long, AccountHealth>, t: Long): Comparator<AiAccount> =
        compareBy<AiAccount> { it.isBackup }
            .thenBy { healthScore(health[it.id], t) < UNHEALTHY_BELOW }
            .thenByDescending { it.powerRank }
            .thenBy { health[it.id]?.recent?.firstOrNull()?.at ?: Long.MIN_VALUE }
            .thenBy { it.id }
}
