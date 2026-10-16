package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.github.GitHubFile
import com.kriptoncod.app.data.github.GitHubService
import com.kriptoncod.app.data.github.GitHubTokenStore
import com.kriptoncod.app.data.github.VerifyWorkflow
import com.kriptoncod.app.data.providers.ApiResult
import com.kriptoncod.app.data.repo.RunRepository
import com.kriptoncod.app.domain.RunStatus

/**
 * Tamamlanan run'ın zip'indeki dosyaları, seçilen repoda yeni bir dala (`kriptoncod/run-<id>`)
 * TEK commit olarak gönderir. Commit'e doğrulama workflow'u da eklenir; böylece push CI'ı tetikler.
 */
class RunPublisher(
    private val runs: RunRepository,
    private val github: GitHubService,
    private val tokens: GitHubTokenStore,
) {

    /** Bu run GitHub'a gönderilmeli mi: repo seçilmiş ve token tanımlı. */
    suspend fun shouldPublish(runId: Long): Boolean =
        runs.getRun(runId)?.repo != null && tokens.get() != null

    /**
     * Dosyaları gönderir. Başarılıysa true; her türlü hatada (token, ağ, izin, boş zip) false.
     * Durum `PUSHING` olarak işaretlenir; sonraki durumu çağıran belirler.
     */
    suspend fun publish(runId: Long): Boolean {
        val run = runs.getRun(runId) ?: return false
        val repo = run.repo ?: return false
        val token = tokens.get() ?: return false

        runs.setRunStatus(runId, RunStatus.PUSHING)

        val files = runs.readZipFiles(runId)
            .filter { (path, _) -> path != VerifyWorkflow.PATH && !path.startsWith("_kripton/") }
            .map { (path, bytes) -> GitHubFile(path, bytes) }
        if (files.isEmpty()) return false

        return try {
            val base = github.defaultBranch(token, repo)
            if (base !is ApiResult.Ok) return false
            val branch = GitHubService.runBranch(runId)
            if (github.createBranch(token, repo, branch, base.value) !is ApiResult.Ok) return false

            val commit = github.commitFiles(
                token = token,
                repo = repo,
                branch = branch,
                message = "Kripton Cod: run #$runId",
                files = files + GitHubFile(VerifyWorkflow.PATH, VerifyWorkflow.YAML),
            )
            commit is ApiResult.Ok
        } catch (_: IllegalArgumentException) {
            // Geçersiz repo/dal/yol ya da dosya içeriğinde gizli anahtar: gönderilmez.
            false
        }
    }
}
