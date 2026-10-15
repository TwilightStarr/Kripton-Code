package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.ai.AiGenerationException
import com.kriptoncod.app.data.ai.AiGenerationException.Kind
import com.kriptoncod.app.data.ai.AiGenerator
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.db.Job
import com.kriptoncod.app.data.providers.ProviderCatalog
import com.kriptoncod.app.data.providers.ProviderEndpoint
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.data.repo.PromptRepository
import com.kriptoncod.app.data.repo.RunRepository
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AssignmentMode
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
import kotlinx.coroutines.flow.first

sealed interface DispatchOutcome {
    /** Tüm işler bitti; run bu durumda kapandı. */
    data class Finished(val status: RunStatus) : DispatchOutcome

    /** Açık işler var ama şimdi ilerlenemiyor (rate limit / geçici hata); [delayMs] sonra tekrar çağır. */
    data class Wait(val delayMs: Long) : DispatchOutcome

    /** Kullanıcı run'ı iptal etti. */
    data object Cancelled : DispatchOutcome
}

/**
 * Bir run'ın bekleyen işlerini AI hesaplarına dağıtır ve üretir.
 *
 * Kurallar ([AiGenerationException] belgesindeki sözleşmeye uyar):
 * - AUTO iş: en güçlü (powerRank) kullanılabilir hesaba gider; hesap 401/429 verirse sıradakine geçer.
 * - MANUAL iş: yalnız atanan hesapta çalışır; 429'da bekler, 401'de FAILED olur.
 * - 401/403/402 → hesap ERROR. 429 → hesap WAITING (Retry-After). 5xx/ağ → iş yeniden kuyruğa,
 *   [MAX_ATTEMPTS] denemeden sonra FAILED. Diğer hatalar → iş FAILED.
 * - Kimlik bilgisi veya uç nokta çözülemeyen hesap ERROR yapılır.
 *
 * [process] idempotenttir: çökme/iptal sonrası tekrar çağrılabilir (yarım kalan RUNNING işler kuyruğa döner).
 */
