package com.kriptoncod.app.data.serial

import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.github.GitHubFile
import com.kriptoncod.app.data.web.WebChatDriver
import com.kriptoncod.app.data.web.WebChatSession
import com.kriptoncod.app.data.web.WebFile
import com.kriptoncod.app.data.web.WebProvider
import com.kriptoncod.app.data.web.WebReply
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AuthType
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSession : WebChatSession {
    val sent = mutableListOf<String>()
    var attached = 0
    private var counter = 0

    override suspend fun send(prompt: String): WebReply {
        sent += prompt
        counter++
        val nonce = Regex("KRIPTON-BASLA-([A-Z0-9]+)").find(prompt)!!.groupValues[1]
        val text = "KRIPTON-BASLA-$nonce\n```src/File$counter.kt\nclass File$counter\n```\nKRIPTON-TAMAM-$nonce"
        return WebReply(WebReply.Status.OK, text)
    }

    override suspend fun download(url: String): WebFile? = null
    override suspend fun close() = Unit
    override suspend fun chatUrl(): String? = "https://chatgpt.com/c/test-chat"
    override suspend fun attach(name: String, bytes: ByteArray): Boolean {
        attached++
        return true
    }
}

private class FakeDriver(val session: FakeSession) : WebChatDriver {
    var opens = 0
    var resumes = 0
    override suspend fun open(account: AiAccount, provider: WebProvider): WebChatSession {
        opens++
        return session
    }

    override suspend fun resume(account: AiAccount, provider: WebProvider, chatUrl: String?): WebChatSession {
        resumes++
        return session
    }
}

private class FakeGit(private val ci: ArrayDeque<CiResult>) : SerialGit {
    val commits = mutableListOf<List<String>>()
    override suspend fun defaultBranch(repo: String) = "main"
    override suspend fun snapshot(repo: String, branch: String): Map<String, ByteArray> = mapOf("README.md" to "hi".toByteArray())
    override suspend fun commit(repo: String, branch: String, message: String, files: List<GitHubFile>): String {
        commits += files.map { it.path }
        return "sha${commits.size}abcdef"
    }

    override suspend fun awaitCi(repo: String, sha: String, shouldStop: () -> Boolean): CiResult = ci.removeFirst()
}

class SerialRunnerTest {

    private val account = AiAccount(
        id = 1,
        providerId = WebProvider.CHATGPT.id,
        displayName = "t",
        authType = AuthType.WEB_SESSION,
        modelId = "web",
        powerRank = 1,
        status = AccountStatus.READY,
    )

    @Test
    fun two_prompts_one_chat_with_a_ci_fix_in_the_same_chat() = runBlocking {
        val store = SerialStore(Files.createTempDirectory("serial-test").toFile())
        val session = FakeSession()
        val driver = FakeDriver(session)
        val git = FakeGit(ArrayDeque(listOf(CiResult.Failed(listOf("[b] e: boom"), emptyList()), CiResult.Passed(emptyList()), CiResult.Passed(emptyList()))))
        val runner = SerialRunner(store, { account }, driver, git, sleep = { })
        val state = store.create { id ->
            SerialState(
                id = id,
                createdAt = 0L,
                name = "t",
                repo = "o/r",
                branch = "main",
                accountId = 1,
                prompts = listOf(SerialPrompt("bir", "Birinci"), SerialPrompt("iki", "İkinci")),
            )
        }

        runner.run(state.id)

        val done = store.get(state.id)!!
        assertEquals(SerialStatus.DONE, done.status)
        // 1. prompt, CI düzeltmesi, 2. prompt: üç mesaj, üçü de AYNI sohbette.
        assertEquals(3, session.sent.size)
        assertTrue(session.sent[1].contains("BUILD LOG"))
        assertTrue(session.sent[1].contains("e: boom"))
        assertEquals(3, git.commits.size)
        // Proje yalnız ilk mesajda eklenir; yeni sohbet açılmaz.
        assertEquals(1, session.attached)
        assertEquals(1, driver.opens)
    }

    @Test
    fun new_chat_directive_opens_a_fresh_chat_with_the_project() = runBlocking {
        val store = SerialStore(Files.createTempDirectory("serial-test").toFile())
        val session = FakeSession()
        val driver = FakeDriver(session)
        val git = FakeGit(ArrayDeque(listOf(CiResult.Passed(emptyList()), CiResult.Passed(emptyList()))))
        val runner = SerialRunner(store, { account }, driver, git, sleep = { })
        val state = store.create { id ->
            SerialState(
                id = id,
                createdAt = 0L,
                name = "t",
                repo = "o/r",
                branch = "main",
                accountId = 1,
                prompts = listOf(SerialPrompt("bir", "Birinci"), SerialPrompt("iki", "İkinci", newChat = true)),
            )
        }

        runner.run(state.id)

        assertEquals(SerialStatus.DONE, store.get(state.id)!!.status)
        assertEquals(2, driver.opens)
        assertEquals(2, session.attached)
    }
}
