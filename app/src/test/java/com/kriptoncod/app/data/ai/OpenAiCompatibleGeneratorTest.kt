package com.kriptoncod.app.data.ai

import com.kriptoncod.app.data.ai.AiGenerationException.Kind
import com.kriptoncod.app.data.providers.ApiKey
import com.kriptoncod.app.data.providers.ProviderEndpoint
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiCompatibleGeneratorTest {
    private lateinit var server: MockWebServer
    private lateinit var generator: OpenAiCompatibleGenerator
    private val key = "sk-test-" + "k".repeat(24)

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        generator = OpenAiCompatibleGenerator(
            OpenAiCompatibleGenerator.defaultClient().newBuilder()
                .readTimeout(500, TimeUnit.MILLISECONDS)
                .callTimeout(3, TimeUnit.SECONDS)
                .build(),
            maxContinuations = 0,
        )
    }

    @After fun tearDown() { runCatching { server.shutdown() } }

    private fun endpoint(): ProviderEndpoint {
        val base = server.url("/v1")
        return ProviderEndpoint(
            providerId = "test",
            baseUrl = base,
            modelsUrl = base.newBuilder().addPathSegment("models").build(),
            chatUrl = base.newBuilder().addPathSegments("chat/completions").build(),
            validationUrl = base.newBuilder().addPathSegment("models").build(),
            authMethod = ApiKey(),
        )
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun reply(content: String, finish: String = "stop") =
        json("""{"choices":[{"index":0,"message":{"role":"assistant","content":${quote(content)}},"finish_reason":"$finish"}]}""")

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun generate(prompt: String = "Yaz") = runBlocking { generator.generate(endpoint(), "m-1", key, prompt) }

    private fun failure(block: () -> Unit): AiGenerationException =
        runCatching(block).exceptionOrNull() as AiGenerationException

    // ---- başarı

    @Test fun successReturnsTrimmedTextAndSendsOpenAiRequest() {
        server.enqueue(reply("  fun main() {}\n"))
        assertEquals("fun main() {}", generate("Bir \"program\" yaz"))

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/chat/completions", req.path)
        assertEquals("Bearer $key", req.getHeader("Authorization"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"model\":\"m-1\""))
        assertTrue(body.contains("\"stream\":false"))
        assertTrue(body.contains("\"role\":\"user\""))
        assertTrue(body.contains("Bir \\\"program\\\" yaz"))
    }

    @Test fun contentPartsAreJoined() {
        server.enqueue(
            json("""{"choices":[{"message":{"content":[{"type":"text","text":"a"},{"type":"image","x":1},{"type":"text","text":"b"}]},"finish_reason":"stop"}]}"""),
        )
        assertEquals("ab", generate())
    }

    @Test fun leadingThinkBlockIsStripped() {
        server.enqueue(reply("<think>düşünüyorum\nçok</think>\n\nSonuç"))
        assertEquals("Sonuç", generate())
    }

    // ---- hata ayrımı

    @Test fun unauthorizedStatusesAreUnauthorized() {
        for (code in listOf(401, 403, 402)) {
            server.enqueue(json("""{"error":{"message":"bad key $key"}}""", code))
            val e = failure { generate() }
            assertEquals(Kind.UNAUTHORIZED, e.kind)
            assertEquals(code, e.httpCode)
            assertFalse(e.kind.retryable)
            assertFalse("hata mesajı anahtarı içermemeli", e.message.orEmpty().contains(key))
        }
    }

    @Test fun geminiStyle400InvalidKeyIsUnauthorized() {
        server.enqueue(json("""{"error":{"message":"API key not valid. Please pass a valid API key."}}""", 400))
        assertEquals(Kind.UNAUTHORIZED, failure { generate() }.kind)
    }

    @Test fun rateLimitCarriesRetryAfter() {
        server.enqueue(json("{}", 429).setHeader("Retry-After", "30"))
        val e = failure { generate() }
        assertEquals(Kind.RATE_LIMITED, e.kind)
        assertEquals(30_000L, e.retryAfterMs)
        assertEquals(1_000_000L + 30_000L, e.waitUntil(1_000_000L))
    }

    @Test fun rateLimitWithoutOrWithBadRetryAfterUsesDefaultWait() {
        server.enqueue(json("{}", 429))
        server.enqueue(json("{}", 429).setHeader("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT"))
        repeat(2) {
            val e = failure { generate() }
            assertEquals(Kind.RATE_LIMITED, e.kind)
            assertNull(e.retryAfterMs)
            assertEquals(10_000L + AiGenerationException.DEFAULT_WAIT_MS, e.waitUntil(10_000L))
        }
    }

    @Test fun waitUntilHasNoUpperCapButDoesNotOverflow() {
        val huge = AiGenerationException(Kind.RATE_LIMITED, 429, retryAfterMs = Long.MAX_VALUE / 4)
        assertEquals(Long.MAX_VALUE / 4, huge.waitUntil(0))
        val max = AiGenerationException(Kind.RATE_LIMITED, 429, retryAfterMs = Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, max.waitUntil(10))
        val zero = AiGenerationException(Kind.RATE_LIMITED, 429, retryAfterMs = 0)
        assertEquals(1_000L, zero.waitUntil(0))
    }

    @Test fun serverErrorsAreRetryable() {
        for (code in listOf(500, 502, 503, 408)) {
            server.enqueue(json("{}", code))
            // OkHttp 408'de isteği bir kez otomatik tekrarlar; ikinci yanıt yoksa okuma zaman aşımı (NETWORK) olurdu.
            if (code == 408) server.enqueue(json("{}", code))
            val e = failure { generate() }
            assertEquals(Kind.SERVER, e.kind)
            assertTrue(e.kind.retryable)
        }
    }

    @Test fun otherClientErrorsAreNotRetryable() {
        server.enqueue(json("{}", 404))
        server.enqueue(json("{}", 422))
        server.enqueue(json("{}", 413))
        assertEquals(Kind.NOT_FOUND, failure { generate() }.kind)
        assertEquals(Kind.BAD_REQUEST, failure { generate() }.kind)
        assertEquals(Kind.BAD_REQUEST, failure { generate() }.kind)
    }

    @Test fun networkFailureIsRetryableNetworkError() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val e = failure { generate() }
        assertEquals(Kind.NETWORK, e.kind)
        assertTrue(e.kind.retryable)
    }

    @Test fun timeoutIsNetworkError() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(Kind.NETWORK, failure { generate() }.kind)
    }

    // ---- bozuk 2xx yanıtlar

    @Test fun unusableSuccessBodiesAreBadResponse() {
        server.enqueue(json("not json"))
        server.enqueue(json("""{"choices":[]}"""))
        server.enqueue(json("""{"choices":[{"message":{"content":null},"finish_reason":"stop"}]}"""))
        server.enqueue(reply("   "))
        server.enqueue(reply("<think>sadece düşünce</think>"))
        server.enqueue(json("""["x"]"""))
        repeat(6) { assertEquals(Kind.BAD_RESPONSE, failure { generate() }.kind) }
    }

    @Test fun lengthFinishReasonIsTruncatedAndNotReturned() {
        server.enqueue(reply("yarım kalmış kod", finish = "length"))
        val e = failure { generate() }
        assertEquals(Kind.TRUNCATED, e.kind)
        assertFalse(e.kind.retryable)
    }

    @Test fun truncatedReplyIsContinuedAndMergedWithoutLimit() {
        val continuing = OpenAiCompatibleGenerator(
            OpenAiCompatibleGenerator.defaultClient().newBuilder()
                .readTimeout(2, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build(),
            maxContinuations = 5,
        )
        server.enqueue(reply("```a.txt\nilk", finish = "length"))
        server.enqueue(reply("\nikinci", finish = "length"))
        server.enqueue(reply("\nson\n```", finish = "stop"))
        val out = runBlocking { continuing.generate(endpoint(), "m-1", key, "Yaz") }
        assertTrue("ilk" in out && "ikinci" in out && "son" in out)
        assertEquals(3, server.requestCount)
        server.takeRequest(); server.takeRequest()
        assertTrue("assistant" in server.takeRequest().body.readUtf8())
    }

    // ---- girdi doğrulama

    @Test fun blankCredentialFailsWithoutNetwork() {
        val e = failure { runBlocking { generator.generate(endpoint(), "m", "  ", "x") } }
        assertEquals(Kind.UNAUTHORIZED, e.kind)
        assertEquals(0, server.requestCount)
    }

    @Test fun credentialWithIllegalHeaderCharsIsUnauthorizedWithoutNetwork() {
        val e = failure { runBlocking { generator.generate(endpoint(), "m", "bad\nkey", "x") } }
        assertEquals(Kind.UNAUTHORIZED, e.kind)
        assertEquals(0, server.requestCount)
    }

    @Test fun blankModelOrPromptIsRejected() {
        for ((model, prompt) in listOf("" to "x", "m" to " ")) {
            val e = runCatching { runBlocking { generator.generate(endpoint(), model, key, prompt) } }.exceptionOrNull()
            assertTrue(e is IllegalArgumentException)
        }
        assertEquals(0, server.requestCount)
    }
}
