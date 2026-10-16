package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.repo.JobSeed
import com.kriptoncod.app.data.repo.PromptRepository
import com.kriptoncod.app.data.repo.RunRepository
import com.kriptoncod.app.domain.Catalog
import com.kriptoncod.app.domain.CatalogParser
import com.kriptoncod.app.domain.CatalogPrompt
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.work.RunScheduler

/**
 * Yeni run oluşturur. İki giriş yolu vardır:
 *  - [launch]: prompt seti; ilk AI (planlayıcı) promptları kataloglara ayırır.
 *  - [launchCatalogs]: kullanıcının hazır kataloğu (ör. zip); planlayıcı atlanır.
 */
class RunLauncher(
    private val prompts: PromptRepository,
    private val runs: RunRepository,
    private val scheduler: RunScheduler,
    private val graph: RunGraphBuilder,
) {
    /** @return yeni run kimliği. Set boşsa [IllegalArgumentException]. */
    suspend fun launch(setId: Long, repo: String? = null): Long {
        val items = prompts.getItems(setId)
        require(items.isNotEmpty()) { "Prompt seti boş" }
        val runId = runs.createRun(repo)
        val body = CatalogParser.plannerPrompt(items.map { CatalogPrompt(it.title, it.body) })
        runs.enqueueJobs(
            runId,
            listOf(JobSeed(0, null, JobKind.PLAN, null, "Katalog çıkarma", body)),
        )
        scheduler.start(runId)
        return runId
    }

    /** @return yeni run kimliği. Katalog boşsa [IllegalArgumentException]. */
    suspend fun launchCatalogs(catalogs: List<Catalog>, repo: String? = null): Long {
        require(catalogs.isNotEmpty() && catalogs.any { it.prompts.isNotEmpty() }) { "Katalog boş" }
        val runId = runs.createRun(repo)
        val planId = runs.enqueueJobs(
            runId,
            listOf(JobSeed(0, null, JobKind.PLAN, null, "Kullanıcı kataloğu", null)),
        ).first()
        // Planlayıcı düğümü hazır gelir: kataloğun özeti çıktısı olarak kaydedilir.
        runs.completeJob(planId, catalogSummary(catalogs))
        graph.build(runId, catalogs)
        scheduler.start(runId)
        return runId
    }

    private fun catalogSummary(catalogs: List<Catalog>): String = buildString {
        catalogs.forEach { c ->
            appendLine("## KATALOG: ${c.name}")
            c.prompts.forEach { appendLine("- ${it.title}") }
        }
    }
}
