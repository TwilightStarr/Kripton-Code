package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.ai.StreamSink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Üretimi süren düğümlerin canlı (akan) metni; yalnız bellekte tutulur, diske yazılmaz.
 *
 * Kalıcı çıktı hâlâ `RunRepository.completeJob` ile yazılır; burası yalnız ekranda "yazı yazı akma" içindir.
 * Bellek sınırlı kalsın diye düğüm başına yalnız SON [maxChars] karakter saklanır; yayın [minIntervalMs]'den
 * sık yapılmaz (her token'da yeniden çizim olmasın).
 */
class LiveOutput(
    private val maxChars: Int = DEFAULT_MAX_CHARS,
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Buffer(val text: StringBuilder = StringBuilder(), var lastPublishAt: Long = 0L)

    private val lock = Any()
    private val buffers = HashMap<Long, Buffer>()
    private val sinks = HashMap<Long, Sink>()
    private val state = MutableStateFlow<Map<Long, String>>(emptyMap())

    /** Düğümün canlı metni; akış yoksa null. Aynı değer art arda yayılmaz. */
    fun observe(jobId: Long): Flow<String?> = state.map { it[jobId] }.distinctUntilChanged()

    fun current(jobId: Long): String? = state.value[jobId]

    /** [jobId] için üreticiye verilecek alıcı. [clear] sonrası eski alıcıdan gelen geç parçalar yok sayılır. */
    fun sinkFor(jobId: Long): StreamSink = synchronized(lock) { Sink(jobId).also { sinks[jobId] = it } }

    /** Düğüm bitti/başarısız/iptal: canlı metin atılır (kalıcı çıktı depodan okunur). */
    fun clear(jobId: Long) {
        synchronized(lock) {
            sinks.remove(jobId)?.closed = true
            buffers.remove(jobId)
            if (jobId in state.value) state.value = state.value - jobId
        }
    }

    private inner class Sink(private val jobId: Long) : StreamSink {
        @Volatile var closed = false
        override fun append(delta: String) { if (!closed) this@LiveOutput.append(jobId, delta) }
        override fun reset() { if (!closed) this@LiveOutput.reset(jobId) }
    }

    private fun append(jobId: Long, delta: String) {
        if (delta.isEmpty()) return
        synchronized(lock) {
            if (sinks[jobId]?.closed != false) return // clear() sonrası gelen geç parça
            val b = buffers.getOrPut(jobId) { Buffer() }
            b.text.append(delta)
            // Üst sınırın iki katını aşınca baştan kırp (her eklemede kopyalamamak için).
            if (b.text.length > maxChars * 2) b.text.delete(0, b.text.length - maxChars)
            val t = now()
            if (jobId !in state.value || t - b.lastPublishAt >= minIntervalMs) publish(jobId, b, t)
        }
    }

    private fun reset(jobId: Long) {
        synchronized(lock) {
            buffers[jobId]?.text?.setLength(0)
            if (jobId in state.value) state.value = state.value + (jobId to "")
        }
    }

    private fun publish(jobId: Long, b: Buffer, t: Long) {
        b.lastPublishAt = t
        val shown = if (b.text.length > maxChars) b.text.substring(b.text.length - maxChars) else b.text.toString()
        state.value = state.value + (jobId to shown)
    }

    companion object {
        const val DEFAULT_MAX_CHARS = 100_000
        const val DEFAULT_MIN_INTERVAL_MS = 80L
    }
}
