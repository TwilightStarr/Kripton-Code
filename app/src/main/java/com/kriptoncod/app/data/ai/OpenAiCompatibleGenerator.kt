package com.kriptoncod.app.data.ai

import com.kriptoncod.app.data.ai.AiGenerationException.Kind
import com.kriptoncod.app.data.ai.normalize.ChoiceReader
import com.kriptoncod.app.data.ai.normalize.Finish
import com.kriptoncod.app.data.ai.normalize.Normalizers
import com.kriptoncod.app.data.ai.normalize.ProviderNormalizer
import com.kriptoncod.app.data.http.await
import com.kriptoncod.app.data.providers.ProviderEndpoint
import com.kriptoncod.app.data.providers.UpstreamProvider
import com.kriptoncod.app.data.web.ContinuationMerger
import com.kriptoncod.app.data.web.PromptWrapper
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * OpenAI uyumlu `POST <chatUrl>`. Tüm sağlayıcı kataloğu bu biçimi konuşur.
 *
 * - [StreamSink] verilirse yanıt SSE ile (`"stream": true`, okhttp-sse) akıtılır: görünür metin geldikçe sink'e
 *   iletilir; bittiğinde birikmiş metin, akışsız yanıtla aynı yoldan (normalizer, devam turları) işlenir.
 *   Sağlayıcı akışı yok sayıp düz JSON dönerse o yanıt doğrudan kullanılır (ikinci istek atılmaz).
 *   [StreamSink.NONE] ile çağrılırsa eskisi gibi akışsız çalışır.
 *   Akış ortasında bağlantı kopması [Kind.NETWORK]'tür; kısmi metin kaydedilmez (iş baştan yeniden denenir).
 * - Kimlik doğrulama [ProviderEndpoint.authMethod] ile eklenir; kimlik bilgisi loglanmaz.
 * - Hata gövdesi okunur ama hata nesnesine konmaz (sağlayıcılar isteği yansıtabilir).
 * - Yanıt, **model kimliğine göre** seçilen [ProviderNormalizer] ile temizlenir (Claude, Gemini, OpenAI,
 *   DeepSeek, Kimi; tanınmayanlar için genel): muhakeme blokları atılır, `finish_reason` sınıflanır.
 * - Kesik çıktı (`length`, `max_tokens`, `MAX_TOKENS` …) otomatik "devam et" turlarıyla tamamlanır
 *   (en çok [maxContinuations] tur); yine de bitmezse KAYDEDİLMEZ ([Kind.TRUNCATED]). Karakter sınırı yoktur.
 *
 * Çıktının kaydı çağıranındır: `RunRepository.completeJob` → `OutputStore.write` (atomik).
 */
