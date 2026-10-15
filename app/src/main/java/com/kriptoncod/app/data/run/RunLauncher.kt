package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.repo.JobSeed
import com.kriptoncod.app.data.repo.PromptRepository
import com.kriptoncod.app.data.repo.RunRepository
import com.kriptoncod.app.work.RunScheduler

/** Bir prompt setinden yeni run oluşturur, işleri kuyruğa alır ve dağıtımı başlatır. */
class RunLauncher(
    private val prompts: PromptRepository,
    private val runs: RunRepository,
    private val scheduler: RunScheduler,
) {
    /** @return yeni run kimliği. Set boşsa [IllegalArgumentException]. */
    suspend fun launch(setId: Long, repo: String? = null): Long {
        val items = prompts.getItems(setId)
        require(items.isNotEmpty()) { "Prompt seti boş" }
        val runId = runs.createRun(repo)
        runs.enqueueJobs(runId, items.map { JobSeed(it.id, prompts.getAssignment(it.id)?.accountId) })
        scheduler.start(runId)
        return runId
    }
}
