package com.kriptoncod.app.data.github

import com.kriptoncod.app.data.providers.ApiError
import com.kriptoncod.app.data.providers.ApiResult
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * GitHub REST istemcisi: repo listesi, dal oluşturma, tek commit ile dosya yazma,
 * Actions run izleme ve log ZIP indirme.
 *
 * Güvenlik:
 * - Token yalnız `Authorization` başlığında gider; loglanmaz, hata sonuçlarına girmez.
 * - Yönlendirme kapalıdır. Log indirmede Location elle izlenir ve token ikinci isteğe EKLENMEZ.
 * - Commit edilecek dosyalardan biri token metnini içeriyorsa işlem reddedilir.
 */
class GitHubService(
    private val client: OkHttpClient = defaultClient(),
    private val apiBase: HttpUrl = "https://api.github.com/".toHttpUrl(),
    private val json: Json = Json { ignoreUnknownKeys = true; coerceInputValues = true },
) {

    // ------------------------------------------------------------------ kullanıcı / repo

    /** Token'ı doğrular (`GET /user`). */
    suspend fun validateToken(token: String): ApiResult<GitHubUser> =
        execute(token, "GET", url("user"))
            .flatMap { decode<UserDto>(it) }
            .map { GitHubUser(it.login) }

    /** Yazma izni olan, arşivlenmemiş repoları günceller sırayla listeler (tüm sayfalar). */
    suspend fun listRepos(token: String): ApiResult<List<GitHubRepo>> {
        val all = mutableListOf<GitHubRepo>()
        var page = 0
        while (true) {
            page++
            val u = url(
                "user/repos",
                "per_page" to PAGE_SIZE.toString(),
                "page" to page.toString(),
                "sort" to "updated",
                "affiliation" to "owner,collaborator,organization_member",
            )
            val dtos = execute(token, "GET", u)
                .flatMap { body -> decodeList<RepoDto>(body) }
                .getOrElse { return it }
            dtos.asSequence()
                .filter { !it.archived && !it.disabled && it.permissions?.push != false }
                .mapTo(all) { GitHubRepo(it.fullName, it.defaultBranch, it.isPrivate) }
            if (dtos.size < PAGE_SIZE) break
        }
        return ApiResult.Ok(all.distinctBy { it.fullName })
    }

    /** Reponun varsayılan dalını (`main`, `master`…) döndürür. */
    suspend fun defaultBranch(token: String, repo: String): ApiResult<String> {
        requireRepo(repo)
        return execute(token, "GET", url("repos/$repo"))
            .flatMap { decode<RepoDto>(it) }
            .map { it.defaultBranch }
    }

    // ------------------------------------------------------------------ dal / commit

    /**
     * [baseBranch] ucundan [branch] dalını oluşturur ve dal ucunun commit SHA'sını döndürür.
     * Dal zaten varsa (422) hata değil: mevcut ucun SHA'sı döner (yeniden deneme güvenli).
     */
    suspend fun createBranch(token: String, repo: String, branch: String, baseBranch: String): ApiResult<String> {
        requireRepo(repo); requireBranch(branch); requireBranch(baseBranch)
        val baseSha = branchSha(token, repo, baseBranch).getOrElse { return it }
        val body = buildJsonObject {
            put("ref", "refs/heads/$branch")
            put("sha", baseSha)
        }
        return when (val r = execute(token, "POST", url("repos/$repo/git/refs"), body)) {
            is ApiResult.Ok -> decode<RefDto>(r.value).map { it.obj.sha }
            is ApiResult.Fail -> if (r.httpCode == 422) branchSha(token, repo, branch) else r
        }
    }

    /**
     * [files] dosyalarını [branch] ucuna TEK commit olarak yazar (Git Data API) ve yeni commit SHA'sını döndürür.
     * Aynı yolu yazmak üzerine yazar. `.github/workflows` yazmak için token'da `workflow` izni gerekir.
     */
    suspend fun commitFiles(
        token: String,
        repo: String,
        branch: String,
        message: String,
        files: List<GitHubFile>,
    ): ApiResult<String> {
        requireRepo(repo); requireBranch(branch)
        require(files.isNotEmpty()) { "Commit için dosya yok" }
        require(files.all { isSafePath(it.path) }) { "Geçersiz dosya yolu" }
        require(files.map { it.path }.toSet().size == files.size) { "Yinelenen dosya yolu" }
        val secret = token.trim()
        require(secret.isNotEmpty() && files.none { String(it.content, Charsets.ISO_8859_1).contains(secret) }) {
            "Dosya içeriği gizli anahtar içeriyor"
        }

        val headSha = branchSha(token, repo, branch).getOrElse { return it }
        val baseTree = execute(token, "GET", url("repos/$repo/git/commits/$headSha"))
            .flatMap { decode<CommitDto>(it) }
            .getOrElse { return it }
            .tree.sha

        val entries = ArrayList<JsonElement>(files.size)
        for (f in files) {
            val blob = buildJsonObject {
                put("content", Base64.getEncoder().encodeToString(f.content))
                put("encoding", "base64")
            }
            val blobSha = execute(token, "POST", url("repos/$repo/git/blobs"), blob)
                .flatMap { decode<ShaDto>(it) }
                .getOrElse { return it }
                .sha
            entries += buildJsonObject {
                put("path", f.path)
                put("mode", "100644")
                put("type", "blob")
                put("sha", blobSha)
            }
        }

        val treeBody = buildJsonObject {
            put("base_tree", baseTree)
            put("tree", JsonArray(entries))
        }
        val treeSha = execute(token, "POST", url("repos/$repo/git/trees"), treeBody)
            .flatMap { decode<ShaDto>(it) }
            .getOrElse { return it }
            .sha

        val commitBody = buildJsonObject {
            put("message", message)
            put("tree", treeSha)
            put("parents", JsonArray(listOf(JsonPrimitive(headSha))))
        }
        val commitSha = execute(token, "POST", url("repos/$repo/git/commits"), commitBody)
            .flatMap { decode<ShaDto>(it) }
            .getOrElse { return it }
            .sha

        val refBody = buildJsonObject {
            put("sha", commitSha)
            put("force", false)
        }
        return execute(token, "PATCH", url("repos/$repo/git/refs/heads/$branch"), refBody)
            .map { commitSha }
    }

    // ------------------------------------------------------------------ Actions

    /** [headSha] commit'i için [branch] üzerindeki en yeni workflow run; henüz yoksa `Ok(null)`. */
    suspend fun findRun(token: String, repo: String, branch: String, headSha: String): ApiResult<GitHubRun?> {
        requireRepo(repo); requireBranch(branch)
        val u = url(
            "repos/$repo/actions/runs",
            "branch" to branch,
            "head_sha" to headSha,
            "per_page" to "5",
        )
        return execute(token, "GET", u)
            .flatMap { decode<RunsDto>(it) }
            .map { dto ->
                dto.workflowRuns.firstOrNull { it.headSha == headSha }
                    ?.let { GitHubRun(it.id, it.status, it.conclusion, it.headSha, it.htmlUrl) }
            }
    }

    /**
     * Run tamamlanana kadar [pollMs] aralıkla yoklar. Run başlangıçta henüz görünmeyebilir; görünene dek beklenir.
     * [timeoutMs] dolarsa [RunWait.TimedOut]; API hatasında [RunWait.Failed].
     */
    suspend fun awaitRun(
        token: String,
        repo: String,
        branch: String,
        headSha: String,
        timeoutMs: Long = DEFAULT_WAIT_MS,
        pollMs: Long = DEFAULT_POLL_MS,
    ): RunWait {
        var last: GitHubRun? = null
        var outcome: RunWait? = null
        withTimeoutOrNull(timeoutMs) {
            while (outcome == null) {
                when (val r = findRun(token, repo, branch, headSha)) {
                    is ApiResult.Fail -> outcome = RunWait.Failed(r)
                    is ApiResult.Ok -> r.value?.let { run ->
                        last = run
                        if (run.isCompleted) outcome = RunWait.Completed(run)
                    }
                }
                if (outcome == null) delay(pollMs)
            }
        }
        return outcome ?: RunWait.TimedOut(last)
    }

    /**
     * Run log ZIP'ini indirir. GitHub 302 ile geçici bir adrese yönlendirir; o adrese token GÖNDERİLMEZ.
     * ZIP içeriğinden hata özeti için [CiLogSummarizer] kullanılır.
     */
    suspend fun downloadRunLogs(token: String, repo: String, runId: Long): ApiResult<ByteArray> {
        requireRepo(repo)
        val request = buildRequest(token, "GET", url("repos/$repo/actions/runs/$runId/logs"), null)
            ?: return ApiResult.Fail(ApiError.INVALID_KEY)
        val first = send(request, MAX_LOG_BYTES).getOrElse { return it }
        if (first.code in 200..299) return ApiResult.Ok(first.body)

        val target = first.location?.let { first.requestUrl.resolve(it) ?: it.toHttpUrlOrNull() }
            ?: return ApiResult.Fail(ApiError.BAD_RESPONSE, first.code)
        if (target.scheme != "https" && apiBase.scheme == "https") {
            return ApiResult.Fail(ApiError.BAD_RESPONSE, first.code)
        }
        // Kimlik bilgisi olmadan: geçici adres imzalıdır.
        val download = Request.Builder().url(target).header("User-Agent", USER_AGENT).get().build()
        val second = send(download, MAX_LOG_BYTES).getOrElse { return it }
        return if (second.code in 200..299) ApiResult.Ok(second.body)
        else ApiResult.Fail(ApiError.BAD_RESPONSE, second.code)
    }

    // ------------------------------------------------------------------ iç yardımcılar

    suspend fun branchSha(token: String, repo: String, branch: String): ApiResult<String> =
        execute(token, "GET", url("repos/$repo/git/ref/heads/$branch"))
            .flatMap { decode<RefDto>(it) }
            .map { it.obj.sha }

    private class Reply(
        val code: Int,
        val location: String?,
        val rateRemaining: String?,
        val body: ByteArray,
        val requestUrl: HttpUrl,
    )

    private fun url(path: String, vararg query: Pair<String, String>): HttpUrl =
        apiBase.newBuilder().addPathSegments(path).apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

    /** Başlıkta geçersiz karakter varsa null (token kırpılmış hâliyle sınanır). */
    private fun buildRequest(token: String, method: String, url: HttpUrl, body: JsonObject?): Request? {
        val t = token.trim()
        if (t.isEmpty()) return null
        return try {
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $t")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", USER_AGENT)
                .method(method, body?.let { json.encodeToString(JsonObject.serializer(), it).toRequestBody(JSON_MEDIA) })
                .build()
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private suspend fun execute(token: String, method: String, url: HttpUrl, body: JsonObject? = null): ApiResult<String> {
        val request = buildRequest(token, method, url, body) ?: return ApiResult.Fail(ApiError.INVALID_KEY)
        val reply = send(request, MAX_JSON_BYTES).getOrElse { return it }
        // JSON uçları yönlendirme beklemez (ör. taşınmış repo); sessizce yutulmaz.
        if (reply.code in 300..399) return ApiResult.Fail(ApiError.BAD_RESPONSE, reply.code)
        return ApiResult.Ok(reply.body.toString(Charsets.UTF_8))
    }

    private suspend fun send(request: Request, maxBytes: Long): ApiResult<Reply> = try {
        client.newCall(request).await().use { response ->
            classify(
                Reply(
                    code = response.code,
                    location = response.header("Location"),
                    rateRemaining = response.header("x-ratelimit-remaining"),
                    body = response.peekBody(maxBytes).bytes(),
                    requestUrl = request.url,
                ),
            )
        }
    } catch (e: IOException) {
        ApiResult.Fail(ApiError.NETWORK)
    }

    private fun classify(r: Reply): ApiResult<Reply> {
        val c = r.code
        return when {
            c in 200..399 -> ApiResult.Ok(r)
            c == 401 -> ApiResult.Fail(ApiError.INVALID_KEY, c)
            c == 429 -> ApiResult.Fail(ApiError.RATE_LIMITED, c)
            c == 403 -> {
                val limited = r.rateRemaining == "0" ||
                    r.body.toString(Charsets.UTF_8).lowercase().contains("rate limit")
                ApiResult.Fail(if (limited) ApiError.RATE_LIMITED else ApiError.INVALID_KEY, c)
            }
            c == 404 -> ApiResult.Fail(ApiError.NOT_FOUND, c)
            c >= 500 -> ApiResult.Fail(ApiError.SERVER, c)
            else -> ApiResult.Fail(ApiError.BAD_RESPONSE, c)
        }
    }

    private inline fun <reified T> decode(body: String): ApiResult<T> = try {
        ApiResult.Ok(json.decodeFromString<T>(body))
    } catch (e: SerializationException) {
        ApiResult.Fail(ApiError.BAD_RESPONSE)
    } catch (e: IllegalArgumentException) {
        ApiResult.Fail(ApiError.BAD_RESPONSE)
    }

    private inline fun <reified T> decodeList(body: String): ApiResult<List<T>> = try {
        ApiResult.Ok(json.decodeFromString(ListSerializer(kotlinx.serialization.serializer<T>()), body))
    } catch (e: SerializationException) {
        ApiResult.Fail(ApiError.BAD_RESPONSE)
    } catch (e: IllegalArgumentException) {
        ApiResult.Fail(ApiError.BAD_RESPONSE)
    }

    private inline fun <T, R> ApiResult<T>.flatMap(f: (T) -> ApiResult<R>): ApiResult<R> = when (this) {
        is ApiResult.Ok -> f(value)
        is ApiResult.Fail -> this
    }

    private inline fun <T, R> ApiResult<T>.map(f: (T) -> R): ApiResult<R> = when (this) {
        is ApiResult.Ok -> ApiResult.Ok(f(value))
        is ApiResult.Fail -> this
    }

    private inline fun <T> ApiResult<T>.getOrElse(onFail: (ApiResult.Fail) -> Nothing): T = when (this) {
        is ApiResult.Ok -> value
        is ApiResult.Fail -> onFail(this)
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) cont.resume(response) else response.close()
                }
            },
        )
    }

    companion object {
        const val API_VERSION = "2022-11-28"
        private const val USER_AGENT = "KriptonCod-Android"
        private const val PAGE_SIZE = 100
        private const val MAX_JSON_BYTES = Long.MAX_VALUE
        private const val MAX_LOG_BYTES = Long.MAX_VALUE
        const val DEFAULT_WAIT_MS = 15L * 60 * 1000
        const val DEFAULT_POLL_MS = 10_000L
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val REPO_REGEX = Regex("[A-Za-z0-9._-]{1,100}/[A-Za-z0-9._-]{1,100}")
        private val BRANCH_REGEX = Regex("[A-Za-z0-9._/-]{1,200}")

        /** Her çalıştırma için sabit dal adı. */
        fun runBranch(runId: Long): String = "kriptoncod/run-$runId"

        internal fun isSafeRepo(repo: String): Boolean = REPO_REGEX.matches(repo)

        internal fun isSafeBranch(branch: String): Boolean =
            BRANCH_REGEX.matches(branch) && ".." !in branch && "//" !in branch &&
                !branch.startsWith("/") && !branch.endsWith("/") && !branch.endsWith(".lock")

        internal fun isSafePath(path: String): Boolean =
            path.isNotEmpty() && path.length <= 240 && !path.startsWith("/") && '\\' !in path &&
                path.none { it.code < 0x20 } &&
                path.split('/').none { it.isEmpty() || it == "." || it == ".." }

        private fun requireRepo(repo: String) = require(isSafeRepo(repo)) { "Geçersiz repo adı" }

        private fun requireBranch(branch: String) = require(isSafeBranch(branch)) { "Geçersiz dal adı" }

        /** Yönlendirme kapalı: token başka bir adrese sızdırılmaz. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
