package com.kriptoncod.app.data.web

import com.kriptoncod.app.data.ai.AiGenerationException
import com.kriptoncod.app.data.ai.AiGenerationException.Kind
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.run.CodeBlockParser
import com.kriptoncod.app.domain.AuthType
import java.io.ByteArrayOutputStream
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
    val bos = ByteArrayOutputStream()
    ZipOutputStream(bos).use { z ->
        for ((n, b) in entries) {
            z.putNextEntry(ZipEntry(n))
            z.write(b)
            z.closeEntry()
        }
    }
    return bos.toByteArray()
}

private fun account(provider: WebProvider, id: Long = 1) = AiAccount(
    id = id,
    providerId = provider.id,
    displayName = "t",
    authType = AuthType.WEB_SESSION,
    modelId = "web",
    powerRank = provider.powerRank,
)

class LimitParserTest {
    private val now = 0L

    @Test fun relativeEnglish() {
        val ms = LimitParser.waitMs("You can try again in 2 hours 15 minutes", now, ZoneOffset.UTC)
        assertEquals((2 * 60 + 15 + 1) * 60_000L, ms)
    }

    @Test fun relativeTurkish() {
        val ms = LimitParser.waitMs("2 saat 10 dakika sonra tekrar dene", now, ZoneOffset.UTC)
        assertEquals((2 * 60 + 10 + 1) * 60_000L, ms)
    }

    @Test fun absoluteClockPm() {
        // 00:00 UTC'de "resets at 3 PM" -> 15 saat + 1 dk
        val ms = LimitParser.waitMs("Your limit resets at 3 PM", now, ZoneOffset.UTC)
        assertEquals((15 * 60 + 1) * 60_000L, ms)
    }

    @Test fun absoluteClockWrapsToTomorrow() {
        val noon = 12 * 3_600_000L
        val ms = LimitParser.waitMs("available again at 9:30 am", noon, ZoneOffset.UTC)
        assertEquals(((21 * 60 + 30) + 1) * 60_000L, ms)
    }

    @Test fun unknownFallsBackToDefaultAndHasNoUpperCap() {
        assertEquals(LimitParser.DEFAULT_WAIT_MS + 60_000L, LimitParser.waitMs("slow down", now, ZoneOffset.UTC))
        assertEquals(5 * 24L * 3_600_000L + 60_000L, LimitParser.waitMs("try again in 5 days", now, ZoneOffset.UTC))
        assertEquals(65_000L, LimitParser.waitMs("try again in 5 seconds", now, ZoneOffset.UTC))
    }

    @Test fun detectsLimitPhrases() {
        assertTrue(LimitParser.looksLikeLimit("You've reached your usage limit"))
        assertTrue(LimitParser.looksLikeLimit("Çok fazla istek gönderdin"))
        assertTrue(LimitParser.looksLikeLimit("请求过于频繁"))
        assertFalse(LimitParser.looksLikeLimit("fun main() {}"))
        assertFalse(LimitParser.looksLikeLimit(null))
    }
}

class ZipBlocksTest {
    @Test fun convertsTextEntriesAndRoundTripsThroughParser() {
        val readme = "# T\n```kotlin\nfun a() {}\n```\n"
        val z = zipOf(
            "src/A.kt" to "fun a() = 1\n".toByteArray(),
            "docs/README.md" to readme.toByteArray(),
            "bin/x.dat" to byteArrayOf(1, 0, 2),
            "../evil.txt" to "x".toByteArray(),
            "__MACOSX/._A.kt" to "x".toByteArray(),
        )
        val r = ZipBlocks.convert(WebFile("out.zip", z))
        val blocks = CodeBlockParser.parse(r.text)
        assertEquals(listOf("src/A.kt", "docs/README.md"), blocks.map { it.path })
        assertEquals("fun a() = 1", blocks[0].content)
        assertEquals(readme.trimEnd(), blocks[1].content) // iç içe çitler korunur
        assertTrue("bin/x.dat" in r.skipped)
        assertTrue("../evil.txt" in r.skipped)
    }

