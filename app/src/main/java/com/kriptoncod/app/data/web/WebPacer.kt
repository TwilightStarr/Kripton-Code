package com.kriptoncod.app.data.web

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Aynı hesaba art arda gönderimler arasına en az [minGapMs] (+ rastgele pay) koyar; insan benzeri
 * ritim ve sağlayıcı sınırlarına çarpmamak için. Her hesap için ayrı kilit tutulur.
 */
class WebPacer(
    private val minGapMs: Long = 20_000L,
    private val jitterMs: Long = 8_000L,
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val random: () -> Long = { (Math.random() * 1000).toLong() },
) {
    private val last = HashMap<Long, Long>()
    private val guard = Mutex()

    /** Hesap için gerekli beklemeyi uygular ve gönderim zamanını kaydeder. */
    suspend fun await(accountId: Long) {
        val wait = guard.withLock {
            val prev = last[accountId]
            val jitter = if (jitterMs > 0) random() % (jitterMs + 1) else 0L
            val w = if (prev == null) 0L else (prev + minGapMs + jitter - now()).coerceAtLeast(0L)
            last[accountId] = now() + w
            w
        }
        if (wait > 0) sleep(wait)
    }
}
