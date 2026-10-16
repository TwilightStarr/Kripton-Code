package com.kriptoncod.app.data.ai

import com.kriptoncod.app.data.ai.AiGenerationException.Kind
import com.kriptoncod.app.data.ai.normalize.ChoiceReader
import com.kriptoncod.app.data.ai.normalize.Finish
import com.kriptoncod.app.data.ai.normalize.Normalizers
import com.kriptoncod.app.data.ai.normalize.ProviderNormalizer
import com.kriptoncod.app.data.http.await
import com.kriptoncod.app.data.providers.ProviderEndpoint
import com.kriptoncod.app.data.providers.UpstreamProvider
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI uyumlu `POST <chatUrl>` (akışsız). Tüm sağlayıcı kataloğu bu biçimi konuşur.
 *
 * - Kimlik doğrulama [ProviderEndpoint.authMethod] ile eklenir; kimlik bilgisi loglanmaz.
 * - Hata gövdesi okunur ama hata nesnesine konmaz (sağlayıcılar isteği yansıtabilir).
 * - Yanıt, **model kimliğine göre** seçilen [ProviderNormalizer] ile temizlenir (Claude, Gemini, OpenAI,
 *   DeepSeek, Kimi; tanınmayanlar için genel): muhakeme blokları atılır, `finish_reason` sınıflanır.
 * - Kesik çıktı (`length`, `max_tokens`, `MAX_TOKENS` …) KAYDEDİLMEZ ([Kind.TRUNCATED]).
 *
 * Çıktının kaydı çağıranındır: `RunRepository.completeJob` → `OutputStore.write` (atomik).
 */
class OpenAiCompatibleGenerator(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : AiGenerator {

    override suspend fun generate(
        endpoint: ProviderEndpoint,
        modelId: String,
        credential: String,
        prompt: String,
    ): String {
        val key = credential.trim()
        if (key.isEmpty()) throw AiGenerationException(Kind.UNAUTHORIZED)
        require(modelId.isNotBlank()) { "Model kimliği boş" }
        require(prompt.isNotBlank()) { "Prompt boş" }

        val payload = buildJsonObject {
            put("model", modelId.trim())
            put("stream", false)
            put(
                "messages",
                buildJsonArray {
                    add(buildJsonObject { put("role", "user"); put("content", prompt) })
                },
            )
        }
        val request = try {
            endpoint.authMethod.authorize(
                Request.Builder()
                    .url(endpoint.chatUrl)
                    .header("Accept", "application/json")
                    .post(payload.toString().toRequestBody(JSON_MEDIA)),
                key,
            ).build()
        } catch (e: IllegalArgumentException) {
            throw AiGenerationException(Kind.UNAUTHORIZED) // başlıkta geçersiz karakter
        }

        val (code, retryAfter, body) = try {
            client.newCall(request).await().use { r ->
                Triple(r.code, r.header("Retry-After"), r.peekBody(MAX_BODY_BYTES).string())
            }
        } catch (e: IOException) {
            throw AiGenerationException(Kind.NETWORK)
        }

        if (code !in 200..299) throw failure(code, retryAfter, body)
        val normalizer = Normalizers.forModel(modelId, UpstreamProvider.fromId(endpoint.providerId))
        return withContext(Dispatchers.Default) { extract(body, normalizer) }
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
    private fun parseRetryAfter(value: String?): Long? =
        value?.trim()?.toLongOrNull()?.takeIf { it in 0..MAX_RETRY_AFTER_S }?.times(1000)

    /**
     * Yanıtı ayrıştırır: `choices[0]` → [ChoiceReader] (içerik biçimleri, muhakeme parçaları) →
     * model ailesine özgü [ProviderNormalizer] (temizlik + `finish_reason` sınıflaması).
     *
     * - Metin boş / reddedilmiş (içerik filtresi, `refusal`) → [Kind.BAD_RESPONSE]
     * - Çıktı uzunluk sınırında kesilmiş → [Kind.TRUNCATED]
     */
    private fun extract(body: String, normalizer: ProviderNormalizer): String {
        val choice: JsonObject = (
            try {
                json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            } catch (e: IllegalArgumentException) { // SerializationException ve tür dönüşüm hataları
                null
            }
            ) ?: throw AiGenerationException(Kind.BAD_RESPONSE)

        val out = normalizer.normalize(ChoiceReader.read(choice))
        if (out.text.isEmpty() || out.finish == Finish.FILTERED) throw AiGenerationException(Kind.BAD_RESPONSE)
        if (out.finish == Finish.TRUNCATED) throw AiGenerationException(Kind.TRUNCATED)
        return out.text
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val MAX_BODY_BYTES = 16L * 1024 * 1024
        private const val MAX_RETRY_AFTER_S = 7L * 24 * 3600
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
