package com.kriptoncod.app.data.kimi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RealKimiAuthClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
) : KimiAuthClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Serializable
    private data class KimiLoginRequest(val email: String, val password: String)

    @Serializable
    private data class KimiTokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("expires_at") val expiresAt: String? = null,
    )

    @Serializable
    private data class KimiRefreshRequest(@SerialName("refresh_token") val refreshToken: String)

    override suspend fun login(loginUrl: String, email: String, password: String): KimiAuthResult = runCatching {
        val body = json.encodeToString(KimiLoginRequest(email, password)).toRequestBody(jsonMediaType)
        val request = Request.Builder().url(web(loginUrl)).post(body).build()
        val response = httpClient.newCall(request).execute()
        val raw = response.body?.string() ?: ""
        response.close()

        if (!response.isSuccessful) {
            if (response.code == 401 || response.code == 403) {
                return@runCatching KimiAuthResult.Error("Kimlik bilgisi reddedildi (HTTP ${response.code})")
            }
            return@runCatching KimiAuthResult.Error(
                "Kimi girişi başarısız (HTTP ${response.code}). Kimi e-posta/şifre ile doğrudan girişi " +
                    "desteklemeyebilir; \"E-posta ile\" (web) girişini veya API anahtarını kullan.",
            )
        }
        parseToken(raw)
    }.getOrElse { e -> KimiAuthResult.Error(e.message ?: "Bilinmeyen hata", e) }

    override suspend fun silentRefresh(refreshUrl: String, refreshToken: String): KimiAuthResult = runCatching {
        val body = json.encodeToString(KimiRefreshRequest(refreshToken)).toRequestBody(jsonMediaType)
        val request = Request.Builder().url(web(refreshUrl)).post(body).build()
        val response = httpClient.newCall(request).execute()
        val raw = response.body?.string() ?: ""
        response.close()

        when {
            response.code == 401 || response.code == 403 -> KimiAuthResult.NeedsReauth
            !response.isSuccessful -> KimiAuthResult.Error("HTTP ${response.code}")
            else -> parseToken(raw)
        }
    }.getOrElse { e -> KimiAuthResult.Error(e.message ?: "Bilinmeyen hata", e) }

    override suspend fun revoke(revokeUrl: String?, token: String) {
        revokeUrl ?: return
        runCatching {
            val req = Request.Builder()
                .url(web(revokeUrl))
                .delete("{}".toRequestBody(jsonMediaType))
                .header("Authorization", "Bearer $token")
                .build()
            httpClient.newCall(req).execute().close()
        }
    }

    private fun parseToken(raw: String): KimiAuthResult {
        val resp = runCatching { json.decodeFromString<KimiTokenResponse>(raw) }.getOrNull()
        val token = resp?.accessToken
        if (token.isNullOrBlank()) {
            return KimiAuthResult.Error("Kimi oturum açma yanıtı beklenmedik biçimde geldi")
        }
        val expiresAtMs = resp.expiresAt?.let { iso ->
            runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()
        } ?: resp.expiresIn?.let { System.currentTimeMillis() + it * 1000 }
            ?: (System.currentTimeMillis() + DEFAULT_LIFETIME_MS)
        return KimiAuthResult.Success(token, expiresAtMs, resp.refreshToken)
    }

    private fun web(url: String): String = url.replace("api.moonshot.cn/v1", "kimi.moonshot.cn")

    companion object {
        internal const val DEFAULT_LIFETIME_MS = 55L * 60 * 1000
    }
}
