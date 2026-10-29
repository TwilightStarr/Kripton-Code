package com.kriptoncod.app.data.serial

import com.kriptoncod.app.data.github.CiLogSummarizer
import com.kriptoncod.app.data.github.GitHubFile
import com.kriptoncod.app.data.github.GitHubRun
import com.kriptoncod.app.data.github.GitHubService
import com.kriptoncod.app.data.github.GitHubTokenStore
import com.kriptoncod.app.data.providers.ApiError
import com.kriptoncod.app.data.providers.ApiResult
import com.kriptoncod.app.data.vault.KeyVaultException
import kotlinx.coroutines.delay

/**
 * GitHub işlemi başarısız. [fatal]: yeniden denemek işe yaramaz (token yok/izinsiz, repo bulunamadı...); kullanıcı müdahalesi
 * gerekir. Değilse (ağ, sunucu, kota) bekleyip yeniden denenir.
 */
class SerialGitException(val fatal: Boolean, message: String) : Exception(message)

/** Bir commit'in GitHub Actions sonucu. */
sealed interface CiResult {
    /** Tüm workflow'lar başarılı; [warnings] günlükteki (gürültü süzülmüş) uyarılardır. */
    data class Passed(val warnings: List<String>) : CiResult

    data class Failed(val errors: List<String>, val warnings: List<String>) : CiResult

    /** Commit hiçbir workflow tetiklemedi (repoda Actions yok ya da dal süzgeci eşleşmedi). */
    data object NoRuns : CiResult

    data object TimedOut : CiResult

    /** [SerialGit.awaitCi]'nin `shouldStop` çağrısı true döndü (duraklat/iptal). */
    data object Stopped : CiResult
}

/** Seri modun GitHub ihtiyaçları; testlerde sahte uygulanır. */
interface SerialGit {
    suspend fun defaultBranch(repo: String): String

    /** [branch] ucundaki projenin dosyaları (yol → bayt). */
    suspend fun snapshot(repo: String, branch: String): Map<String, ByteArray>

    /** [files]'ı [branch] ucuna TEK commit olarak yazar; yeni commit SHA'sı döner. */
    suspend fun commit(repo: String, branch: String, message: String, files: List<GitHubFile>): String

    /** [sha] commit'inin tüm workflow'ları bitene kadar bekler ve günlükleri tarar. */
    suspend fun awaitCi(repo: String, sha: String, shouldStop: () -> Boolean): CiResult

    /**
     * [branch] ucunda ZATEN tamamlanmış bir Actions çalıştırması varsa onun sonucu (bekleme yok); hiç run yoksa, run sürüyorsa ya da
     * sorgu başarısızsa null. Yeni sohbet açılırken AI'ın mevcut build hatalarını baştan görmesi içindir.
     */
    suspend fun existingCi(repo: String, branch: String): CiResult? = null
}

