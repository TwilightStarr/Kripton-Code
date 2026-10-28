package com.kriptoncod.app.data.serial

import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.github.GitHubFile
import com.kriptoncod.app.data.web.ReplyParser
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sayfa tarafı bozulduğunda (seçici/DOM) seri modun aynı mesajı tekrar tekrar göndermediğini doğrular. */
private class ScriptedWebSession(private val replies: ArrayDeque<WebReply>) : WebChatSession {
    val sent = mutableListOf<String>()
    override suspend fun send(prompt: String): WebReply {
        sent += prompt
        return replies.removeFirst()
    }

    override suspend fun download(url: String): WebFile? = null
    override suspend fun close() = Unit

    /** Sohbette okunabilir asistan yanıtı yok (seçici bozuk ya da yanıt gelmedi). */
    override suspend fun peekLast(): WebReply? = WebReply(WebReply.Status.OK, "")
}

private class ScriptedWebDriver(private val session: WebChatSession) : WebChatDriver {
    override suspend fun open(account: AiAccount, provider: WebProvider): WebChatSession = session
}

private class StubGit : SerialGit {
    override suspend fun defaultBranch(repo: String) = "main"
    override suspend fun snapshot(repo: String, branch: String): Map<String, ByteArray> = mapOf("README.md" to "hi".toByteArray())
    override suspend fun commit(repo: String, branch: String, message: String, files: List<GitHubFile>) = "sha"
    override suspend fun awaitCi(repo: String, sha: String, shouldStop: () -> Boolean): CiResult = CiResult.Passed(emptyList())
}

class SerialWebFailureTest {

    private val account = AiAccount(
        id = 1,
        providerId = WebProvider.CHATGPT.id,
        displayName = "t",
        authType = AuthType.WEB_SESSION,
        modelId = "web",
        powerRank = 1,
        status = AccountStatus.READY,
    )

    private fun newRun(store: SerialStore) = store.create { id ->
        SerialState(
            id = id,
            createdAt = 0L,
            name = "t",
            repo = "o/r",
            branch = "main",
            accountId = 1,
            prompts = listOf(SerialPrompt("bir", "Birinci")),
        )
    }

    private fun runner(store: SerialStore, session: WebChatSession) = SerialRunner(store, { account }, ScriptedWebDriver(session), StubGit(), sleep = { }, errorDelayMs = { 0L })

    @Test
    fun message_sent_but_reply_unreadable_is_not_resent_blindly() = runBlocking {
        val store = SerialStore(Files.createTempDirectory("serial-test").toFile())
        val failure = WebReply(WebReply.Status.ERROR, reason = "not_started", sent = true, diag = "assistant: x=0")
        val session = ScriptedWebSession(ArrayDeque(listOf(failure, failure, failure)))
        val state = newRun(store)

        runner(store, session).run(state.id)

        assertEquals(SerialStatus.WAITING_USER, store.get(state.id)!!.status)
        assertEquals(1, session.sent.size)
        assertTrue(store.get(state.id)!!.log.any { it.contains("not_started") })
    }

    @Test
    fun repeated_page_failure_stops_after_three_attempts_with_reason_in_log() = runBlocking {
        val store = SerialStore(Files.createTempDirectory("serial-test").toFile())
        val failure = WebReply(WebReply.Status.ERROR, reason = "no_input", diag = "input: div.ProseMirror=0")
        val session = ScriptedWebSession(ArrayDeque(listOf(failure, failure, failure, failure)))
        val state = newRun(store)

        runner(store, session).run(state.id)

        val s = store.get(state.id)!!
        assertEquals(SerialStatus.WAITING_USER, s.status)
        assertEquals(SerialRunner.MAX_DOM_FAILURES, session.sent.size)
        assertTrue(s.log.any { it.contains("no_input") && it.contains("ProseMirror") })
    }

    @Test
    fun replyParserCarriesReasonSentAndDiag() {
        val raw = """{"status":"error","reason":"not_started","sent":true,"diag":"url=https://x","text":"","files":[]}"""
        val r = ReplyParser.parse(raw)
        assertEquals(WebReply.Status.ERROR, r.status)
        assertEquals("not_started", r.reason)
        assertTrue(r.sent)
        assertNotNull(r.diag)
    }
}
