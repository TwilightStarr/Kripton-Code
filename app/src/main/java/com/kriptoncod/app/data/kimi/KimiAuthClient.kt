package com.kriptoncod.app.data.kimi

/**
 * Kimi (kimi.com) e-posta/şifre oturum açma istemcisinin test edilebilir soyutlaması.
 * Üretimde [RealKimiAuthClient] kullanılır.
 */
interface KimiAuthClient {
    /** Oturum açma uç noktasına POST atar; başarıda session token (+ varsa refresh token) döner. */
    suspend fun login(loginUrl: String, email: String, password: String): KimiAuthResult

    /** Refresh token ile sessiz yenileme; başarısızsa [KimiAuthResult.NeedsReauth]. */
    suspend fun silentRefresh(refreshUrl: String, refreshToken: String): KimiAuthResult

    /** Oturumu kapat (olanak varsa). Hata sessizce yutulur. */
    suspend fun revoke(revokeUrl: String?, token: String)
}

sealed interface KimiAuthResult {
    /** [sessionToken] Bearer olarak kullanılır; [expiresAtMs] epoch-ms bitiş zamanı. */
    data class Success(
        val sessionToken: String,
        val expiresAtMs: Long,
        val refreshToken: String? = null,
    ) : KimiAuthResult

    /** Yenileme mümkün değil; kullanıcı yeniden giriş yapmalı. */
    data object NeedsReauth : KimiAuthResult

    /** Kimlik bilgisi yanlış ya da ağ hatası; [message] günlük için. */
    data class Error(val message: String, val cause: Throwable? = null) : KimiAuthResult
}
