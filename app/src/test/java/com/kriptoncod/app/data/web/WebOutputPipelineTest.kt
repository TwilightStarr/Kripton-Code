package com.kriptoncod.app.data.web

import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.run.CodeBlockParser
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AuthType
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class Sess(private val replies: ArrayDeque<WebReply>, private val files: Map<String, WebFile> = emptyMap()) : WebChatSession {
    val sent = mutableListOf<String>()
    override suspend fun send(prompt: String): WebReply { sent += prompt; return replies.removeFirst() }
    override suspend fun download(url: String): WebFile? = files[url]
    override suspend fun close() = Unit
}

private class Drv(val s: Sess) : WebChatDriver {
    override suspend fun open(account: AiAccount, provider: WebProvider): WebChatSession = s
}

private fun acct(p: WebProvider) = AiAccount(
    id = 1, providerId = p.id, displayName = "t", authType = AuthType.WEB_SESSION,
    modelId = "web", powerRank = 1, status = AccountStatus.READY,
)

private fun gen(s: Sess) = WebSessionGenerator(Drv(s), WebPacer(minGapMs = 0, jitterMs = 0), now = { 0L })
private fun ok(t: String) = WebReply(WebReply.Status.OK, t)

class WebOutputCheckTest {
    @Test fun pathlessBlocksAreRescuedNotDropped() {
        val c = WebOutputCheck.canonical("Açıklama\n```kotlin\nfun a() = 1\n```\n```src/B.kt\nval b = 2\n```")
        val paths = CodeBlockParser.parse(c).map { it.path }
        assertTrue(paths.contains("src/B.kt"))
        assertTrue(paths.any { it.startsWith("unnamed/part_") && it.endsWith(".kt") })
    }

    @Test fun completeVersionWinsOverPlaceholderVersion() {
        val c = WebOutputCheck.canonical("```a.kt\nfun a() {\n    // ...\n}\n```\n```a.kt\nfun a() { println(1) }\n```")
        assertEquals("fun a() { println(1) }", CodeBlockParser.parse(c).single().content)
    }

    @Test fun inspectFlagsPlaceholdersAndMissingBlocks() {
        assertFalse(WebOutputCheck.inspect("```a.kt\nfun a() {\n    // rest of the code\n}\n```").ok)
        assertTrue(WebOutputCheck.inspect("sadece metin").noBlocks)
        assertTrue(WebOutputCheck.inspect("```a.kt\nval x = 1\n```").ok)
    }
}

class WebPipelineTest {
    @Test fun placeholderReplyTriggersRepairTurnInSameChat() = runBlocking {
        val s = Sess(
            ArrayDeque(
                listOf(
                    ok("```src/A.kt\nfun a() {\n    // ...\n}\n```"),
                    ok("```src/A.kt\nfun a() { println(1) }\n```"),
                ),
            ),
        )
        val out = gen(s).generate(acct(WebProvider.KIMI), "yaz")
        assertEquals(2, s.sent.size)
        assertTrue(s.sent[1].contains("src/A.kt"))
        assertEquals("fun a() { println(1) }", CodeBlockParser.parse(out).single().content)
    }

    @Test fun repairTurnsAreBounded() = runBlocking {
        val bad = ok("```A.kt\nfun a() {\n    // ...\n}\n```")
        val s = Sess(ArrayDeque(listOf(bad, bad, bad, bad, bad, bad)))
        gen(s).generate(acct(WebProvider.KIMI), "yaz")
        assertEquals(1 + WebOutputPolicy.forProvider(WebProvider.KIMI).repairTurns, s.sent.size)
    }

    @Test fun kimiThinkingTagsAreStripped() = runBlocking {
        val s = Sess(ArrayDeque(listOf(ok("<think>düşünüyorum</think>\n```a/B.kt\nval x = 1\n```"))))
        val out = gen(s).generate(acct(WebProvider.KIMI), "yaz")
        assertFalse(out.contains("think"))
        assertEquals("a/B.kt", CodeBlockParser.parse(out).single().path)
    }

    @Test fun zipBinariesAndRawZipArePassedAsAssets() = runBlocking {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("app/Main.kt")); z.write("fun main() {}".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("assets/logo.png")); z.write(byteArrayOf(0, 1, 2, -1, -2)); z.closeEntry()
        }
        val s = Sess(
            ArrayDeque(listOf(WebReply(WebReply.Status.OK, "ok", listOf("https://claude.ai/f.zip")))),
            mapOf("https://claude.ai/f.zip" to WebFile("proje.zip", bos.toByteArray())),
        )
        val assets = mutableListOf<WebAsset>()
        val out = gen(s).generate(acct(WebProvider.CLAUDE), "yaz", onAsset = { assets += it })
        assertEquals("app/Main.kt", CodeBlockParser.parse(out).single().path)
        assertTrue(assets.any { it.path == "assets/logo.png" })
        assertTrue(assets.any { it.path == "_kripton/raw/proje.zip" })
    }
}
