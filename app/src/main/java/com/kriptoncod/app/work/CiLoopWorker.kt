package com.kriptoncod.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kriptoncod.app.KriptonCodApp
import com.kriptoncod.app.data.github.CiLogSummarizer
import com.kriptoncod.app.data.github.GitHubFile
import com.kriptoncod.app.data.github.RunWait
import com.kriptoncod.app.data.providers.ApiResult
import com.kriptoncod.app.data.run.CodeBlockParser
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.CiLogParser
import com.kriptoncod.app.domain.RunStatus
import kotlinx.coroutines.flow.first

class CiLoopWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val runId = inputData.getLong(KEY_RUN_ID, -1L)
        if (runId < 0) return Result.failure()

        val c = (applicationContext as KriptonCodApp).container
        val run = c.runRepository.getRun(runId) ?: return Result.failure()
        if (run.status == RunStatus.CANCELLED) return Result.success()

        val repo = run.repo ?: return Result.failure()
        val token = c.githubTokenStore.get() ?: return Result.failure()
        val branch = "kripton-cod/${run.createdAt}"

        // Push yapan commit'in SHA'sı (dal ucundan okunur).
        val headSha = c.githubService.branchSha(token, repo, branch)
            .let { if (it is ApiResult.Ok) it.value else null }
            ?: return Result.retry()

        // Workflow bitene dek bekle (poll; worker ölürse retry ile sürer).
        val waited = c.githubService.awaitRun(token, repo, branch, headSha)
        val finished = when (waited) {
            is RunWait.Completed -> waited.run
            is RunWait.TimedOut -> return Result.retry()
            is RunWait.Failed -> return Result.retry()
        }

        if (finished.conclusion == "success") {
            c.runRepository.setRunStatus(runId, RunStatus.DONE)
            return Result.success()
        }

        // Deneme sınırı
        val maxAttempts = c.themeStore.ciMaxAttemptsFlow.first()
        c.runRepository.incrementCiAttempts(runId)
        val current = c.runRepository.getRun(runId) ?: return Result.failure()
        if (current.ciAttempts >= maxAttempts) {
            c.runRepository.setRunStatus(runId, RunStatus.FAILED)
            return Result.success()
        }

        // Log indir + hata çıkar
        val logBytes = c.githubService.downloadRunLogs(token, repo, finished.id)
        val logText = when (logBytes) {
            is ApiResult.Ok -> CiLogSummarizer.summarize(logBytes.value)
            else -> return Result.retry()
        }
        val errors = CiLogParser.parse(logText)
        if (errors.isEmpty()) {   // çözülemezse yeniden dene
            c.runRepository.setRunStatus(runId, RunStatus.FAILED)
            return Result.success()
        }

        // En güçlü hazır AI ile düzelt
        val fixPrompt = buildString {
            appendLine("Aşağıdaki CI hatalarını düzelt. Yalnızca düzeltilmiş dosyaları " +
                "```yol/dosya biçiminde döndür.")
            appendLine(CiLogParser.toPromptSummary(errors))
        }
        val account = c.aiAccountRepository.observeAll().first()
            .filter { it.status == AccountStatus.READY }
            .maxByOrNull { it.powerRank } ?: return Result.retry()

        val credential = c.credentialProvider.credentialFor(account)
            ?: return Result.retry()
        val endpoint = com.kriptoncod.app.data.providers.ProviderCatalog
            .byId(account.providerId)?.resolve() ?: return Result.retry()

        c.runRepository.setRunStatus(runId, RunStatus.FIXING)
        val fixed = try {
            c.aiGenerator.generate(endpoint, account.modelId, credential, fixPrompt)
        } catch (e: Exception) { return Result.retry() }

        val blocks = CodeBlockParser.parse(fixed).filter {
            com.kriptoncod.app.data.run.PathSafety.validate(it.path)
                is com.kriptoncod.app.data.run.PathSafety.Result.Safe
        }
        if (blocks.isEmpty()) return Result.retry()

        // Yeni commit + push
        val commit = c.githubService.commitFiles(
            token, repo, branch,
            "Kripton Cod: CI düzeltmesi (deneme ${current.ciAttempts})",
            blocks.map { GitHubFile(it.path, it.content) },
        )
        return when (commit) {
            is ApiResult.Ok -> {
                c.runRepository.setRunStatus(runId, RunStatus.CI_WAITING)
                Result.retry()   // yeni CI turunu izle
            }
            else -> Result.retry()
        }
    }

    companion object { const val KEY_RUN_ID = "run_id" }
}