    @Test fun singleFileAndInvalidInput() {
        val r = ZipBlocks.convert(WebFile("Main.kt", "val x = 1".toByteArray()))
        assertEquals("```Main.kt\nval x = 1\n```", r.text)
        assertEquals("", ZipBlocks.convert(WebFile("a b.kt", "x".toByteArray())).text)
        assertEquals("", ZipBlocks.convert(WebFile("a.bin", byteArrayOf(0, 1))).text)
    }
}

class ContinuationMergerTest {
    @Test fun continuesInsideOpenFence() {
        val merged = ContinuationMerger.merge(listOf("```src/A.kt\nfun a() {\n", "    println(1)\n}\n```"))
        val b = CodeBlockParser.parse(merged)
        assertEquals(1, b.size)
        assertTrue(b[0].content.contains("println(1)"))
        assertFalse(ContinuationMerger.endsInsideFence(merged))
    }

    @Test fun restartedFenceClosesTruncatedBlockAndLaterBlockWins() {
        val merged = ContinuationMerger.merge(
            listOf("```src/A.kt\nfun a() {\n", "```src/A.kt\nfun a() = 2\n```\n```src/B.kt\nval b = 1\n```"),
        )
        assertFalse(ContinuationMerger.endsInsideFence(merged))
        val last = CodeBlockParser.parse(merged).associate { it.path to it.content }
        assertEquals("fun a() = 2", last["src/A.kt"])
        assertEquals("val b = 1", last["src/B.kt"])
    }

    @Test fun closedPartsJoinWithBlankLine() {
        val merged = ContinuationMerger.merge(listOf("```a.kt\nx\n```", "```b.kt\ny\n```"))
        assertEquals(2, CodeBlockParser.parse(merged).size)
        assertEquals("", ContinuationMerger.merge(listOf("", "  ")))
    }
}

class ReplyParserTest {
    @Test fun parsesQuotedAndPlain() {
        val plain = """{"status":"ok","text":"hi","files":["https://a/b.zip","https://a/b.zip"],"truncated":true}"""
        val r1 = ReplyParser.parse(plain)
        assertEquals(WebReply.Status.OK, r1.status)
        assertEquals(listOf("https://a/b.zip"), r1.fileUrls)
        assertTrue(r1.truncated)
        val quoted = "\"" + plain.replace("\"", "\\\"") + "\""
        assertEquals("hi", ReplyParser.parse(quoted).text)
        val lim = ReplyParser.parse("""{"status":"rate_limited","limit":"try again in 1 hour"}""")
        assertEquals(WebReply.Status.RATE_LIMITED, lim.status)
        assertEquals("try again in 1 hour", lim.limitText)
        assertEquals(WebReply.Status.NEEDS_LOGIN, ReplyParser.parse("""{"status":"needs_login"}""").status)
    }

    @Test fun garbageIsError() {
        assertEquals(WebReply.Status.ERROR, ReplyParser.parse(null).status)
        assertEquals(WebReply.Status.ERROR, ReplyParser.parse("null").status)
        assertEquals(WebReply.Status.ERROR, ReplyParser.parse("not json").status)
    }
}

class WebPacerTest {
    @Test fun enforcesGapPerAccount() = runBlocking {
        var t = 1_000L
        val slept = mutableListOf<Long>()
        val pacer = WebPacer(minGapMs = 20_000, jitterMs = 0, now = { t }, sleep = {
            slept += it
            t += it
        })
        pacer.await(1) // ilk gönderim: bekleme yok
        t += 5_000
        pacer.await(1) // 5 sn geçti -> 15 sn daha
        pacer.await(2) // başka hesap: bekleme yok
        assertEquals(listOf(15_000L), slept)
    }
}

private class FakeSession(private val replies: ArrayDeque<WebReply>, private val files: Map<String, WebFile> = emptyMap()) : WebChatSession {
    val sent = mutableListOf<String>()
    var closed = false
    override suspend fun send(prompt: String): WebReply {
        sent += prompt
        return replies.removeFirst()
    }
    override suspend fun download(url: String): WebFile? = files[url]
    override suspend fun close() {
        closed = true
    }
}

private class FakeDriver(val session: FakeSession? = null, val error: Throwable? = null) : WebChatDriver {
    override suspend fun open(account: AiAccount, provider: WebProvider): WebChatSession {
        error?.let { throw it }
        return session!!
    }
}

