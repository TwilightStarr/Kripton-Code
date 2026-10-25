package com.kriptoncod.app.work

import com.kriptoncod.app.data.run.DispatchOutcome
import com.kriptoncod.app.data.run.PromptDispatcher
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Uygulama açıkken run'ı UYGULAMANIN KENDİ süreci içinde yürütür.
 *
 * Neden: Dağıtım normalde yalnız WorkManager işçisiyle ([PromptDispatchWorker]) yürüyordu. İşçi hiç başlamazsa
 * (kısıt, üretici güç yönetimi, ön plan servisi reddi, sessiz çökme) run "Çalışıyor" görünür ama hiçbir düğüm
 * başlamaz ve sebebi görünmezdi. Bu yürütücü uygulama açıkken işin kesin ilerlemesini sağlar; WorkManager
 * işçisi arka plan sürekliliği (ön plan servisi, süreç ölünce yeniden başlatma) için aynen kalır.
 *
 * Aynı run'ı iki yürütücünün birlikte işlemesini [PromptDispatcher.process] içindeki koruma engeller; biri çalışırken
 * öbürü hiçbir şeye dokunmadan bekler.
 */
class InAppDispatcher(
    private val scope: CoroutineScope,
    /** Tembel: AppContainer döngüsel kurulum yapmasın diye dağıtıcı ilk kullanımda alınır. */
    private val dispatcher: () -> PromptDispatcher,
    /** Döngüyü düşüren beklenmedik hata (çökme günlüğüne yazmak için); kendi hatası yutulur. */
    private val onError: (runId: Long, error: Throwable) -> Unit = { _, _ -> },
) {
    private val loops = ConcurrentHashMap<Long, kotlinx.coroutines.Job>()

    /** [runId] için döngü yoksa başlatır; zaten çalışıyorsa dokunmaz. */
    @Synchronized
    fun ensure(runId: Long) {
        if (loops[runId]?.isActive == true) return
        loops[runId] = scope.launch { loop(runId) }
    }

    /** Duraklat/iptal: devam eden üretim ve döngü hemen bırakılır (yarım düğüm devamda baştan yapılır). */
    @Synchronized
    fun stop(runId: Long) {
        loops.remove(runId)?.cancel()
    }

    fun isRunning(runId: Long): Boolean = loops[runId]?.isActive == true

    private suspend fun loop(runId: Long) {
        var failures = 0
        while (true) {
            val outcome = try {
                dispatcher().process(runId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                runCatching { onError(runId, e) }
                failures++
                if (failures >= MAX_FAILURES) return
                delay(FAILURE_DELAY_MS)
                continue
            }
            failures = 0
            when (outcome) {
                is DispatchOutcome.Finished -> return
                is DispatchOutcome.Cancelled -> return
                is DispatchOutcome.Wait -> delay(outcome.delayMs.coerceIn(MIN_POLL_MS, MAX_POLL_MS))
            }
        }
    }

    private companion object {
        const val MIN_POLL_MS = 2_000L
        const val MAX_POLL_MS = 120_000L
        const val FAILURE_DELAY_MS = 30_000L
        const val MAX_FAILURES = 20
    }
}
