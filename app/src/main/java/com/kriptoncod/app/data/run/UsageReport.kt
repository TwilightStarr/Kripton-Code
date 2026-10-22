package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.db.UsageEvent
import com.kriptoncod.app.domain.UsageOutcome
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Kullanım panosunun saf hesabı: ham [UsageEvent]'lerden hesap başına istek/token/maliyet ve zaman dilimi
 * (saatlik ya da günlük) toplamları. Veritabanına, saate ve ağa dokunmaz; saat dilimi dışarıdan verilir.
 *
 * Token değeri üç türlüdür ve panoda karışmaz: gerçek (sağlayıcı bildirdi), tahmini (karakterden) ve bilinmeyen
 * (eski kayıt, başarısız istek). Maliyet yalnız hesaba fiyat girilmişse hesaplanır; fiyat uydurulmaz.
 */
object UsageReport {
    enum class Window(val days: Int) {
        DAY(1),
        WEEK(7),
        MONTH(30),
        ;

        val ms: Long get() = days * 24L * 60 * 60 * 1000
    }

    data class AccountRow(
        val accountId: Long,
        /** Silinmiş hesabın olayları da görünür; o zaman null. */
        val account: AiAccount?,
        val requests: Int,
        val successes: Int,
        val rateLimited: Int,
        val promptTokens: Long,
        val completionTokens: Long,
        /** Token değeri tahmin olan istek sayısı. */
        val estimatedRequests: Int,
        /** Token bilgisi hiç olmayan istek sayısı. */
        val unknownRequests: Int,
        /** Tahmini maliyet (USD); hesapta fiyat yoksa null. */
        val cost: Double?,
    ) {
        val totalTokens: Long get() = promptTokens + completionTokens
    }

    /** [startMs] ile başlayan dilimin (saat ya da gün) toplamları. */
    data class Bucket(val startMs: Long, val tokens: Long, val requests: Int)

    data class Report(
        val window: Window,
        val rows: List<AccountRow>,
        val buckets: List<Bucket>,
        /** Buckets saatlikse true, günlükse false. */
        val hourly: Boolean,
        val requests: Int,
        val promptTokens: Long,
        val completionTokens: Long,
        val estimatedRequests: Int,
        /** Fiyatı girilmiş hesapların toplam maliyeti; hiçbirinde fiyat yoksa null. */
        val cost: Double?,
        /** Token harcamış ama fiyatı olmayan hesap sayısı: toplam maliyet bunlar için eksiktir. */
        val unpricedAccounts: Int,
    ) {
        val totalTokens: Long get() = promptTokens + completionTokens
    }

    fun build(
        events: List<UsageEvent>,
        accounts: List<AiAccount>,
        window: Window,
        now: Long,
        zone: ZoneId,
    ): Report {
        val since = now - window.ms
        val inWindow = events.filter { it.at in since..now }
        val byAccount = inWindow.groupBy { it.accountId }
        val known = accounts.associateBy { it.id }

        val rows = (known.keys + byAccount.keys).map { id ->
            val evs = byAccount[id].orEmpty()
            val account = known[id]
            val prompt = evs.sumOf { (it.promptTokens ?: 0).toLong() }
            val completion = evs.sumOf { (it.completionTokens ?: 0).toLong() }
            AccountRow(
                accountId = id,
                account = account,
                requests = evs.size,
                successes = evs.count { it.outcome == UsageOutcome.SUCCESS },
                rateLimited = evs.count { it.outcome == UsageOutcome.RATE_LIMITED },
                promptTokens = prompt,
                completionTokens = completion,
                estimatedRequests = evs.count { it.tokensEstimated },
                unknownRequests = evs.count { it.promptTokens == null && it.completionTokens == null },
                cost = costOf(account, prompt, completion),
            )
        }.sortedWith(
            compareByDescending<AccountRow> { it.totalTokens }
                .thenByDescending { it.requests }
                .thenBy { it.account?.displayName.orEmpty() },
        )

        val hourly = window == Window.DAY
        return Report(
            window = window,
            rows = rows,
            buckets = buckets(inWindow, since, now, zone, hourly),
            hourly = hourly,
            requests = rows.sumOf { it.requests },
            promptTokens = rows.sumOf { it.promptTokens },
            completionTokens = rows.sumOf { it.completionTokens },
            estimatedRequests = rows.sumOf { it.estimatedRequests },
            cost = rows.mapNotNull { it.cost }.takeIf { it.isNotEmpty() }?.sum(),
            unpricedAccounts = rows.count { it.cost == null && it.totalTokens > 0 },
        )
    }

    /** Fiyatın yalnız biri girilmişse diğer yön 0 sayılır (ör. yalnız çıktı ücretli). */
    fun costOf(account: AiAccount?, promptTokens: Long, completionTokens: Long): Double? {
        val pin = account?.priceInPerM
        val pout = account?.priceOutPerM
        if (pin == null && pout == null) return null
        return (promptTokens * (pin ?: 0.0) + completionTokens * (pout ?: 0.0)) / 1_000_000.0
    }

    /** Pencere başından şimdiye boşluksuz dilimler (boş dilimler 0 ile dolar; grafik sürekli kalır). */
    private fun buckets(events: List<UsageEvent>, since: Long, now: Long, zone: ZoneId, hourly: Boolean): List<Bucket> {
        val unit = if (hourly) ChronoUnit.HOURS else ChronoUnit.DAYS
        fun floor(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).truncatedTo(unit)
        val sums = events.groupBy { floor(it.at).toInstant().toEpochMilli() }
        val out = ArrayList<Bucket>()
        var cursor = floor(since)
        val end = floor(now)
        while (!cursor.isAfter(end)) {
            val start = cursor.toInstant().toEpochMilli()
            val evs = sums[start].orEmpty()
            out += Bucket(start, evs.sumOf { (it.promptTokens ?: 0).toLong() + (it.completionTokens ?: 0) }, evs.size)
            cursor = cursor.plus(1, unit)
        }
        return out
    }
}

/** Panodaki sayıların kısa gösterimi. Yerel ayardan bağımsız, sabit nokta ayracı ("1.2K"). */
object UsageFormat {
    fun tokens(n: Long): String = when {
        n < 1_000 -> n.toString()
        n < 1_000_000 -> compact(n / 1_000.0, "K")
        n < 1_000_000_000 -> compact(n / 1_000_000.0, "M")
        else -> compact(n / 1_000_000_000.0, "B")
    }

    private fun compact(v: Double, suffix: String): String {
        val text = if (v < 10) String.format(java.util.Locale.ROOT, "%.1f", v) else String.format(java.util.Locale.ROOT, "%.0f", v)
        return text.removeSuffix(".0") + suffix
    }

    /** USD; küçük tutarlar sıfır görünmesin diye 1 sentin altında üç basamak. */
    fun cost(usd: Double): String = if (usd in 0.0..0.0099999) String.format(java.util.Locale.ROOT, "$%.3f", usd) else String.format(java.util.Locale.ROOT, "$%.2f", usd)
}