private fun gen(driver: WebChatDriver, max: Int = 3) = WebSessionGenerator(driver, WebPacer(minGapMs = 0, jitterMs = 0), now = { 0L }, maxContinues = max)

private fun ok(text: String, files: List<String> = emptyList(), truncated: Boolean = false) = WebReply(WebReply.Status.OK, text, files, truncated = truncated)

class WebSessionGeneratorTest {

    @Test fun claudeZipIsDownloadedAndConvertedToBlocks() = runBlocking {
        val z = zipOf("app/Main.kt" to "fun main() {}".toByteArray())
        val s = FakeSession(ArrayDeque(listOf(ok("done", listOf("https://claude.ai/f.zip")))), mapOf("https://claude.ai/f.zip" to WebFile("f.zip", z)))
        val out = gen(FakeDriver(s)).generate(account(WebProvider.CLAUDE), "yaz")
        assertEquals("app/Main.kt", CodeBlockParser.parse(out).single().path)
        assertTrue(s.sent.single().contains(".zip"))
        assertTrue(s.closed)
    }

    @Test fun zipFlowFallsBackToCodeBlocksInSameChat() = runBlocking {
        val s = FakeSession(ArrayDeque(listOf(ok("I cannot create files"), ok("```a/B.kt\nval x=1\n```"))))
        val out = gen(FakeDriver(s)).generate(account(WebProvider.CHATGPT), "yaz")
        assertEquals("a/B.kt", CodeBlockParser.parse(out).single().path)
        assertEquals(2, s.sent.size)
        assertTrue(s.sent[1].contains("fenced code block"))
    }

    @Test fun kimiContinuesTruncatedAnswerAndMerges() = runBlocking {
        val s = FakeSession(ArrayDeque(listOf(ok("```src/A.kt\nfun a() {\n"), ok("  println(1)\n}\n```"))))
        val out = gen(FakeDriver(s)).generate(account(WebProvider.KIMI), "yaz")
        val b = CodeBlockParser.parse(out).single()
        assertEquals("src/A.kt", b.path)
        assertTrue(b.content.contains("println(1)"))
        assertEquals(2, s.sent.size)
        assertTrue(s.sent[1].startsWith("Continue exactly"))
    }

    @Test fun kimiStillTruncatedAfterMaxContinuesIsTruncatedError() = runBlocking {
        val open = ok("```src/A.kt\nline\n")
        val s = FakeSession(ArrayDeque(listOf(open, open, open, open)))
        try {
            gen(FakeDriver(s), max = 2).generate(account(WebProvider.KIMI), "yaz")
            fail("TRUNCATED bekleniyordu")
        } catch (e: AiGenerationException) {
            assertEquals(Kind.TRUNCATED, e.kind)
        }
        assertEquals(3, s.sent.size) // ilk + 2 devam
    }

    @Test fun rateLimitCarriesWaitFromPageText() = runBlocking {
        val s = FakeSession(ArrayDeque(listOf(WebReply(WebReply.Status.RATE_LIMITED, limitText = "try again in 3 hours"))))
        try {
            gen(FakeDriver(s)).generate(account(WebProvider.CLAUDE), "yaz")
            fail()
        } catch (e: AiGenerationException) {
            assertEquals(Kind.RATE_LIMITED, e.kind)
            assertEquals((3 * 60 + 1) * 60_000L, e.retryAfterMs)
            assertEquals((3 * 60 + 1) * 60_000L, e.waitUntil(0L))
        }
        assertTrue(s.closed)
    }

    @Test fun limitMessageInAnswerBodyIsDetected() = runBlocking {
        val s = FakeSession(ArrayDeque(listOf(ok("You've reached your usage limit. Try again in 45 minutes."))))
        try {
            gen(FakeDriver(s)).generate(account(WebProvider.CHATGPT), "yaz")
            fail()
        } catch (e: AiGenerationException) {
            assertEquals(Kind.RATE_LIMITED, e.kind)
        }
    }

