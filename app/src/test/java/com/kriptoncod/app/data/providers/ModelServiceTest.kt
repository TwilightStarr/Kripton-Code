package com.kriptoncod.app.data.providers

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModelServiceTest {
    private lateinit var server: MockWebServer
    private val service = ModelService(
        ModelService.defaultClient().newBuilder()
            .readTimeout(400, TimeUnit.MILLISECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .build(),
    )

    @Before fun setUp() { server = MockWebServer().apply { start() } }

    @After fun tearDown() { runCatching { server.shutdown() } }

    private fun endpoint(validationPath: String = "models"): ProviderEndpoint {
        val base = server.url("/v1")
        return ProviderEndpoint(
            providerId = "test",
            baseUrl = base,
            modelsUrl = base.newBuilder().addPathSegment("models").build(),
            chatUrl = base.newBuilder().addPathSegments("chat/completions").build(),
            validationUrl = base.newBuilder().addPathSegments(validationPath).build(),
            authMethod = ApiKey(),
        )
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun fail(r: ApiResult<*>): ApiResult.Fail = r as ApiResult.Fail

    @Test fun validateSendsBearerKeyAndSucceeds() = runBlocking {
        server.enqueue(json("""{"data":[]}"""))
        assertTrue(service.validate(endpoint(), "  sk-test  ") is ApiResult.Ok)
        val req = server.takeRequest()
        assertEquals("/v1/models", req.path)
        assertEquals("Bearer sk-test", req.getHeader("Authorization"))
    }

    @Test fun unauthorizedAndForbiddenMeanInvalidKey() = runBlocking {
        server.enqueue(json("{}", 401))
        server.enqueue(json("{}", 403))
        assertEquals(ApiError.INVALID_KEY, fail(service.validate(endpoint(), "k")).error)
        assertEquals(ApiError.INVALID_KEY, fail(service.validate(endpoint(), "k")).error)
    }

    @Test fun geminiStyle400InvalidKeyIsRecognized() = runBlocking {
        server.enqueue(json("""{"error":{"code":400,"message":"API key not valid. Please pass a valid API key."}}""", 400))
        assertEquals(ApiError.INVALID_KEY, fail(service.validate(endpoint(), "k")).error)
    }

    @Test fun otherHttpStatusesAreClassified() = runBlocking {
        server.enqueue(json("{}", 429))
        server.enqueue(json("{}", 404))
        server.enqueue(json("{}", 503))
        server.enqueue(json("{}", 400))
        assertEquals(ApiError.RATE_LIMITED, fail(service.validate(endpoint(), "k")).error)
        assertEquals(ApiError.NOT_FOUND, fail(service.validate(endpoint(), "k")).error)
        val s = fail(service.validate(endpoint(), "k"))
        assertEquals(ApiError.SERVER, s.error)
        assertEquals(503, s.httpCode)
        assertEquals(ApiError.BAD_RESPONSE, fail(service.validate(endpoint(), "k")).error)
    }

    @Test fun blankOrIllegalKeyNeverHitsTheNetwork() = runBlocking {
        assertEquals(ApiError.INVALID_KEY, fail(service.validate(endpoint(), "   ")).error)
        assertEquals(ApiError.INVALID_KEY, fail(service.validate(endpoint(), "anahtar-ğ")).error)
        assertEquals(0, server.requestCount)
    }

    @Test fun redirectsAreNotFollowed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/baska").toString()))
        assertEquals(ApiError.BAD_RESPONSE, fail(service.validate(endpoint(), "k")).error)
        assertEquals(1, server.requestCount)
    }

    @Test fun connectionFailureAndTimeoutMeanNetworkError() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(ApiError.NETWORK, fail(service.validate(endpoint(), "k")).error)
        val ep = endpoint()
        server.shutdown()
        assertEquals(ApiError.NETWORK, fail(service.validate(ep, "k")).error)
    }

    @Test fun listModelsParsesGeminiIdsAndStripsModelsPrefix() = runBlocking {
        server.enqueue(json("""{"object":"list","data":[{"id":"models/gemini-2.5-pro","object":"model"},{"id":"models/text-embedding-004"}]}"""))
        val list = (service.listModels(endpoint(), "k") as ApiResult.Ok).value
        assertEquals(listOf("gemini-2.5-pro", "text-embedding-004"), list.map { it.id })
    }

    @Test fun listModelsFiltersByProviderMetadata() = runBlocking {
        server.enqueue(
            json(
                """{"data":[
                  {"id":"llama-3.3-70b-versatile","active":true,"context_window":131072},
                  {"id":"old-model","active":false},
                  {"id":"mistral-large-latest","max_context_length":128000,"capabilities":{"completion_chat":true}},
                  {"id":"mistral-embed","capabilities":{"completion_chat":false}},
                  {"id":"vendor/draw","architecture":{"output_modalities":["image"]}},
                  {"id":"vendor/chat:free","context_length":65536,"architecture":{"output_modalities":["text"]}},
                  {"id":"vendor/chat:free"}
                ]}""",
            ),
        )
        val list = (service.listModels(endpoint(), "k") as ApiResult.Ok).value
        assertEquals(
            listOf("llama-3.3-70b-versatile", "mistral-large-latest", "vendor/chat:free"),
            list.map { it.id },
        )
        assertEquals(131072L, list[0].contextLength)
        assertEquals(128000L, list[1].contextLength)
        assertEquals(65536L, list[2].contextLength)
    }

    @Test fun malformedJsonIsBadResponse() = runBlocking {
        server.enqueue(json("<html>not json</html>"))
        server.enqueue(json("""{"data":[{"noId":1}]}"""))
        assertEquals(ApiError.BAD_RESPONSE, fail(service.listModels(endpoint(), "k")).error)
        assertEquals(ApiError.BAD_RESPONSE, fail(service.listModels(endpoint(), "k")).error)
    }

    @Test fun discoverUsesSingleRequestWhenValidationIsModelsEndpoint() = runBlocking {
        server.enqueue(json("""{"data":[{"id":"llama-3.3-70b-versatile"},{"id":"qwen3-coder-480b"},{"id":"whisper-large-v3"}]}"""))
        val ranking = (service.discover(endpoint(), "k") as ApiResult.Ok).value
        assertEquals(1, server.requestCount)
        assertEquals("qwen3-coder-480b", ranking.recommended!!.id)
        assertEquals(2, ranking.models.size)
    }

    @Test fun discoverValidatesFirstWhenEndpointsDiffer() = runBlocking {
        server.enqueue(json("""{"data":{"label":"anahtar"}}"""))
        server.enqueue(json("""{"data":[{"id":"vendor/big-70b"},{"id":"vendor/small:free"}]}"""))
        val ep = endpoint(validationPath = "key").copy(freeModelMarker = ":free")
        val ranking = (service.discover(ep, "k") as ApiResult.Ok).value
        assertEquals("/v1/key", server.takeRequest().path)
        assertEquals("/v1/models", server.takeRequest().path)
        assertEquals("vendor/small:free", ranking.recommended!!.id)
    }

    @Test fun discoverStopsWhenValidationFails() = runBlocking {
        server.enqueue(json("{}", 401))
        val r = service.discover(endpoint(validationPath = "key"), "k")
        assertEquals(ApiError.INVALID_KEY, fail(r).error)
        assertEquals(1, server.requestCount)
    }
}
