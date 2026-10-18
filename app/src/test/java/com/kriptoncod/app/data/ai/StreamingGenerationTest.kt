package com.kriptoncod.app.data.ai

import com.kriptoncod.app.data.ai.AiGenerationException.Kind
import com.kriptoncod.app.data.providers.ApiKey
import com.kriptoncod.app.data.providers.ProviderEndpoint
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** SSE akışı: parçalar sink'e akar, nihai metin akışsız yolla aynı normalizer'dan geçer. */
class StreamingGenerationTest {
    private lateinit var server: MockWebServer
    private lateinit var generator: OpenAiCompatibleGenerator
    private val key = "sk-test-" + "k".repeat(24)

    private class Recorder : StreamSink {
        val deltas = mutableListOf<String>()
        var resets = 0
        override fun append(delta: String) {
            deltas += delta
        }
        override fun reset() {
            resets++
        }
    }

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        generator = OpenAiCompatibleGenerator(
            OpenAiCompatibleGenerator.defaultClient().newBuilder()
                .readTimeout(2, TimeUnit.SECONDS)
                .build(),
            maxContinuations = 0,
        )
    }

    @After fun tearDown() {
        runCatching { server.shutdown() }
    }

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

    private fun sse(vararg events: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(events.joinToString("") { "data: $it\n\n" })

    private fun delta(text: String, finish: String? = null) = """{"choices":[{"index":0,"delta":{"content":"$text"},"finish_reason":${finish?.let { "\"$it\"" } ?: "null"}}]}"""

    private fun gen(sink: StreamSink, prompt: String = "Yaz") = runBlocking { generator.generate(endpoint(), "m-1", key, prompt, sink) }

    @Test fun streamsDeltasToSinkAndReturnsJoinedText() {
        server.enqueue(sse(delta("Mer"), delta("haba "), delta("dünya", "stop"), "[DONE]"))
        val sink = Recorder()

        assertEquals("Merhaba dünya", gen(sink))
        assertEquals(listOf("Mer", "haba ", "dünya"), sink.deltas)

        val req = server.takeRequest()
        assertEquals("text/event-stream", req.getHeader("Accept"))
        assertTrue(req.body.readUtf8().contains("\"stream\":true"))
    }

    @Test fun reasoningDeltasAreNotShownLive() {
        server.enqueue(
            sse(
                """{"choices":[{"delta":{"reasoning_content":"düşünüyorum"}}]}""",
                delta("cevap", "stop"),
                "[DONE]",
            ),
        )
        val sink = Recorder()

        assertEquals("cevap", gen(sink))
        assertEquals(listOf("cevap"), sink.deltas)
    }

    @Test fun truncatedFinishReasonIsReportedLikeNonStreaming() {
        server.enqueue(sse(delta("yarım", "length"), "[DONE]"))
        val e = runCatching { gen(Recorder()) }.exceptionOrNull() as AiGenerationException
        assertEquals(Kind.TRUNCATED, e.kind) // maxContinuations = 0
    }

    @Test fun providerIgnoringStreamFlagFallsBackToPlainJsonBody() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"role":"assistant","content":"düz yanıt"},"finish_reason":"stop"}]}"""),
        )
        assertEquals("düz yanıt", gen(Recorder()))
        assertEquals(1, server.requestCount) // ikinci istek atılmadı
    }

    @Test fun httpErrorsMapToTheSameKindsAsNonStreaming() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        assertEquals(Kind.UNAUTHORIZED, (runCatching { gen(Recorder()) }.exceptionOrNull() as AiGenerationException).kind)

        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "7").setBody("{}"))
        val rate = runCatching { gen(Recorder()) }.exceptionOrNull() as AiGenerationException
        assertEquals(Kind.RATE_LIMITED, rate.kind)
        assertEquals(7_000L, rate.retryAfterMs)

        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        assertEquals(Kind.SERVER, (runCatching { gen(Recorder()) }.exceptionOrNull() as AiGenerationException).kind)
    }

    @Test fun emptyStreamIsABadResponse() {
        server.enqueue(sse(delta("", "stop"), "[DONE]"))
        val e = runCatching { gen(Recorder()) }.exceptionOrNull() as AiGenerationException
        assertEquals(Kind.BAD_RESPONSE, e.kind)
    }

    @Test fun streamEndingWithoutDoneButWithFinishReasonCompletes() {
        server.enqueue(sse(delta("tamam", "stop")))
        assertEquals("tamam", gen(Recorder()))
    }

    @Test fun noSinkKeepsNonStreamingRequest() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"x"},"finish_reason":"stop"}]}"""),
        )
        assertEquals("x", runBlocking { generator.generate(endpoint(), "m-1", key, "Yaz") })
        val req = server.takeRequest()
        assertEquals("application/json", req.getHeader("Accept"))
        assertTrue(req.body.readUtf8().contains("\"stream\":false"))
    }
}