    @Test fun statusMapping() = runBlocking {
        suspend fun kindFor(r: WebReply): Kind {
            val s = FakeSession(ArrayDeque(listOf(r)))
            try {
                gen(FakeDriver(s)).generate(account(WebProvider.CLAUDE), "x")
                fail()
            } catch (e: AiGenerationException) {
                return e.kind
            }
            error("unreachable")
        }
        assertEquals(Kind.UNAUTHORIZED, kindFor(WebReply(WebReply.Status.NEEDS_LOGIN)))
        assertEquals(Kind.NETWORK, kindFor(WebReply(WebReply.Status.TIMEOUT)))
        assertEquals(Kind.NETWORK, kindFor(WebReply(WebReply.Status.ERROR)))
    }

    @Test fun missingSessionFromDriverPropagates() = runBlocking {
        try {
            gen(FakeDriver(error = AiGenerationException(Kind.UNAUTHORIZED))).generate(account(WebProvider.KIMI), "x")
            fail()
        } catch (e: AiGenerationException) {
            assertEquals(Kind.UNAUTHORIZED, e.kind)
        }
    }

    @Test fun planPromptsAreNotWrapped() = runBlocking {
        val s = FakeSession(ArrayDeque(listOf(ok("## KATALOG: a\n### PROMPT: p\nx"))))
        val out = gen(FakeDriver(s)).generate(account(WebProvider.CLAUDE), "plan", expectFiles = false)
        assertEquals("plan", s.sent.single())
        assertTrue(out.startsWith("## KATALOG"))
    }

    @Test fun geminiNeverUsesTheWebChat() = runBlocking {
        try {
            gen(FakeDriver(FakeSession(ArrayDeque()))).generate(account(WebProvider.GEMINI), "x")
            fail()
        } catch (e: AiGenerationException) {
            assertEquals(Kind.BAD_REQUEST, e.kind)
        }
    }
}

class WebSessionStoreTest {
    private class MemVault : com.kriptoncod.app.data.vault.KeyVault {
        val m = mutableMapOf<String, String>()
        override suspend fun put(keyId: String, secret: String) {
            m[keyId] = secret
        }
        override suspend fun get(keyId: String) = m[keyId]
        override suspend fun contains(keyId: String) = keyId in m
        override suspend fun delete(keyId: String) {
            m.remove(keyId)
        }
        override suspend fun listIds() = m.keys.sorted()
    }

    @Test fun roundTripAndClear() = runBlocking {
        val store = WebSessionStore(MemVault(), now = { 42L })
        assertNull(store.load(7))
        store.save(7, WebSession(cookies = mapOf("https://claude.ai" to "a=b; c=d"), localStorage = mapOf("k" to "v")))
        val s = store.load(7)!!
        assertEquals(42L, s.savedAtMs)
        assertEquals("a=b; c=d", s.cookies["https://claude.ai"])
        assertFalse(s.isEmpty)
        store.clear(7)
        assertFalse(store.has(7))
        assertTrue(WebSession().isEmpty)
    }

    @Test fun cookieTextParsing() {
        assertEquals(listOf("a" to "b", "c" to "d=e"), CookieText.parse(" a=b; c=d=e; bad; =x"))
    }
}

class WebSelectorsTest {
    @Test fun everyWebProviderHasDefaultsAndJsonRoundTrips() {
        WebProvider.entries.filter { it.output != OutputStyle.API_KEY }.forEach {
            assertTrue(it.id, WebSelectors.defaults(it).input.isNotEmpty())
        }
        assertTrue(WebSelectors.defaultsJson().contains("\"claude\""))
    }

    @Test fun overrideFileReplacesOnlyThatProvider() {
        val f = java.io.File.createTempFile("sel", ".json")
        f.writeText("""{"kimi":{"input":["#x"],"send":["#s"],"stop":["#t"],"assistant":["#a"]}}""")
        assertEquals(listOf("#x"), WebSelectors.load(WebProvider.KIMI, f).input)
        assertEquals(WebSelectors.defaults(WebProvider.CLAUDE), WebSelectors.load(WebProvider.CLAUDE, f))
        f.writeText("garbage")
        assertEquals(WebSelectors.defaults(WebProvider.KIMI), WebSelectors.load(WebProvider.KIMI, f))
        f.delete()
    }
}
