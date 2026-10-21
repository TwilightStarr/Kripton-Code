package com.kriptoncod.app.work

import com.kriptoncod.app.domain.RunStatus

/**
 * Bekçi: yarım kalmış (RUNNING/PUSHING/CI_WAITING...) ama arkasında canlı WorkManager zinciri olmayan run'ları bulur
 * ve zinciri yeniden kurar. Zincir şu durumlarda kopabilir: ön plan servisi süre dolumu (Android 15 dataSync
 * ~6 saat/gün), sistemin işi öldürmesi, bir istisna. Kurtarma eskiden yalnız uygulama açılışında çalışıyordu.
 *
 * Not: kullanıcı uygulamayı "Zorla durdur" ederse Android tüm zamanlanmış işleri siler ve uygulama bir kez
 * elle açılana dek hiçbir şey çalışamaz; bu bir platform sınırıdır.
 */
object RunWatchdog {

    data class RunView(val id: Long, val status: RunStatus)

    sealed interface Action {
        val runId: Long
        data class RestartDispatch(override val runId: Long) : Action
        data class RestartCi(override val runId: Long) : Action
    }

    private val DISPATCHING = setOf(RunStatus.PREPARING, RunStatus.RUNNING, RunStatus.PUSHING)
    private val CI = setOf(RunStatus.CI_WAITING, RunStatus.FIXING)

    /** [activeDispatch]/[activeCi]: canlı (bekleyen ya da çalışan) zinciri olan run kimlikleri. */
    fun plan(runs: List<RunView>, activeDispatch: Set<Long>, activeCi: Set<Long>): List<Action> = runs.mapNotNull { r ->
        when {
            r.status in DISPATCHING && r.id !in activeDispatch -> Action.RestartDispatch(r.id)
            r.status in CI && r.id !in activeCi -> Action.RestartCi(r.id)
            else -> null
        }
    }

    /** Planı uygular; yapılan eylemleri döndürür. */
    suspend fun sweep(
        runs: List<RunView>,
        isDispatchActive: suspend (Long) -> Boolean,
        isCiActive: suspend (Long) -> Boolean,
        restartDispatch: (Long) -> Unit,
        restartCi: (Long) -> Unit,
    ): List<Action> {
        val pending = runs.filter { it.status in DISPATCHING || it.status in CI }
        val activeDispatch = pending.filter { it.status in DISPATCHING && isDispatchActive(it.id) }.map { it.id }.toSet()
        val activeCi = pending.filter { it.status in CI && isCiActive(it.id) }.map { it.id }.toSet()
        val actions = plan(runs, activeDispatch, activeCi)
        actions.forEach {
            when (it) {
                is Action.RestartDispatch -> restartDispatch(it.runId)
                is Action.RestartCi -> restartCi(it.runId)
            }
        }
        return actions
    }
}