class PromptDispatcher(
    private val runs: RunRepository,
    private val prompts: PromptRepository,
    private val accounts: AiAccountRepository,
    private val credentials: CredentialProvider,
    private val generator: AiGenerator,
    private val now: () -> Long = System::currentTimeMillis,
    private val resolveEndpoint: (AiAccount) -> ProviderEndpoint? =
        { ProviderCatalog.byId(it.providerId)?.resolve() },
    /** Tüm işler DONE olunca çağrılır; ZIP/GitHub adımları buraya bağlanır. Dönen durum run'a yazılır. */
    private val onAllJobsDone: suspend (runId: Long) -> RunStatus = { RunStatus.DONE },
) {

    suspend fun process(runId: Long): DispatchOutcome {
        val run = runs.getRun(runId) ?: return DispatchOutcome.Cancelled
        when (run.status) {
            RunStatus.CANCELLED -> return DispatchOutcome.Cancelled
            RunStatus.DONE, RunStatus.FAILED -> return DispatchOutcome.Finished(run.status)
            else -> Unit
        }
        runs.setRunStatus(runId, RunStatus.RUNNING)
        accounts.releaseExpiredWaits()

        // Önceki worker ölmüşse RUNNING'de kalan işleri kuyruğa geri al.
        runs.getJobs(runId).filter { it.state == JobState.RUNNING }.forEach { runs.requeueJob(it.id) }

        var wakeAtMs: Long? = null
        for (job in runs.getJobs(runId)) {
            if (job.state != JobState.PENDING && job.state != JobState.WAITING) continue
            if (runs.getRun(runId)?.status == RunStatus.CANCELLED) return DispatchOutcome.Cancelled
            val result = processJob(job)
            if (result is JobResult.Wait) {
                wakeAtMs = minOf(wakeAtMs ?: result.untilMs, result.untilMs)
            }
        }

        val jobs = runs.getJobs(runId)
        if (jobs.any { it.state in OPEN_STATES }) {
            val delay = ((wakeAtMs ?: (now() + RETRY_BASE_MS)) - now()).coerceAtLeast(MIN_DELAY_MS)
            return DispatchOutcome.Wait(delay)
        }

        val status = if (jobs.all { it.state == JobState.DONE }) onAllJobsDone(runId) else RunStatus.FAILED
        runs.setRunStatus(runId, status)
        runs.saveHistory(runId)
        return DispatchOutcome.Finished(status)
    }

    private sealed interface JobResult {
        data object Done : JobResult
        data object Failed : JobResult
        data class Wait(val untilMs: Long) : JobResult
    }

    private sealed interface Pick {
        data class Use(val account: AiAccount) : Pick
        data class WaitUntil(val untilMs: Long) : Pick
        data object None : Pick
    }

    private suspend fun processJob(job: Job): JobResult {
        val item = prompts.getItem(job.promptId)
        if (item == null || item.body.isBlank()) {
            runs.failJob(job.id)
            return JobResult.Failed
        }
        val manual = prompts.getAssignment(job.promptId)?.mode == AssignmentMode.MANUAL
        val tried = mutableSetOf<Long>()

        while (true) {
            val account = when (val pick = pickAccount(job, manual, tried)) {
                is Pick.Use -> pick.account
                is Pick.WaitUntil -> {
                    runs.waitJob(job.id)
                    return JobResult.Wait(pick.untilMs)
                }
                Pick.None -> {
                    runs.failJob(job.id)
                    return JobResult.Failed
                }
            }
            tried += account.id

            val endpoint = resolveEndpoint(account)
            val credential = credentials.credentialFor(account)
            if (endpoint == null || credential.isNullOrBlank()) {
                accounts.markError(account.id)
                if (manual) {
                    runs.failJob(job.id)
                    return JobResult.Failed
                }
                continue
            }

            if (job.accountId != account.id) runs.assignAccount(job.id, account.id)
            runs.startJob(job.id)

            try {
                val text = generator.generate(endpoint, account.modelId, credential, item.body)
                if (text.isBlank()) {
                    runs.failJob(job.id)
                    return JobResult.Failed
                }
                runs.completeJob(job.id, text)
                return JobResult.Done
            } catch (e: AiGenerationException) {
                when (e.kind) {
                    Kind.UNAUTHORIZED -> {
                        accounts.markError(account.id)
                        if (manual) {
                            runs.failJob(job.id)
                            return JobResult.Failed
                        }
                        // AUTO: döngü başa döner, sıradaki hesap seçilir.
                    }
                    Kind.RATE_LIMITED -> {
                        val until = e.waitUntil(now())
                        accounts.markWaiting(account.id, until)
                        if (manual) {
                            runs.waitJob(job.id)
                            return JobResult.Wait(until)
                        }
                    }
                    Kind.SERVER, Kind.NETWORK -> {
                        val attempts = runs.getJob(job.id)?.attempts ?: MAX_ATTEMPTS
                        if (attempts >= MAX_ATTEMPTS) {
                            runs.failJob(job.id)
                            return JobResult.Failed
                        }
                        runs.requeueJob(job.id)
                        return JobResult.Wait(now() + RETRY_BASE_MS * attempts)
                    }
                    else -> { // NOT_FOUND, BAD_REQUEST, BAD_RESPONSE, TRUNCATED: aynı girdiyle tekrar anlamsız
                        runs.failJob(job.id)
                        return JobResult.Failed
                    }
                }
            } catch (_: IllegalArgumentException) { // boş model kimliği vb. yapılandırma hatası
                runs.failJob(job.id)
                return JobResult.Failed
            }
        }
    }

    private suspend fun pickAccount(job: Job, manual: Boolean, tried: Set<Long>): Pick {
        val t = now()
        if (manual) {
            val id = job.accountId?.takeIf { it !in tried } ?: return Pick.None
            val account = accounts.get(id) ?: return Pick.None
            if (!account.enabled || account.status == AccountStatus.ERROR) return Pick.None
            val until = account.waitUntil
            if (account.status == AccountStatus.WAITING && until != null && until > t) return Pick.WaitUntil(until)
            return Pick.Use(account)
        }

        // observeAll() powerRank'e göre azalan sıralıdır: en güçlü hesap önce.
        val enabled = accounts.observeAll().first().filter { it.enabled }
        enabled.firstOrNull { it.id !in tried && it.isUsable(t) }?.let { return Pick.Use(it) }

        val earliest = enabled
            .filter { it.status == AccountStatus.WAITING }
            .mapNotNull { it.waitUntil }
            .filter { it > t }
            .minOrNull()
        return if (earliest != null) Pick.WaitUntil(earliest) else Pick.None
    }

    private fun AiAccount.isUsable(t: Long): Boolean = when (status) {
        AccountStatus.READY -> true
        AccountStatus.WAITING -> (waitUntil ?: 0L) <= t
        AccountStatus.ERROR -> false
    }

    companion object {
        /** Bir iş için en çok başlatma sayısı (hesap değişimleri de sayılır). */
        const val MAX_ATTEMPTS = 4
        const val RETRY_BASE_MS = 15_000L
        private const val MIN_DELAY_MS = 1_000L
        private val OPEN_STATES = setOf(JobState.PENDING, JobState.RUNNING, JobState.WAITING)
    }
}