class OpenAiCompatibleGenerator(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
    private val maxContinuations: Int = DEFAULT_MAX_CONTINUATIONS,
) : AiGenerator {

    override suspend fun generate(
        endpoint: ProviderEndpoint,
        modelId: String,
        credential: String,
        prompt: String,
    ): String = generate(endpoint, modelId, credential, prompt, StreamSink.NONE)

    override suspend fun generate(
        endpoint: ProviderEndpoint,
        modelId: String,
        credential: String,
        prompt: String,
        sink: StreamSink,
    ): String {
        val key = credential.trim()
        if (key.isEmpty()) throw AiGenerationException(Kind.UNAUTHORIZED)
        require(modelId.isNotBlank()) { "Model kimliği boş" }
        require(prompt.isNotBlank()) { "Prompt boş" }

        val normalizer = Normalizers.forModel(modelId, UpstreamProvider.fromId(endpoint.providerId))
        val streaming = sink !== StreamSink.NONE
        // Token sayacı varsa her turun kullanımı ona eklenir (bkz. TokenMeter); yoksa yok sayılır.
        val meter = coroutineContext[TokenMeter]
        val report: (TokenUsage) -> Unit = { meter?.add(it) }
        val parts = mutableListOf<String>()
        var continuations = 0
        while (true) {
            val messages = messagesFor(prompt, parts)
            val chunk = if (streaming) {
                when (val r = postStreaming(endpoint, modelId, key, messages, sink)) {
                    is Streamed.Choice -> {
                        r.usage?.let(report)
                        withContext(Dispatchers.Default) { extractChoice(r.choice, normalizer) }
                    }

                    is Streamed.Whole -> withContext(Dispatchers.Default) { extract(r.body, normalizer, report) }
                }
            } else {
                val body = post(endpoint, modelId, key, messages)
                withContext(Dispatchers.Default) { extract(body, normalizer, report) }
            }
            parts += chunk.text
            if (!chunk.truncated) {
                return if (parts.size == 1) parts[0] else ContinuationMerger.merge(parts)
            }
            // Kesik çıktı: kaydedilmez; kaldığı yerden devam ettirilir.
            if (continuations >= maxContinuations) throw AiGenerationException(Kind.TRUNCATED)
            continuations++
        }
    }

    /** İlk tur: yalnız prompt. Sonraki turlar: o ana kadarki yanıt + "devam et" mesajı. */
    private fun messagesFor(prompt: String, parts: List<String>) = buildJsonArray {
        add(
            buildJsonObject {
                put("role", "user")
                put("content", prompt)
            },
        )
        if (parts.isNotEmpty()) {
            add(
                buildJsonObject {
                    put("role", "assistant")
                    put("content", ContinuationMerger.merge(parts))
                },
            )
            add(
                buildJsonObject {
                    put("role", "user")
                    put("content", PromptWrapper.continuation())
                },
            )
        }
    }

    private fun buildRequest(
        endpoint: ProviderEndpoint,
        key: String,
        payload: JsonObject,
        accept: String,
    ): Request = try {
        endpoint.authMethod.authorize(
            Request.Builder()
                .url(endpoint.chatUrl)
                .header("Accept", accept)
                .post(payload.toString().toRequestBody(JSON_MEDIA)),
            key,
        ).build()
    } catch (e: IllegalArgumentException) {
        throw AiGenerationException(Kind.UNAUTHORIZED) // başlıkta geçersiz karakter
    }

    private suspend fun post(endpoint: ProviderEndpoint, modelId: String, key: String, messages: JsonArray): String {
        val payload = buildJsonObject {
            put("model", modelId.trim())
            put("stream", false)
            put("messages", messages)
        }
        val request = buildRequest(endpoint, key, payload, "application/json")

        val (code, retryAfter, body) = try {
            client.newCall(request).await().use { r ->
                Triple(r.code, r.header("Retry-After"), r.peekBody(MAX_BODY_BYTES).string())
            }
        } catch (e: IOException) {
            throw AiGenerationException(Kind.NETWORK)
        }

        if (code !in 200..299) throw failure(code, retryAfter, body)
        return body
    }

    private sealed interface Streamed {
        /** SSE parçalarından birleştirilmiş, akışsız yanıttaki `choices[0]` ile aynı biçimli nesne. */
        class Choice(val choice: JsonObject, val usage: TokenUsage? = null) : Streamed

        /** Sağlayıcı akışı yok sayıp düz JSON döndürdü. */
        class Whole(val body: String) : Streamed
    }

    /**
     * SSE akışı: her `data:` olayındaki `choices[0].delta` görünür metni [sink]'e iletilir ve biriktirilir.
     * İptalde (duraklat/iptal) bağlantı hemen kapanır.
     */
    private suspend fun postStreaming(
        endpoint: ProviderEndpoint,
        modelId: String,
        key: String,
        messages: JsonArray,
        sink: StreamSink,
    ): Streamed {
        val payload = buildJsonObject {
            put("model", modelId.trim())
            put("stream", true)
            // Akışta kullanım yalnız istenirse gelir; bilinmeyen alanı reddedebilecek sağlayıcılara gönderilmez.
            if (endpoint.providerId in USAGE_STREAM_PROVIDERS) {
                put("stream_options", buildJsonObject { put("include_usage", true) })
            }
            put("messages", messages)
        }
        val request = buildRequest(endpoint, key, payload, "text/event-stream")
        // Akış uzun sürebilir: toplam süre sınırı kalkar; takılmayı okuma zaman aşımı (olaylar arası) yakalar.
        val streamClient = client.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).build()

        return suspendCancellableCoroutine { cont ->
            val text = StringBuilder()
            val refusal = StringBuilder()
            var finish: String? = null
            var usage: TokenUsage? = null

            fun finishStream(): Streamed.Choice {
                val message = buildJsonObject {
                    put("role", "assistant")
                    put("content", text.toString())
                    if (refusal.isNotEmpty()) put("refusal", refusal.toString())
                }
                return Streamed.Choice(
                    buildJsonObject {
                        put("message", message)
                        finish?.let { put("finish_reason", it) }
                    },
                    usage,
                )
            }

            val listener = object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (!cont.isActive) return
                    if (data.trim() == "[DONE]") {
                        eventSource.cancel()
                        cont.resume(finishStream())
                        return
                    }
                    val event = try {
                        json.parseToJsonElement(data).jsonObject
                    } catch (e: IllegalArgumentException) {
                        return // bozuk/yorum olayı: yok say
                    }
                    // Kullanım çoğunlukla son olayda, boş `choices` ile gelir; seçimden ÖNCE okunur.
                    UsageParser.read(event["usage"])?.let { usage = it }
                    val choice = (event["choices"] as? JsonArray)
                        ?.firstOrNull() as? JsonObject
                    if (choice == null) {
                        if (event["error"] != null) {
                            eventSource.cancel()
                            cont.resumeWithException(AiGenerationException(Kind.BAD_RESPONSE))
                        }
                        return
                    }
                    val delta = choice["delta"] as? JsonObject
                    if (delta != null) {
                        val raw = ChoiceReader.read(buildJsonObject { put("message", delta) })
                        raw.visibleText?.let {
                            text.append(it)
                            sink.append(it)
                        }
                        raw.refusal?.let { refusal.append(it) }
                    }
                    (choice["finish_reason"] as? JsonPrimitive)
                        ?.contentOrNull?.takeIf { it.isNotBlank() }?.let { finish = it }
                }

                override fun onClosed(eventSource: EventSource) {
                    // [DONE] gelmeden kapanan akış: metin ve bitiş nedeni varsa tamam, yoksa yarım kalmıştır.
                    if (!cont.isActive) return
                    if (text.isNotEmpty() && finish != null) {
                        cont.resume(finishStream())
                    } else {
                        cont.resumeWithException(AiGenerationException(Kind.NETWORK))
                    }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    if (!cont.isActive) return
                    if (response != null && !response.isSuccessful) {
                        val body = runCatching { response.peekBody(MAX_ERROR_BODY_BYTES).string() }.getOrDefault("")
                        cont.resumeWithException(failure(response.code, response.header("Retry-After"), body))
                        return
                    }
                    if (response != null && t is IllegalStateException && text.isEmpty()) {
                        // 2xx ama text/event-stream değil: sağlayıcı akışı yok saydı; gövde tam yanıttır.
                        val body = runCatching { response.body?.string() }.getOrNull()
                        if (body != null) {
                            cont.resume(Streamed.Whole(body))
                            return
                        }
                    }
                    cont.resumeWithException(AiGenerationException(Kind.NETWORK))
                }
            }

            val source = EventSources.createFactory(streamClient).newEventSource(request, listener)
            cont.invokeOnCancellation { source.cancel() }
        }
    }

    private fun failure(code: Int, retryAfter: String?, body: String): AiGenerationException {
        val kind = when {
            code == 401 || code == 403 || code == 402 -> Kind.UNAUTHORIZED
            code == 400 && INVALID_KEY_HINTS.any { it in body.lowercase() } -> Kind.UNAUTHORIZED
            code == 429 -> Kind.RATE_LIMITED
            code == 404 -> Kind.NOT_FOUND
            code == 408 || code >= 500 -> Kind.SERVER
            else -> Kind.BAD_REQUEST
        }
        return AiGenerationException(kind, code, parseRetryAfter(retryAfter))
    }

    /** Yalnız saniye biçimi desteklenir; HTTP-date veya geçersiz değer null. */
    private fun parseRetryAfter(value: String?): Long? = value?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.let { if (it > Long.MAX_VALUE / 1000) Long.MAX_VALUE else it * 1000 }

    /**
     * Yanıtı ayrıştırır: `choices[0]` → [ChoiceReader] (içerik biçimleri, muhakeme parçaları) →
     * model ailesine özgü [ProviderNormalizer] (temizlik + `finish_reason` sınıflaması).
     *
     * - Metin boş / reddedilmiş (içerik filtresi, `refusal`) → [Kind.BAD_RESPONSE]
     * - Çıktı uzunluk sınırında kesilmiş → [Chunk.truncated] (çağıran devam turu ister)
     */
    private class Chunk(val text: String, val truncated: Boolean)

    private fun extract(body: String, normalizer: ProviderNormalizer, report: (TokenUsage) -> Unit): Chunk {
        val (choice, usage) = try {
            val root = json.parseToJsonElement(body).jsonObject
            root["choices"]?.jsonArray?.firstOrNull()?.jsonObject to UsageParser.read(root["usage"])
        } catch (e: IllegalArgumentException) { // SerializationException ve tür dönüşüm hataları
            null to null
        }
        // Reddedilen/boş yanıt da token harcamış olabilir: kullanım hata fırlatılmadan ÖNCE bildirilir.
        usage?.let(report)
        if (choice == null) throw AiGenerationException(Kind.BAD_RESPONSE)
        return extractChoice(choice, normalizer)
    }

    private fun extractChoice(choice: JsonObject, normalizer: ProviderNormalizer): Chunk {
        val out = normalizer.normalize(ChoiceReader.read(choice))
        if (out.text.isEmpty() || out.finish == Finish.FILTERED) throw AiGenerationException(Kind.BAD_RESPONSE)
        return Chunk(out.text, truncated = out.finish == Finish.TRUNCATED)
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        const val DEFAULT_MAX_CONTINUATIONS = 20
        private const val MAX_BODY_BYTES = Long.MAX_VALUE
        private const val MAX_ERROR_BODY_BYTES = 64L * 1024

        /** Akışta `stream_options.include_usage` gönderilen, bunu desteklediği bilinen sağlayıcılar. */
        private val USAGE_STREAM_PROVIDERS = setOf("openai", "openrouter", "groq", "mistral", "deepseek")
        private val INVALID_KEY_HINTS = listOf("api key not valid", "api_key_invalid")

        /** Uzun üretimler için geniş zaman aşımı; yönlendirme kapalı (anahtar başka adrese sızmaz). */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .callTimeout(300, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
