package com.kriptoncod.app.data.run

import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus

/**
 * Tüm düğümler bitince run'ın nihai durumuna karar verir.
 *
 *  - Paket üretilemediyse -> FAILED.
 *  - Nihai birleştirme bitmiş ama herhangi bir düğüm FAILED ise -> **PARTIAL**: sonuç eksik olabilir, GitHub'a
 *    ancak kullanıcı onaylarsa ([approvePush]) gönderilir. Yeşil "tamamlandı" gösterilmez.
 *  - Hiç başarısız düğüm yoksa -> repo seçilmişse (testler eksikse üretilip) GitHub'a gönderilir ve CI izlenir; seçilmemişse DONE.
 *
 * Ağ/AI işleri işlev parametreleridir; böylece mantık Android olmadan sınanır.
 */
class RunCompleter(
    private val runs: Store,
    /** Zip'i üretir (nihai birleştirme/paketleme). */
    private val packageRun: suspend (runId: Long) -> Boolean,
    /** Push'tan önce: projede test yoksa AI'a yazdırır; hata verirse yutulmalıdır (best effort). */
    private val ensureTests: suspend (runId: Long) -> Unit,
    private val shouldPublish: suspend (runId: Long) -> Boolean,
    private val publish: suspend (runId: Long) -> Boolean,
    private val startCiLoop: (runId: Long) -> Unit,
) {

    /** Run durumu erişimi; [com.kriptoncod.app.data.repo.RunRepository] bunu AppContainer'da karşılar. */
    interface Store {
        suspend fun jobStates(runId: Long): List<JobState>
        suspend fun status(runId: Long): RunStatus?
        suspend fun setStatus(runId: Long, status: RunStatus)
        suspend fun saveHistory(runId: Long)
    }

    /** [PromptDispatcher] için `onAllJobsDone`. */
    suspend fun complete(runId: Long): RunStatus {
        if (!packageRun(runId)) return RunStatus.FAILED
        if (runs.jobStates(runId).any { it == JobState.FAILED }) return RunStatus.PARTIAL
        return publishOrFinish(runId, failStatus = RunStatus.FAILED)
    }

    /**
     * Kullanıcı kısmi sonucu GitHub'a göndermeyi onayladı. Yalnız PARTIAL run'da ve repo seçiliyse çalışır.
     * Gönderim başarısızsa run PARTIAL'da kalır (tekrar denenebilir).
     * @return yeni durum; run uygun değilse null (hiçbir şey değişmez).
     */
    suspend fun approvePush(runId: Long): RunStatus? {
        if (runs.status(runId) != RunStatus.PARTIAL) return null
        if (!shouldPublish(runId)) return null
        val status = publishOrFinish(runId, failStatus = RunStatus.PARTIAL)
        runs.setStatus(runId, status)
        runs.saveHistory(runId)
        return status
    }

    private suspend fun publishOrFinish(runId: Long, failStatus: RunStatus): RunStatus {
        if (!shouldPublish(runId)) return RunStatus.DONE
        runCatching { ensureTests(runId) }
        return if (publish(runId)) {
            startCiLoop(runId)
            RunStatus.CI_WAITING
        } else {
            failStatus
        }
    }
}
