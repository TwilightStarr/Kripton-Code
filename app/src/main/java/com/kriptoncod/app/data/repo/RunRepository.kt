package com.kriptoncod.app.data.repo

import androidx.room.withTransaction
import com.kriptoncod.app.data.db.Job
import com.kriptoncod.app.data.db.JobDao
import com.kriptoncod.app.data.db.KriptonDatabase
import com.kriptoncod.app.data.db.RunDao
import com.kriptoncod.app.data.db.RunRecord
import com.kriptoncod.app.data.files.HistoryFile
import com.kriptoncod.app.data.files.HistoryJob
import com.kriptoncod.app.data.files.HistoryStore
import com.kriptoncod.app.data.files.OutputStore
import com.kriptoncod.app.data.files.StoreRecovery
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow

data class JobSeed(val promptId: Long, val accountId: Long?)

data class RecoverySummary(
    /** Çıktısı diskte tamamlanmış ama DB'de DONE olmayan işler (kilitlenme sonrası DONE yapıldı). */
    val adopted: Int,
    /** Yarım kalan RUNNING işler PENDING'e döndü. */
    val requeued: Int,
    /** DONE görünüp dosyası kaybolan/bozuk işler PENDING'e döndü. */
    val lostOutputs: Int,
    val files: StoreRecovery,
)

class RunRepository(
    private val db: KriptonDatabase,
    private val outputs: OutputStore,
    private val history: HistoryStore,
    private val runs: RunDao = db.runDao(),
    private val jobs: JobDao = db.jobDao(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun observeRuns(): Flow<List<RunRecord>> = runs.observeAll()
    fun observeRun(id: Long): Flow<RunRecord?> = runs.observe(id)
    fun observeJobs(runId: Long): Flow<List<Job>> = jobs.observeByRun(runId)

    suspend fun getRun(id: Long): RunRecord? = runs.get(id)
    suspend fun getJobs(runId: Long): List<Job> = jobs.getByRun(runId)

    suspend fun createRun(repo: String? = null): Long =
        runs.insert(RunRecord(createdAt = now(), status = RunStatus.PREPARING, repo = repo))

    suspend fun enqueueJobs(runId: Long, seeds: List<JobSeed>): List<Long> {
        val t = now()
        return jobs.insertAll(seeds.map { Job(runId = runId, promptId = it.promptId, accountId = it.accountId, updatedAt = t) })
    }

    suspend fun setRunStatus(runId: Long, status: RunStatus) {
        runs.setStatus(runId, status)
    }

    suspend fun setRepo(runId: Long, repo: String?) {
        runs.setRepo(runId, repo)
    }

    suspend fun incrementCiAttempts(runId: Long) {
        runs.incrementCiAttempts(runId)
    }

    suspend fun startJob(jobId: Long) {
        jobs.markStarted(jobId, JobState.RUNNING, now())
    }

    suspend fun waitJob(jobId: Long) {
        jobs.setState(jobId, JobState.WAITING, now())
    }

    suspend fun failJob(jobId: Long) {
        jobs.setState(jobId, JobState.FAILED, now())
    }

    /** Yeniden üretim: eski çıktıyı siler ve işi PENDING yapar (aksi halde [recover] eski .txt'yi DONE sayar). */
    suspend fun requeueJob(jobId: Long) {
        val job = jobs.get(jobId) ?: return
        outputs.deleteOutput(job.runId, jobId)
        jobs.setResult(jobId, JobState.PENDING, null, now())
    }

    suspend fun assignAccount(jobId: Long, accountId: Long?) {
        jobs.setAccount(jobId, accountId, now())
    }

    /** Çıktıyı atomik yazar, sonra DB'yi DONE yapar. Arada çökerse [recover] işi DONE'a alır. */
    suspend fun completeJob(jobId: Long, text: String) {
        val job = jobs.get(jobId) ?: error("İş yok: $jobId")
        val path = outputs.write(job.runId, jobId, text)
        jobs.setResult(jobId, JobState.DONE, path, now())
    }

    suspend fun readOutput(jobId: Long): String? {
        val path = jobs.get(jobId)?.outputPath ?: return null
        return outputs.read(path)
    }

    /** Zip'i atomik yazar ve yolunu RunRecord'a kaydeder. */
    suspend fun attachZip(runId: Long, writer: (OutputStream) -> Unit): String {
        val path = outputs.writeZip(runId, writer)
        runs.setZip(runId, path)
        return path
    }

    /** Çalıştırmanın geçmiş JSON'unu (history/<runId>.json) DB durumundan üretir. */
    suspend fun saveHistory(runId: Long) {
        val run = runs.get(runId) ?: return
        history.write(
            HistoryFile(
                runId = run.id,
                createdAt = run.createdAt,
                status = run.status.name,
                repo = run.repo,
                ciAttempts = run.ciAttempts,
                zipPath = run.zipPath,
                jobs = jobs.getByRun(runId).map {
                    HistoryJob(it.id, it.promptId, it.accountId, it.state.name, it.attempts, it.outputPath)
                },
            ),
        )
    }

    suspend fun deleteRun(runId: Long) {
        runs.deleteById(runId) // işler CASCADE ile gider
        outputs.deleteRun(runId)
        history.delete(runId)
    }

    /**
     * Açılış kurtarması: dosya deposunu süpürür, sonra DB ile uzlaştırır.
     *  - PENDING/RUNNING/WAITING iş için geçerli .txt varsa -> DONE (kilitlenme .txt ile DB arasında olmuş),
     *  - kalan RUNNING işler -> PENDING,
     *  - DONE ama dosyası yok/boş -> PENDING (çıktı yeniden üretilecek).
     */
    suspend fun recover(): RecoverySummary {
        val files = outputs.recover()
        history.recover()
        var adopted = 0
        var requeued = 0
        var lost = 0
        db.withTransaction {
            val t = now()
            for (state in listOf(JobState.PENDING, JobState.RUNNING, JobState.WAITING)) {
                for (job in jobs.getByState(state)) {
                    val rel = outputs.outputRelativePath(job.runId, job.id)
                    if (outputs.isValid(rel)) {
                        jobs.setResult(job.id, JobState.DONE, rel, t)
                        adopted++
                    } else if (state == JobState.RUNNING) {
                        jobs.setState(job.id, JobState.PENDING, t)
                        requeued++
                    }
                }
            }
            for (job in jobs.getByState(JobState.DONE)) {
                val path = job.outputPath
                if (path == null || !outputs.isValid(path)) {
                    jobs.setResult(job.id, JobState.PENDING, null, t)
                    lost++
                }
            }
        }
        return RecoverySummary(adopted, requeued, lost, files)
    }
}
