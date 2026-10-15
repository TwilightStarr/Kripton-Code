package com.kriptoncod.app.work

/** WorkManager benzersiz iş adları. */
object WorkNames {
    const val PROMPT_DISPATCH = "kriptoncod.prompt_dispatch"
    const val CI_WATCH = "kriptoncod.ci_watch"

    /** Bir çalıştırmanın dağıtım işi; her run için tek benzersiz zincir. */
    fun dispatch(runId: Long): String = "$PROMPT_DISPATCH.$runId"

    /** CI izleme/düzeltme döngüsü işi. */
    fun ciLoop(runId: Long): String = "ci_loop_$runId"
}
