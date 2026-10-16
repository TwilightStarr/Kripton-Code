package com.kriptoncod.app.data.github

import com.kriptoncod.app.data.providers.ApiError
import com.kriptoncod.app.data.providers.ApiResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var service: GitHubService
    private val token = "ghp_" + "b".repeat(36)

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        service = GitHubService(
            client = GitHubService.defaultClient().newBuilder().callTimeout(5, TimeUnit.SECONDS).build(),
            apiBase = server.url("/"),
        )
    }

    @After fun tearDown() { runCatching { server.shutdown() } }

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun <T> ok(r: ApiResult<T>): T = (r as ApiResult.Ok).value

    private fun fail(r: ApiResult<*>): ApiResult.Fail = r as ApiResult.Fail

    // ---- repo listesi

    @Test fun listReposParsesAndFiltersAndSendsHeaders() = runBlocking {
        server.enqueue(
            json(
                """[
                {"full_name":"me/app","default_branch":"main","private":true,"permissions":{"push":true}},
                {"full_name":"me/old","default_branch":"master","archived":true,"permissions":{"push":true}},
                {"full_name":"org/readonly","default_branch":"main","permissions":{"push":false}},
                {"full_name":"org/noperm","default_branch":"dev"}
                ]""",
            ),
        )
        val repos = ok(service.listRepos(" $token "))
        assertEquals(listOf("me/app", "org/noperm"), repos.map { it.fullName })
        assertEquals("dev", repos[1].defaultBranch)
        assertTrue(repos[0].isPrivate)

        val req = server.takeRequest()
        assertEquals("Bearer $token", req.getHeader("Authorization"))
        assertEquals("application/vnd.github+json", req.getHeader("Accept"))
        assertEquals(GitHubService.API_VERSION, req.getHeader("X-GitHub-Api-Version"))
        assertTrue(req.path!!.startsWith("/user/repos?"))
    }

    @Test fun listReposFollowsPagesUntilShortPage() = runBlocking {
        val full = (1..100).joinToString(",", "[", "]") { """{"full_name":"me/r$it","permissions":{"push":true}}""" }
        server.enqueue(json(full))
        server.enqueue(json("""[{"full_name":"me/last","permissions":{"push":true}}]"""))
        assertEquals(101, ok(service.listRepos(token)).size)
        assertEquals(2, server.requestCount)
    }

    @Test fun malformedBodyIsBadResponse() = runBlocking {
        server.enqueue(json("not json"))
        assertEquals(ApiError.BAD_RESPONSE, fail(service.listRepos(token)).error)
    }

    // ---- hata ayrımı

    @Test fun errorCodesAreClassified() = runBlocking {
        server.enqueue(json("{}", 401))
        server.enqueue(json("""{"message":"Forbidden"}""", 403))
        server.enqueue(MockResponse().setResponseCode(403).setHeader("x-ratelimit-remaining", "0").setBody("{}"))
        server.enqueue(json("""{"message":"API rate limit exceeded"}""", 403))
        server.enqueue(json("{}", 429))
        server.enqueue(json("{}", 404))
        server.enqueue(json("{}", 502))
        assertEquals(ApiError.INVALID_KEY, fail(service.validateToken(token)).error)
        assertEquals(ApiError.INVALID_KEY, fail(service.validateToken(token)).error)
        assertEquals(ApiError.RATE_LIMITED, fail(service.validateToken(token)).error)
        assertEquals(ApiError.RATE_LIMITED, fail(service.validateToken(token)).error)
        assertEquals(ApiError.RATE_LIMITED, fail(service.validateToken(token)).error)
        assertEquals(ApiError.NOT_FOUND, fail(service.validateToken(token)).error)
        assertEquals(ApiError.SERVER, fail(service.validateToken(token)).error)
    }

    @Test fun blankTokenFailsWithoutNetwork() = runBlocking {
        assertEquals(ApiError.INVALID_KEY, fail(service.validateToken("  ")).error)
        assertEquals(0, server.requestCount)
    }

    @Test fun validateTokenReturnsLogin() = runBlocking {
        server.enqueue(json("""{"login":"octo","id":1}"""))
        assertEquals("octo", ok(service.validateToken(token)).login)
    }

    // ---- dal

    @Test fun defaultBranchReadsRepoDefaultBranch() = runBlocking {
        server.enqueue(json("""{"full_name":"me/app","default_branch":"trunk"}"""))
        assertEquals("trunk", ok(service.defaultBranch(token, "me/app")))
        assertEquals("/repos/me/app", server.takeRequest().path)
    }

    @Test fun createBranchUsesBaseShaAndReturnsNewSha() = runBlocking {
        server.enqueue(json("""{"ref":"refs/heads/main","object":{"sha":"base111"}}"""))
        server.enqueue(json("""{"ref":"refs/heads/kriptoncod/run-7","object":{"sha":"base111"}}""", 201))
        val sha = ok(service.createBranch(token, "me/app", GitHubService.runBranch(7), "main"))
        assertEquals("base111", sha)
        assertEquals("/repos/me/app/git/ref/heads/main", server.takeRequest().path)
        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("/repos/me/app/git/refs", post.path)
        val body = post.body.readUtf8()
        assertTrue(body.contains("refs/heads/kriptoncod/run-7"))
        assertTrue(body.contains("base111"))
    }

    @Test fun createBranchIsIdempotentWhenBranchExists() = runBlocking {
        server.enqueue(json("""{"object":{"sha":"base111"}}"""))
        server.enqueue(json("""{"message":"Reference already exists"}""", 422))
        server.enqueue(json("""{"object":{"sha":"existing222"}}"""))
        assertEquals("existing222", ok(service.createBranch(token, "me/app", "kriptoncod/run-7", "main")))
    }

    // ---- commit

    @Test fun commitFilesWritesSingleCommitViaGitDataApi() = runBlocking {
        server.enqueue(json("""{"object":{"sha":"head1"}}"""))
        server.enqueue(json("""{"sha":"head1","tree":{"sha":"tree1"}}"""))
        server.enqueue(json("""{"sha":"blobA"}""", 201))
        server.enqueue(json("""{"sha":"blobB"}""", 201))
        server.enqueue(json("""{"sha":"tree2"}""", 201))
        server.enqueue(json("""{"sha":"commit2"}""", 201))
        server.enqueue(json("""{"object":{"sha":"commit2"}}"""))

        val sha = ok(
            service.commitFiles(
                token, "me/app", "kriptoncod/run-7", "Run 7",
                listOf(GitHubFile("out/a.txt", "hello"), GitHubFile(VerifyWorkflow.PATH, VerifyWorkflow.YAML)),
            ),
        )
        assertEquals("commit2", sha)

        val paths = (1..7).map { server.takeRequest() }
        assertEquals("/repos/me/app/git/ref/heads/kriptoncod/run-7", paths[0].path)
        assertEquals("/repos/me/app/git/commits/head1", paths[1].path)
        assertEquals("/repos/me/app/git/blobs", paths[2].path)
        assertTrue(paths[2].body.readUtf8().contains("\"encoding\":\"base64\""))
        assertEquals("/repos/me/app/git/trees", paths[4].path)
        val tree = paths[4].body.readUtf8()
        assertTrue(tree.contains("tree1"))
        assertTrue(tree.contains("out/a.txt") && tree.contains("blobB"))
        val commit = paths[5].body.readUtf8()
        assertTrue(commit.contains("tree2") && commit.contains("head1"))
        assertEquals("PATCH", paths[6].method)
        assertEquals("/repos/me/app/git/refs/heads/kriptoncod/run-7", paths[6].path)
        assertTrue(paths[6].body.readUtf8().contains("\"force\":false"))
    }

    @Test fun commitRejectsFilesContainingTheToken() = runBlocking {
        val e = runCatching {
            service.commitFiles(token, "me/app", "kriptoncod/run-1", "m", listOf(GitHubFile("a.txt", "x $token y")))
        }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
        assertTrue(!e!!.message.orEmpty().contains(token))
        assertEquals(0, server.requestCount)
    }

    @Test fun unsafeInputsAreRejectedBeforeAnyRequest() = runBlocking {
        fun rejects(block: suspend () -> Unit) {
            assertTrue(runCatching { runBlocking { block() } }.exceptionOrNull() is IllegalArgumentException)
        }
        for (bad in listOf("../x", "/abs", "a//b", "a/./b", "a\\b", "")) {
            rejects { service.commitFiles(token, "me/app", "kriptoncod/run-1", "m", listOf(GitHubFile(bad, "x"))) }
        }
        rejects { service.findRun(token, "bad repo", "kriptoncod/run-1", "c2") }
        rejects { service.createBranch(token, "me/app", "a..b", "main") }
        assertEquals(0, server.requestCount)
    }

    // ---- Actions

    private fun runsJson(vararg runs: String) = """{"total_count":${runs.size},"workflow_runs":[${runs.joinToString(",")}]}"""

    private fun run(status: String, conclusion: String?, sha: String = "c2") =
        """{"id":99,"status":"$status","conclusion":${conclusion?.let { "\"$it\"" } ?: "null"},"head_sha":"$sha","html_url":"https://github.com/me/app/actions/runs/99"}"""

    @Test fun awaitRunWaitsForRunToAppearAndComplete() = runBlocking {
        server.enqueue(json(runsJson()))
        server.enqueue(json(runsJson(run("queued", null))))
        server.enqueue(json(runsJson(run("in_progress", null))))
        server.enqueue(json(runsJson(run("completed", "failure"))))
        val w = service.awaitRun(token, "me/app", "kriptoncod/run-7", "c2", timeoutMs = 5_000, pollMs = 10)
        val done = w as RunWait.Completed
        assertEquals(99L, done.run.id)
        assertEquals(CiOutcome.FAILURE, done.run.outcome)
        assertEquals(4, server.requestCount)
        val path = server.takeRequest().path!!
        assertTrue(path.contains("head_sha=c2") && path.contains("branch=kriptoncod"))
    }

    @Test fun awaitRunReportsSuccessAndIgnoresOtherCommits() = runBlocking {
        server.enqueue(json(runsJson(run("completed", "success", sha = "other"))))
        server.enqueue(json(runsJson(run("completed", "success", sha = "c2"))))
        val w = service.awaitRun(token, "me/app", "kriptoncod/run-7", "c2", timeoutMs = 5_000, pollMs = 10)
        assertEquals(CiOutcome.SUCCESS, (w as RunWait.Completed).run.outcome)
    }

    @Test fun cancelledOrTimedOutConclusionCountsAsFailure() {
        assertEquals(CiOutcome.FAILURE, GitHubRun(1, "completed", "cancelled", "c", "u").outcome)
        assertEquals(CiOutcome.FAILURE, GitHubRun(1, "completed", "timed_out", "c", "u").outcome)
        assertNull(GitHubRun(1, "in_progress", null, "c", "u").outcome)
    }

    @Test fun awaitRunTimesOutWithLastSeenRun() = runBlocking {
        repeat(200) { server.enqueue(json(runsJson(run("in_progress", null)))) }
        val w = service.awaitRun(token, "me/app", "kriptoncod/run-7", "c2", timeoutMs = 150, pollMs = 20)
        val t = w as RunWait.TimedOut
        assertEquals("in_progress", t.lastRun?.status)
    }

    @Test fun awaitRunStopsOnApiFailure() = runBlocking {
        server.enqueue(json("{}", 401))
        val w = service.awaitRun(token, "me/app", "kriptoncod/run-7", "c2", timeoutMs = 5_000, pollMs = 10)
        assertEquals(ApiError.INVALID_KEY, (w as RunWait.Failed).failure.error)
    }

    // ---- log indirme

    @Test fun downloadLogsFollowsRedirectWithoutSendingToken() = runBlocking {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/blob/logs.zip").toString()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(bytes)))
        val got = ok(service.downloadRunLogs(token, "me/app", 99))
        assertTrue(bytes.contentEquals(got))

        val first = server.takeRequest()
        assertEquals("/repos/me/app/actions/runs/99/logs", first.path)
        assertEquals("Bearer $token", first.getHeader("Authorization"))
        val second = server.takeRequest()
        assertEquals("/blob/logs.zip", second.path)
        assertNull(second.getHeader("Authorization"))
    }

    @Test fun downloadLogsWithoutLocationIsBadResponse() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302))
        assertEquals(ApiError.BAD_RESPONSE, fail(service.downloadRunLogs(token, "me/app", 99)).error)
    }

    @Test fun expiredLogsAreBadResponse() = runBlocking {
        server.enqueue(json("""{"message":"Logs expired"}""", 410))
        assertEquals(ApiError.BAD_RESPONSE, fail(service.downloadRunLogs(token, "me/app", 99)).error)
    }
}