class GitHubSerialGit(
    private val github: GitHubService,
    private val tokens: GitHubTokenStore,
    private val pollMs: Long = 20_000L,
    private val settleMs: Long = 25_000L,
    private val noRunGraceMs: Long = 4L * 60_000L,
    private val timeoutMs: Long = 3L * 3_600_000L,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val now: () -> Long = System::currentTimeMillis,
) : SerialGit {

    private suspend fun token(): String = try {
        tokens.get()
    } catch (e: KeyVaultException) {
        null
    } ?: throw SerialGitException(true, "GitHub token tanımlı değil. Ayarlar > GitHub bölümünden ekleyin.")

    private fun fail(f: ApiResult.Fail, what: String): SerialGitException = when (f.error) {
        ApiError.INVALID_KEY -> SerialGitException(true, "GitHub $what reddedildi: token geçersiz ya da izni yok (repo + workflow izni gerekir).")
        ApiError.NOT_FOUND -> SerialGitException(true, "GitHub $what bulunamadı: repo/dal yok, token erişemiyor ya da repo boş.")
        else -> SerialGitException(false, "GitHub $what başarısız (${f.error}${f.httpCode?.let { " $it" } ?: ""}).")
    }

    override suspend fun defaultBranch(repo: String): String = when (val r = github.defaultBranch(token(), repo)) {
        is ApiResult.Ok -> r.value
        is ApiResult.Fail -> throw fail(r, "repo bilgisi")
    }

    override suspend fun snapshot(repo: String, branch: String): Map<String, ByteArray> = when (val r = github.downloadRepoZip(token(), repo, branch)) {
        is ApiResult.Ok -> {
            val files = try {
                RepoZip.read(r.value).files
            } catch (e: java.io.IOException) {
                throw SerialGitException(false, "Repo zip'i okunamadı (eksik/bozuk indirme); yeniden denenecek.")
            }
            // Boş sonuç eksik inmiş bir arşivin belirtisidir; boş proje AI'a "eksiksiz proje" diye verilmesin.
            if (files.isEmpty()) throw SerialGitException(false, "Repo zip'i boş geldi; yeniden denenecek.")
            files
        }

        is ApiResult.Fail -> throw fail(r, "repo indirme")
    }

    override suspend fun existingCi(repo: String, branch: String): CiResult? {
        val token = token()
        val sha = when (val r = github.branchSha(token, repo, branch)) {
            is ApiResult.Ok -> r.value
            is ApiResult.Fail -> return null
        }
        val runs = when (val r = github.listRunsForSha(token, repo, sha)) {
            is ApiResult.Ok -> r.value
            is ApiResult.Fail -> return null
        }
        if (runs.isEmpty() || runs.any { !it.isCompleted }) return null
        return evaluate(token, repo, runs)
    }

    override suspend fun commit(repo: String, branch: String, message: String, files: List<GitHubFile>): String {
        val result = try {
            github.commitFiles(token(), repo, branch, message, files)
        } catch (e: IllegalArgumentException) {
            throw SerialGitException(true, "Commit reddedildi: ${e.message}")
        }
        return when (result) {
            is ApiResult.Ok -> result.value
            is ApiResult.Fail -> throw fail(result, "commit")
        }
    }

    override suspend fun awaitCi(repo: String, sha: String, shouldStop: () -> Boolean): CiResult {
        val token = token()
        val start = now()
        var apiFailures = 0
        var settledCount = -1
        var settledAt = 0L
        while (true) {
            if (shouldStop()) return CiResult.Stopped
            when (val r = github.listRunsForSha(token, repo, sha)) {
                is ApiResult.Fail -> {
                    val e = fail(r, "Actions sorgusu")
                    if (e.fatal) throw e
                    if (++apiFailures > MAX_API_FAILURES) throw e
                }

                is ApiResult.Ok -> {
                    apiFailures = 0
                    val runs = r.value
                    if (runs.isEmpty()) {
                        if (now() - start > noRunGraceMs) return CiResult.NoRuns
                    } else if (runs.all { it.isCompleted }) {
                        // Bir commit birkaç workflow tetikleyebilir ve hepsi aynı anda görünmeyebilir: sayı sabitlenene dek bekle.
                        if (settledCount != runs.size) {
                            settledCount = runs.size
                            settledAt = now()
                        } else if (now() - settledAt >= settleMs) {
                            return evaluate(token, repo, runs)
                        }
                    } else {
                        settledCount = -1
                    }
                }
            }
            if (now() - start > timeoutMs) return CiResult.TimedOut
            sleep(pollMs)
        }
    }

    private suspend fun evaluate(token: String, repo: String, runs: List<GitHubRun>): CiResult {
        val errors = ArrayList<String>()
        val warnings = ArrayList<String>()
        var failed = false
        for (run in runs) {
            val bad = run.conclusion !in IGNORABLE
            if (bad) failed = true
            val bytes = downloadLogs(token, repo, run.id)
            if (bytes == null) {
                if (bad) errors += "[run ${run.id}] workflow sonucu: ${run.conclusion} (günlük indirilemedi) ${run.htmlUrl}"
                continue
            }
            val scan = CiLogScan.scan(bytes)
            warnings += scan.warnings
            if (bad) {
                errors += scan.errors.ifEmpty {
                    // Hata satırı bulunamadı: günlüğün sonu verilir ki AI neyin patladığını görebilsin.
                    val tail = CiLogSummarizer.summarize(bytes)
                    listOf("[run ${run.id}] workflow sonucu: ${run.conclusion}") + tail.lines().filter { it.isNotBlank() }.takeLast(400)
                }
            } else {
                // Başarılı run'da hata satırı olsa bile (ör. "continue-on-error" adımları) yalnız uyarı sayılır; hiçbiri atlanmaz.
                warnings += scan.errors.map { "$it (başarılı run'da)" }
            }
        }
        val w = warnings.distinct()
        return if (failed) CiResult.Failed(errors.distinct(), w) else CiResult.Passed(w)
    }

    private suspend fun downloadLogs(token: String, repo: String, runId: Long): ByteArray? {
        repeat(3) { attempt ->
            when (val r = github.downloadRunLogs(token, repo, runId)) {
                is ApiResult.Ok -> return r.value
                is ApiResult.Fail -> if (r.error == ApiError.NOT_FOUND && attempt >= 1) return null
            }
            sleep(5_000L)
        }
        return null
    }

    private companion object {
        const val MAX_API_FAILURES = 30

        /** Düzeltilecek bir şey olmayan sonuçlar (başarı, atlanma, elle iptal, onay bekleyen). */
        val IGNORABLE = setOf("success", "skipped", "neutral", "cancelled", "action_required", "stale")
    }
}
