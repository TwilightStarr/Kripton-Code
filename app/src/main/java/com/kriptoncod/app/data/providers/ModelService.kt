package com.kriptoncod.app.data.providers

import androidx.annotation.StringRes
import com.kriptoncod.app.R
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

enum class ApiError(@StringRes val messageRes: Int) {
    INVALID_KEY(R.string.err_invalid_key),
    RATE_LIMITED(R.string.err_rate_limited),
    NOT_FOUND(R.string.err_not_found),
    SERVER(R.string.err_server),
    NETWORK(R.string.err_network),
    BAD_RESPONSE(R.string.err_bad_response),
}

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    /** Hata ayrıntısı anahtar içermez; yalnız tür ve HTTP kodu taşır. */
    data class Fail(val error: ApiError, val httpCode: Int? = null) : ApiResult<Nothing>
}

/** Sağlayıcıdan model listesi çekme, anahtar doğrulama ve öneri üretme. Anahtar loglanmaz. */
class ModelService(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; coerceInputValues = true },
) {

    /** Anahtarı doğrulama isteği atar (kota harcamaz). 429 → anahtar geçerli olabilir ama sınır dolu. */
    suspend fun validate(endpoint: ProviderEndpoint, apiKey: String): ApiResult<Unit> =
        when (val r = get(endpoint.validationUrl, endpoint.authMethod, apiKey)) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Fail -> r
        }

    suspend fun listModels(endpoint: ProviderEndpoint, apiKey: String): ApiResult<List<RemoteModel>> {
        val body = when (val r = get(endpoint.modelsUrl, endpoint.authMethod, apiKey)) {
            is ApiResult.Ok -> r.value
            is ApiResult.Fail -> return r
        }
        return withContext(Dispatchers.Default) {
            try {
                ApiResult.Ok(json.decodeFromString<ModelListDto>(body).toModels())
            } catch (e: IllegalArgumentException) { // SerializationException de buna dahildir
                ApiResult.Fail(ApiError.BAD_RESPONSE)
            }
        }
    }

    /** Doğrula → modelleri çek → puanla. Doğrulama ve liste aynı uç noktaysa tek istek atılır. */
    suspend fun discover(endpoint: ProviderEndpoint, apiKey: String): ApiResult<ModelRanking> {
        if (endpoint.validationUrl != endpoint.modelsUrl) {
            val v = validate(endpoint, apiKey)
            if (v is ApiResult.Fail) return v
        }
        return when (val r = listModels(endpoint, apiKey)) {
            is ApiResult.Ok -> ApiResult.Ok(ModelScorer.rank(r.value, endpoint.freeModelMarker))
            is ApiResult.Fail -> r
        }
    }

    private suspend fun get(url: HttpUrl, auth: AuthMethod, apiKey: String): ApiResult<String> {
        val key = apiKey.trim()
        if (key.isEmpty()) return ApiResult.Fail(ApiError.INVALID_KEY)
        val request = try {
            auth.authorize(Request.Builder().url(url).header("Accept", "application/json"), key).get().build()
        } catch (e: IllegalArgumentException) {
            return ApiResult.Fail(ApiError.INVALID_KEY) // başlıkta geçersiz karakter
        }
        return try {
            client.newCall(request).await().use { response ->
                val body = response.peekBody(MAX_BODY_BYTES).string()
                classify(response.code, body)
            }
        } catch (e: IOException) {
            ApiResult.Fail(ApiError.NETWORK)
        }
    }

    private fun classify(code: Int, body: String): ApiResult<String> = when {
        code in 200..299 -> ApiResult.Ok(body)
        code == 401 || code == 403 -> ApiResult.Fail(ApiError.INVALID_KEY, code)
        code == 400 && INVALID_KEY_HINTS.any { it in body.lowercase() } -> ApiResult.Fail(ApiError.INVALID_KEY, code)
        code == 404 -> ApiResult.Fail(ApiError.NOT_FOUND, code)
        code == 429 -> ApiResult.Fail(ApiError.RATE_LIMITED, code)
        code >= 500 -> ApiResult.Fail(ApiError.SERVER, code)
        else -> ApiResult.Fail(ApiError.BAD_RESPONSE, code)
    }

    private fun ModelListDto.toModels(): List<RemoteModel> = data
        .asSequence()
        .filter { it.active != false }
        .filter { it.capabilities?.completionChat != false }
        .filter { m -> m.architecture?.outputModalities?.let { "text" in it } ?: true }
        .map { m ->
            RemoteModel(
                id = m.id.trim().removePrefix("models/"), // Gemini "models/gemini-…" döndürür
                contextLength = m.contextLength ?: m.contextWindow ?: m.maxContextLength,
            )
        }
        .filter { it.id.isNotEmpty() }
        .distinctBy { it.id }
        .toList()

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) cont.resume(response) else response.close()
                }
            },
        )
    }

    companion object {
        private const val MAX_BODY_BYTES = 8L * 1024 * 1024
        private val INVALID_KEY_HINTS = listOf("api key not valid", "api_key_invalid")

        /** Yönlendirme kapalı: anahtar başka bir adrese sızdırılmaz. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}

@Serializable
private data class ModelListDto(val data: List<ModelDto> = emptyList())

@Serializable
private data class ModelDto(
    val id: String,
    val active: Boolean? = null,
    @SerialName("context_length") val contextLength: Long? = null,
    @SerialName("context_window") val contextWindow: Long? = null,
    @SerialName("max_context_length") val maxContextLength: Long? = null,
    val capabilities: CapabilitiesDto? = null,
    val architecture: ArchitectureDto? = null,
)

@Serializable
private data class CapabilitiesDto(@SerialName("completion_chat") val completionChat: Boolean? = null)

@Serializable
private data class ArchitectureDto(@SerialName("output_modalities") val outputModalities: List<String>? = null)
