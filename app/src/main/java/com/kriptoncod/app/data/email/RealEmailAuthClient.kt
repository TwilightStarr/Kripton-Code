package com.kriptoncod.app.data.email

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Üretim EmailAuthClient uygulaması.
 * Anthropic Console ve OpenAI oturum açma uç noktalarını destekler.
 *
 * Token ömrü sağlayıcı yanıtından alınır; yoksa varsayılan [DEFAULT_LIFETIME_MS] uygulanır.
 */
class RealEmailAuthClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
) : EmailAuthClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ── Anthropic Console ──────────────────────────────────────────────────────

    @Serializable
    private data class AnthropicLoginRequest(val email: String, val password: String)

    @Serializable
    private data class AnthropicLoginResponse(
        @SerialName("session_token") val sessionToken: String? = null,
        @SerialName("expires_at") val expiresAt: String? = null, // ISO-8601 veya epoch-sn
    )

    // ── OpenAI ────────────────────────────────────────────────────────────────

    @Serializable
    private data class OpenAiLoginRequest(val username: String, val password: String)

    @Serializable
    private data class OpenAiLoginResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null, // saniye
    )

    // ── Ortak ─────────────────────────────────────────────────────────────────

    override suspend fun login(loginUrl: String, email: String, password: String): EmailAuthResult {
        return runCatching {
            // Sağlayıcıya göre istek gövdesi şeklini belirle
            val body = when {
                loginUrl.contains("anthropic.com") ->
                    json.encodeToString(AnthropicLoginRequest(email, password))

                else ->
                    json.encodeToString(OpenAiLoginRequest(email, password))
            }.toRequestBody(jsonMediaType)

            val request = Request.Builder().url(loginUrl).post(body).build()
            val response = httpClient.newCall(request).execute()
            val raw = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@runCatching EmailAuthResult.Error(
                    "Giriş reddedildi (HTTP ${response.code}). Bu sağlayıcı e-posta/şifre ile doğrudan girişe " +
                        "izin vermeyebilir; \"E-posta ile\" (web) girişini veya API anahtarını kullan.",
                )
            }

            parseToken(loginUrl, raw)
        }.getOrElse { e -> EmailAuthResult.Error(e.message ?: "Bilinmeyen hata", e) }
    }

    override suspend fun silentRefresh(loginUrl: String, token: String): EmailAuthResult {
        // Anthropic ve OpenAI Console token yenileme uç noktası şu an tanımlı değil;
        // süresi dolunca kullanıcı yeniden giriş yapmalı.
        return EmailAuthResult.NeedsReauth
    }

    override suspend fun revoke(revokeUrl: String?, token: String) {
        revokeUrl ?: return
        runCatching {
            val req = Request.Builder()
                .url(revokeUrl)
                .delete("{}".toRequestBody(jsonMediaType))
                .header("Authorization", "Bearer $token")
                .build()
            httpClient.newCall(req).execute().close()
        }
        // Hata sessizce yutulur (RFC 7009 §2.2.1 ile uyumlu)
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    private fun parseToken(loginUrl: String, raw: String): EmailAuthResult = if (loginUrl.contains("anthropic.com")) {
        val resp = runCatching { json.decodeFromString<AnthropicLoginResponse>(raw) }
            .getOrNull()
        val token = resp?.sessionToken
        if (token.isNullOrBlank()) {
            EmailAuthResult.Error("Anthropic oturum açma yanıtı beklenmedik biçimde geldi")
        } else {
            val expiresAtMs = parseIsoOrEpochSec(resp.expiresAt)
                ?: (System.currentTimeMillis() + DEFAULT_LIFETIME_MS)
            EmailAuthResult.Success(token, expiresAtMs)
        }
    } else {
        val resp = runCatching { json.decodeFromString<OpenAiLoginResponse>(raw) }
            .getOrNull()
        val token = resp?.accessToken
        if (token.isNullOrBlank()) {
            EmailAuthResult.Error("OpenAI oturum açma yanıtı beklenmedik biçimde geldi")
        } else {
            val expiresAtMs = resp.expiresIn
                ?.let { System.currentTimeMillis() + it * 1000 }
                ?: (System.currentTimeMillis() + DEFAULT_LIFETIME_MS)
            EmailAuthResult.Success(token, expiresAtMs)
        }
    }

    /** ISO-8601 ya da epoch-saniye string'ini epoch-ms'e çevirir. */
    private fun parseIsoOrEpochSec(value: String?): Long? {
        value ?: return null
        return runCatching {
            val epochSec = value.toLong()
            epochSec * 1000
        }.getOrElse {
            runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
        }
    }

    companion object {
        /** Varsayılan token ömrü: 55 dakika. */
        internal const val DEFAULT_LIFETIME_MS = 55L * 60 * 1000
    }
}
