package com.kriptoncod.app.data.ai

import com.kriptoncod.app.data.ai.AiGenerationException.Kind
import com.kriptoncod.app.data.http.await
import com.kriptoncod.app.data.providers.ProviderEndpoint
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
 * - `finish_reason == "length"` kesik çıktı sayılır ve KAYDEDİLMEZ ([Kind.TRUNCATED]).
 * - Başında `<think>…</think>` muhakeme bloğu varsa atılır.
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
        return withContext(Dispatchers.Default) { extract(body) }
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

    private fun extract(body: String): String {
        val choice: JsonObject = (
            try {
                json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            } catch (e: IllegalArgumentException) { // SerializationException ve tür dönüşüm hataları
                null
            }
            ) ?: throw AiGenerationException(Kind.BAD_RESPONSE)

        val text = contentText(choice["message"]?.let { (it as? JsonObject)?.get("content") })
            ?.let { THINK_BLOCK.replaceFirst(it, "") }
            ?.trim()
        if (text.isNullOrEmpty()) throw AiGenerationException(Kind.BAD_RESPONSE)

        val finish = (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull
        if (finish == "length") throw AiGenerationException(Kind.TRUNCATED)
        return text
    }

    /** `content` düz metin ya da `[{type:"text", text:"…"}]` parça listesi olabilir. */
    private fun contentText(content: JsonElement?): String? = when (content) {
        null, JsonNull -> null
        is JsonPrimitive -> content.contentOrNull
        is JsonArray -> content.mapNotNull { part ->
            val obj = part as? JsonObject ?: return@mapNotNull null
            val type = (obj["type"] as? JsonPrimitive)?.contentOrNull
            if (type == null || type == "text") (obj["text"] as? JsonPrimitive)?.contentOrNull else null
        }.joinToString("").ifEmpty { null }
        else -> null
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val MAX_BODY_BYTES = 16L * 1024 * 1024
        private const val MAX_RETRY_AFTER_S = 7L * 24 * 3600
        private val INVALID_KEY_HINTS = listOf("api key not valid", "api_key_invalid")
        private val THINK_BLOCK = Regex("^\\s*<think>.*?</think>\\s*", RegexOption.DOT_MATCHES_ALL)

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
