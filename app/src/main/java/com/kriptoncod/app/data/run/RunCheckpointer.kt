package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.db.Job
import com.kriptoncod.app.data.github.GitHubFile
import com.kriptoncod.app.data.github.GitHubService
import com.kriptoncod.app.data.github.GitHubTokenStore
import com.kriptoncod.app.data.providers.ApiResult
import com.kriptoncod.app.data.repo.RunRepository
import com.kriptoncod.app.data.web.WebOutputCheck
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.domain.JobState

/**
 * Ara kayıt: her katalog birleştirmesi bitince o katalogun dosyalarını (ve ham düğüm çıktılarını) GitHub'da
 * `kriptoncod/wip-<runId>` dalına commit eder. Telefon kaybolsa ya da son birleştirme çökse bile günlerin işi
 * depoda durur. Yalnızca repo seçilmiş ve token tanımlıysa çalışır; hata run'ı ASLA etkilemez (en iyi çaba).
 * WIP dalı CI workflow'unu tetiklemez (workflow dosyası yalnız nihai dalda vardır) ve mesaja `[skip ci]` eklenir.
 */
class RunCheckpointer(
    private val runs: RunRepository,
    private val github: GitHubService,
    private val tokens: GitHubTokenStore,
) {

    /** @return commit atıldıysa true. */
    suspend fun checkpoint(runId: Long, merge: Job): Boolean {
        if (merge.kind != JobKind.CATALOG_MERGE || merge.state != JobState.DONE) return false
        val repo = runs.getRun(runId)?.repo ?: return false
        val token = tokens.get() ?: return false
        val files = filesFor(runId, merge)
        if (files.isEmpty()) return false

        val branch = wipBranch(runId)
        return try {
            val base = github.defaultBranch(token, repo)
            if (base !is ApiResult.Ok) return false
            // Dal varsa mevcut ucu döner (yeniden deneme güvenli); yoksa varsayılan daldan açılır.
            if (github.createBranch(token, repo, branch, base.value) !is ApiResult.Ok) return false
            github.commitFiles(
                token = token,
                repo = repo,
                branch = branch,
                message = "Kripton Cod: run #$runId — ${merge.catalog ?: "katalog"} tamamlandı (ara kayıt) [skip ci]",
                files = files,
            ) is ApiResult.Ok
        } catch (_: IllegalArgumentException) {
            false // geçersiz yol/dal ya da dosyada gizli anahtar: gönderilmez
        }
    }

    private suspend fun filesFor(runId: Long, merge: Job): List<GitHubFile> {
        val out = linkedMapOf<String, ByteArray>()
        // 1) Birleştirilmiş katalog dosyaları, nihai paketteki yollarıyla.
        val merged = runs.readOutput(merge.id).orEmpty()
        for (b in CodeBlockParser.parse(WebOutputCheck.canonical(merged))) {
            val p = (PathSafety.validate(b.path) as? PathSafety.Result.Safe)?.normalized ?: continue
            if (p.startsWith("_kripton/") || p.startsWith(".github/")) continue
            out[p] = b.content.toByteArray(Charsets.UTF_8)
        }
        // 2) Bu katalogun ham düğüm çıktıları: birleştirme yanlış giderse kaynak kaybolmasın.
        for (job in runs.getJobs(runId)) {
            if (job.kind != JobKind.WORK || job.catalog != merge.catalog || job.state != JobState.DONE) continue
            val text = runs.readOutput(job.id) ?: continue
            out["$NODES_DIR/job${job.id}_${sanitize(job.title)}.md"] = text.toByteArray(Charsets.UTF_8)
        }
        return out.map { GitHubFile(it.key, it.value) }
    }

    companion object {
        const val NODES_DIR = ".kriptoncod/wip/nodes"

        fun wipBranch(runId: Long): String = "kriptoncod/wip-$runId"

        private fun sanitize(title: String?): String = (title ?: "node").replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').ifEmpty { "node" }.take(40)
    }
}
