package com.kriptoncod.app.data.ai

import com.kriptoncod.app.data.ai.AiGenerationException.Kind
import com.kriptoncod.app.data.providers.ApiResult
import com.kriptoncod.app.data.providers.ModelService
import com.kriptoncod.app.data.providers.ProxyProvider
import com.kriptoncod.app.data.providers.UpstreamProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Proxy hesabı uçtan uca: gerçek HTTP (MockWebServer) → normalizer → yeniden deneme. */
class ProxyGenerationTest {
    private lateinit var server: MockWebServer
    private lateinit var other: MockWebServer
    private val client = OpenAiCompatibleGenerator.defaultClient().newBuilder()
        .readTimeout(1, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build()
    private val generator = RetryingAiGenerator(OpenAiCompatibleGenerator(client), backoff = {})

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        other = MockWebServer().apply { start() }
    }

    @After fun tearDown() {
        runCatching { server.shutdown() }
        runCatching { other.shutdown() }
    }

    private fun endpoint(s: MockWebServer, upstream: UpstreamProvider) =
        ProxyProvider.endpoint("http://127.0.0.1:${s.port}", upstream)!!

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun reply(message: String, finish: String = "stop") =
        json("""{"choices":[{"index":0,"message":$message,"finish_reason":"$finish"}]}""")

    private fun plain(content: String, finish: String = "stop") = reply("""{"role":"assistant","content":"$content"}""", finish)

    private fun generate(s: MockWebServer, upstream: UpstreamProvider, model: String, key: String = "proxy-key") =
        runBlocking { generator.generate(endpoint(s, upstream), model, key, "Yaz") }

    private fun failure(block: () -> Unit): AiGenerationException =
        runCatching(block).exceptionOrNull() as AiGenerationException

    @Test fun sendsOpenAiRequestToProxyWithBearerKey() {
        server.enqueue(plain("merhaba"))
        assertEquals("merhaba", generate(server, UpstreamProvider.CLAUDE, "claude-sonnet-5-5"))
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/chat/completions", req.path)
        assertEquals("Bearer proxy-key", req.getHeader("Authorization"))
        assertTrue(req.body.readUtf8().contains("\"model\":\"claude-sonnet-5-5\""))
    }

    @Test fun claudeThinkingPartsAreDropped() {
        server.enqueue(reply("""{"content":[{"type":"thinking","thinking":"gizli"},{"type":"text","text":"Sonuç"}]}"""))
        assertEquals("Sonuç", generate(server, UpstreamProvider.CLAUDE, "claude-opus-5-5"))
    }

    @Test fun geminiMaxTokensIsRetriedThenSucceeds() {
        server.enqueue(plain("yarım", "MAX_TOKENS"))
        server.enqueue(plain("tam"))
        assertEquals("tam", generate(server, UpstreamProvider.GEMINI, "gemini-3.8-flash"))
        assertEquals(2, server.requestCount)
        server.takeRequest()
        assertTrue(server.takeRequest().body.readUtf8().contains("Not:"))
    }

    @Test fun deepSeekReasoningContentIsNotPartOfTheAnswer() {
        server.enqueue(reply("""{"content":"Sonuç","reasoning_content":"uzun muhakeme"}"""))
        assertEquals("Sonuç", generate(server, UpstreamProvider.DEEPSEEK, "deepseek-v4"))
    }

    @Test fun kimiThinkAndSpecialTokensAreCleaned() {
        server.enqueue(plain("<think>x</think>Sonuç<|im_end|>"))
        assertEquals("Sonuç", generate(server, UpstreamProvider.KIMI, "kimi-k3"))
    }

    @Test fun emptyOrBrokenBodiesAreRetried() {
        server.enqueue(reply("""{"content":null}"""))
        server.enqueue(json("""{"error":{"message":"upstream hiccup"}}"""))
        server.enqueue(plain("tamam"))
        assertEquals("tamam", generate(server, UpstreamProvider.OPENAI, "gpt-6-luna"))
        assertEquals(3, server.requestCount)
    }

    @Test fun unclosedCodeBlockIsRetried() {
        server.enqueue(plain("```a.txt\\nyarım"))
        server.enqueue(plain("```a.txt\\ntam\\n```"))
        assertEquals("```a.txt\ntam\n```", generate(server, UpstreamProvider.OPENAI, "gpt-6-astra"))
        assertEquals(2, server.requestCount)
    }

    @Test fun persistentTruncationFailsWithoutReturningPartialCode() {
        repeat(3) { server.enqueue(plain("yarım", "length")) }
        assertEquals(Kind.TRUNCATED, failure { generate(server, UpstreamProvider.OPENAI, "gpt-6-astra") }.kind)
        assertEquals(3, server.requestCount)
    }

    @Test fun wrongProxyKeyIsUnauthorizedAndNotRetried() {
        server.enqueue(json("""{"error":"invalid api key"}""", 401))
        assertEquals(Kind.UNAUTHORIZED, failure { generate(server, UpstreamProvider.CLAUDE, "claude-opus-5-5") }.kind)
        assertEquals(1, server.requestCount)
    }

    @Test fun twoAccountsOfTheSameProviderUseTheirOwnServerAndKey() {
        server.enqueue(plain("benimki"))
        other.enqueue(plain("arkadaşınki"))
        assertEquals("benimki", generate(server, UpstreamProvider.CLAUDE, "claude-sonnet-5-5", key = "my-key"))
        assertEquals("arkadaşınki", generate(other, UpstreamProvider.CLAUDE, "claude-sonnet-5-5", key = "friend-key"))
        assertEquals("Bearer my-key", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer friend-key", other.takeRequest().getHeader("Authorization"))
    }

    @Test fun modelDiscoveryReadsOwnerAndUsesOneRequest() = runBlocking {
        server.enqueue(
            json(
                """{"object":"list","data":[
                {"id":"claude-sonnet-5-5","object":"model","owned_by":"anthropic"},
                {"id":"gemini-3.8-flash","object":"model","owned_by":"google"},
                {"id":"grok-4.7","object":"model","owned_by":"xai"},
                {"id":"gpt-image-2","object":"model","owned_by":"openai"}]}""",
            ),
        )
        val result = ModelService(client).discover(endpoint(server, UpstreamProvider.CLAUDE), "proxy-key")
        val ranking = (result as ApiResult.Ok).value
        val byId = ranking.models.associate { it.model.id to it.model }
        assertTrue("görsel modeller elenmeli", "gpt-image-2" !in byId)
        assertEquals("xai", byId.getValue("grok-4.7").ownedBy)
        assertEquals(UpstreamProvider.OTHER, UpstreamProvider.detect("grok-4.7", "xai"))
        assertEquals(UpstreamProvider.CLAUDE, UpstreamProvider.detect("claude-sonnet-5-5", byId.getValue("claude-sonnet-5-5").ownedBy))
        assertEquals(1, server.requestCount)
        val req = server.takeRequest()
        assertEquals("/v1/models", req.path)
        assertEquals("Bearer proxy-key", req.getHeader("Authorization"))
    }
}
