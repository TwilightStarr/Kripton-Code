package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.data.repo.JobSeed
import com.kriptoncod.app.data.repo.RunRepository
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.Catalog
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.domain.WorkAssigner
import kotlinx.coroutines.flow.first

/**
 * Katalogları düğüm grafiğine çevirir: her prompt bir WORK düğümü, her katalog bir CATALOG_MERGE düğümü,
 * en sonda tek FINAL_MERGE düğümü. WORK ve katalog birleştirmeleri hesaplara güce göre dağıtılır
 * (güçlü hesap daha çok/büyük iş alır); FINAL_MERGE her zaman o an en güçlü kullanılabilir hesaba gider.
 */
class RunGraphBuilder(
    private val runs: RunRepository,
    private val accounts: AiAccountRepository,
) {
    suspend fun build(runId: Long, catalogs: List<Catalog>) {
        val candidates = accounts.observeAll().first()
            .filter { it.enabled && it.status != AccountStatus.ERROR }
            .map { WorkAssigner.Candidate(it.id, it.powerRank) }

        val flat = catalogs.flatMap { c -> c.prompts.map { c.name to it } }
        val workAccounts = WorkAssigner.assign(flat.map { it.second.body.length }, candidates)
        val mergeAccounts = WorkAssigner.assign(catalogs.map { it.size }, candidates)

        val seeds = ArrayList<JobSeed>(flat.size + catalogs.size + 1)
        flat.forEachIndexed { i, (catalog, prompt) ->
            seeds += JobSeed(0, workAccounts[i], JobKind.WORK, catalog, prompt.title, prompt.body)
        }
        catalogs.forEachIndexed { i, c ->
            seeds += JobSeed(0, mergeAccounts[i], JobKind.CATALOG_MERGE, c.name, "Birleştir: ${c.name}", null)
        }
        seeds += JobSeed(0, null, JobKind.FINAL_MERGE, null, "Nihai birleştirme", null)
        runs.enqueueJobs(runId, seeds)
    }
}
