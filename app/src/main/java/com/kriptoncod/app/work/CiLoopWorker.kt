package com.kriptoncod.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kriptoncod.app.KriptonCodApp
import com.kriptoncod.app.data.github.CiLogSummarizer
import com.kriptoncod.app.data.github.GitHubFile
import com.kriptoncod.app.data.github.GitHubService
import com.kriptoncod.app.data.github.RunWait
import com.kriptoncod.app.data.providers.ApiResult
import com.kriptoncod.app.data.run.CiFixer
import com.kriptoncod.app.domain.CiLogParser
import com.kriptoncod.app.domain.RunStatus
import kotlinx.coroutines.flow.first

/**
 * GitHub Actions sonucunu bekler; başarısızsa log özetini AI'a verip düzeltmeyi aynı dala commit'ler.
 * Geçici hatalarda [Result.retry] kullanılır; [MAX_WORKER_ATTEMPTS] aşılırsa run FAILED olur.
 */
class CiLoopWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val runId = inputData.getLong(KEY_RUN_ID, -1L)
        if (runId < 0) return Result.failure()

        val c = (applicationContext as KriptonCodApp).container
        val run = c.runRepository.getRun(runId) ?: return Result.failure()
        if (run.status == RunStatus.CANCELLED) return Result.success()

        val repo = run.repo ?: return failRun(runId)
        val token = c.githubTokenStore.get() ?: return failRun(runId)
        val branch = GitHubService.runBranch(runId)

        // Geçici hata: sınırsız yeniden denemeyi önle.
        suspend fun retryOrFail(): Result = if (runAttemptCount < MAX_WORKER_ATTEMPTS) Result.retry() else failNow(c, runId)

        // Push yapan commit'in SHA'sı (dal ucundan okunur).
        val headSha = when (val head = c.githubService.branchSha(token, repo, branch)) {
            is ApiResult.Ok -> head.value
            is ApiResult.Fail -> return retryOrFail()
        }

        // Workflow bitene dek bekle (poll; worker ölürse retry ile sürer).
        val finished = when (val waited = c.githubService.awaitRun(token, repo, branch, headSha)) {
            is RunWait.Completed -> waited.run
            is RunWait.TimedOut -> return retryOrFail()
            is RunWait.Failed -> return retryOrFail()
        }

        if (finished.conclusion == "success") {
            c.runRepository.setRunStatus(runId, RunStatus.DONE)
            c.runRepository.saveHistory(runId)
            return Result.success()
        }

        // Deneme sınırı
        val maxAttempts = c.themeStore.ciMaxAttemptsFlow.first()
        c.runRepository.incrementCiAttempts(runId)
        val current = c.runRepository.getRun(runId) ?: return Result.failure()
        if (current.ciAttempts >= maxAttempts) return failRun(runId)

        // Log indir + hata çıkar
        val logText = when (val logBytes = c.githubService.downloadRunLogs(token, repo, finished.id)) {
            is ApiResult.Ok -> CiLogSummarizer.summarize(logBytes.value)
            else -> return retryOrFail()
        }
        val errors = CiLogParser.parse(logText)
        if (errors.isEmpty()) return failRun(runId) // hata çözümlenemedi

        // Düzeltme: hatalı dosyaların güncel içeriğiyle; hesaplar sırayla denenir (bkz. CiFixer).
        c.runRepository.setRunStatus(runId, RunStatus.FIXING)
        val blocks = when (val fixed = c.ciFixer.fix(runId, errors, current.ciAttempts)) {
            is CiFixer.Result.Fixed -> fixed.files
            is CiFixer.Result.Failed -> return retryOrFail()
        }

        // Yeni commit + push
        val commit = try {
            c.githubService.commitFiles(
                token,
                repo,
                branch,
                "Kripton Cod: CI düzeltmesi (deneme ${current.ciAttempts})",
                blocks.map { GitHubFile(it.path, it.content) },
            )
        } catch (e: IllegalArgumentException) {
            return failRun(runId)
        }
        if (commit is ApiResult.Ok) {
            runCatching { c.ciFixer.applyToZip(runId, blocks) } // sonraki tur güncel içerikle çalışsın
            c.runRepository.setRunStatus(runId, RunStatus.CI_WAITING)
        }
        return retryOrFail() // yeni CI turunu izle
    }

    private suspend fun failRun(runId: Long): Result {
        failNow((applicationContext as KriptonCodApp).container, runId)
        return Result.success() // iş bitti (başarısız); WorkManager tekrar denemesin
    }

    private suspend fun failNow(c: com.kriptoncod.app.AppContainer, runId: Long): Result {
        runCatching {
            c.runRepository.setRunStatus(runId, RunStatus.FAILED)
            c.runRepository.saveHistory(runId)
        }
        return Result.failure()
    }

    companion object {
        const val KEY_RUN_ID = "run_id"
        private const val MAX_WORKER_ATTEMPTS = 20
    }
}
