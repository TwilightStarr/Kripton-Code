package com.kriptoncod.app.data.serial

import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Seri çalışmaları uygulamanın kendi süreci içinde yürütür (run başına tek döngü). Arka plan sürekliliğini
 * [com.kriptoncod.app.work.SerialWorker] ön plan servisiyle sağlar; süreç ölürse worker/açılış kurtarması [ensure] ile sürdürür.
 */
class SerialExecutor(
    private val scope: CoroutineScope,
    private val store: SerialStore,
    private val runner: () -> SerialRunner,
    private val onError: (id: Long, error: Throwable) -> Unit = { _, _ -> },
) {
    private val jobs = ConcurrentHashMap<Long, Job>()

    @Synchronized
    fun ensure(id: Long) {
        if (jobs[id]?.isActive == true) return
        if (store.get(id)?.status != SerialStatus.RUNNING) return
        jobs[id] = scope.launch { loop(id) }
    }

    @Synchronized
    fun stop(id: Long) {
        jobs.remove(id)?.cancel()
    }

    fun isRunning(id: Long): Boolean = jobs[id]?.isActive == true

    fun pause(id: Long) {
        store.update(id) { if (it.status == SerialStatus.RUNNING) it.copy(status = SerialStatus.PAUSED, message = "Duraklatıldı.") else it }
        store.log(id, "Duraklatıldı")
        stop(id)
    }

    /** Duraklatılmış ya da müdahale bekleyen çalışmayı kaldığı yerden sürdürür. */
    fun resume(id: Long) {
        val s = store.get(id) ?: return
        if (s.status.isFinished) return
        store.update(id) { it.copy(status = SerialStatus.RUNNING, message = "") }
        store.log(id, "Devam ediliyor")
        ensure(id)
    }

    fun cancel(id: Long) {
        store.update(id) { it.copy(status = SerialStatus.CANCELLED, message = "İptal edildi.") }
        store.log(id, "İptal edildi")
        stop(id)
    }

    /** Açılışta: yarım kalan (RUNNING) çalışmaları yeniden başlatır. */
    fun resumeAll() {
        store.observeAll().value.filter { it.status == SerialStatus.RUNNING }.forEach { ensure(it.id) }
    }

    private suspend fun loop(id: Long) {
        var failures = 0
        while (store.get(id)?.status == SerialStatus.RUNNING) {
            try {
                runner().run(id)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                runCatching { onError(id, e) }
                failures++
                if (failures >= MAX_FAILURES) {
                    store.update(id) { it.copy(status = SerialStatus.WAITING_USER, message = "Beklenmeyen hata: ${e.message}") }
                    store.log(id, "Beklenmeyen hata: ${e.message}")
                    return
                }
                store.log(id, "Beklenmeyen hata (${e.message}); 30 sn sonra yeniden denenecek")
                delay(30_000L)
            }
        }
    }

    private companion object {
        const val MAX_FAILURES = 10
    }
}
