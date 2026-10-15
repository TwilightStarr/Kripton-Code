package com.kriptoncod.app.data.email

/**
 * E-posta/şifre ile oturum açma istemcisinin test edilebilir soyutlaması.
 * Üretimde [RealEmailAuthClient], testlerde [FakeEmailAuthClient] kullanılır.
 */
interface EmailAuthClient {
    /**
     * Sağlayıcının oturum açma uç noktasına POST atar; başarıda session token döner.
     * [loginUrl]  → ProviderCatalog.loginPath'ten türetilmiş tam URL.
     */
    suspend fun login(loginUrl: String, email: String, password: String): EmailAuthResult

    /**
     * Mevcut session token'ının geçerliliğini sorgular; gerekirse yeniler.
     * Sağlayıcı yenileme desteklemiyorsa [EmailAuthResult.NeedsReauth] döner.
     */
    suspend fun silentRefresh(loginUrl: String, token: String): EmailAuthResult

    /** Session token'ını geçersiz kılar (olanak varsa). Hata sessizce yutulur. */
    suspend fun revoke(revokeUrl: String?, token: String)
}

sealed interface EmailAuthResult {
    /** Başarılı; [sessionToken] Bearer olarak kullanılır, [expiresAtMs] epoch-ms bitiş zamanı. */
    data class Success(val sessionToken: String, val expiresAtMs: Long) : EmailAuthResult

    /** Token yenilenemedi; kullanıcı yeniden giriş yapmalı. */
    data object NeedsReauth : EmailAuthResult

    /** Kimlik bilgisi yanlış ya da ağ hatası; [message] günlük için. */
    data class Error(val message: String, val cause: Throwable? = null) : EmailAuthResult
}
