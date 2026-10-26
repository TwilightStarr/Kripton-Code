package com.kriptoncod.app.work

/** WorkManager benzersiz iş adları. */
object WorkNames {
    const val PROMPT_DISPATCH = "kriptoncod.prompt_dispatch"
    const val CI_WATCH = "kriptoncod.ci_watch"
    const val WATCHDOG = "kriptoncod.watchdog"
    const val ACCOUNT_HEALTH = "kriptoncod.account_health"

    /** Bir çalıştırmanın dağıtım işi; her run için tek benzersiz zincir. */
    fun dispatch(runId: Long): String = "$PROMPT_DISPATCH.$runId"

    /** CI izleme/düzeltme döngüsü işi. */
    fun ciLoop(runId: Long): String = "ci_loop_$runId"

    /** Seri mod çalışmasının ön plan işçisi. */
    fun serial(id: Long): String = "serial_run_$id"

    /** Kısmi sonucun onaylı GitHub gönderimi. */
    fun partialPush(runId: Long): String = "partial_push_$runId"
}
