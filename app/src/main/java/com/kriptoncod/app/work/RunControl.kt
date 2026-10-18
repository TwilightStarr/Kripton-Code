package com.kriptoncod.app.work

import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.domain.RunStatus

/**
 * Çalışmayı uygulamayı açmadan denetleyen eylemler (bildirim düğmeleri ve ana ekran widget'ı ortak kullanır).
 * Durum kuralları ekrandakiyle aynıdır: yalnız uygun durumdaki run'a işlem yapılır, aksi hâlde sessizce atlanır.
 */
object RunControl {
    enum class Action { PAUSE, RESUME, CANCEL }

    /** Duraklatılabilir: dağıtım sürüyor. */
    val PAUSABLE: Set<RunStatus> = setOf(RunStatus.PREPARING, RunStatus.RUNNING)

    /** İptal edilebilir: bitmemiş her durum (duraklatılmış dahil). */
    val CANCELLABLE: Set<RunStatus> = setOf(
        RunStatus.PREPARING,
        RunStatus.RUNNING,
        RunStatus.PUSHING,
        RunStatus.CI_WAITING,
        RunStatus.FIXING,
        RunStatus.PAUSED,
    )

    /** Sürüyor ya da duraklatılmış; widget ve bildirim bu run'ları gösterir. */
    val ACTIVE: Set<RunStatus> = CANCELLABLE

    fun isApplicable(action: Action, status: RunStatus?): Boolean = when (action) {
        Action.PAUSE -> status in PAUSABLE
        Action.RESUME -> status == RunStatus.PAUSED
        Action.CANCEL -> status in CANCELLABLE
    }

    /** @return işlem uygulandıysa true; run yok/durumu uygun değilse false (hiçbir şey değişmez). */
    suspend fun apply(container: AppContainer, action: Action, runId: Long): Boolean {
        val status = container.runRepository.getRun(runId)?.status
        if (!isApplicable(action, status)) return false
        when (action) {
            Action.PAUSE -> container.runScheduler.pause(runId)
            Action.RESUME -> container.runScheduler.unpause(runId)
            Action.CANCEL -> container.runScheduler.cancel(runId)
        }
        return true
    }
}
