package com.kriptoncod.app.data.email

import com.kriptoncod.app.data.providers.AuthMethod
import com.kriptoncod.app.domain.AuthType
import okhttp3.Request

/**
 * E-posta/şifre tabanlı [AuthMethod] uygulaması — Claude (Anthropic) ve ChatGPT (OpenAI) için.
 *
 * [authorize] → kasadan okunan session token'ı Bearer başlığı olarak isteğe ekler.
 * [signIn]    → sağlayıcı giriş uç noktasına POST atar; token kasaya yazılır.
 * [silentRefreshIfNeeded] → süresi dolan token için sessiz yenileme dener.
 */
class EmailPasswordAuth(
    private val tokenStore: EmailTokenStore,
    private val authClient: EmailAuthClient,
    private val accountId: Long,
    /** Sağlayıcının tam giriş URL'si (ProviderCatalog.loginPath'ten türetilir). */
    private val loginUrl: String,
    /** Oturum kapatma URL'si; yoksa null. */
    private val revokeUrl: String? = null,
) : AuthMethod {

    override val type: AuthType = AuthType.EMAIL
    override val isAvailable: Boolean = true

    /**
     * OkHttp isteğine Authorization: Bearer <token> ekler.
     * [credential] → kasadan okunan geçerli session token.
     */
    override fun authorize(request: Request.Builder, credential: String): Request.Builder =
        request.header("Authorization", "Bearer $credential")

    /** Kasadan geçerli token'ı döndürür; yoksa null. */
    suspend fun loadToken(): String? = tokenStore.getValid(accountId)?.sessionToken

    /**
     * Sessiz yenileme: token hâlâ geçerliyse [EmailAuthState.Valid],
     * yenileme başarısızsa [EmailAuthState.NeedsReauth],
     * hata varsa [EmailAuthState.Error] döner.
     */
    suspend fun silentRefreshIfNeeded(): EmailAuthState {
        val existing = tokenStore.getValid(accountId)
        if (existing != null) return EmailAuthState.Valid(existing.sessionToken)

        // Kasada süresi dolmuş token var mı kontrol et (varsa yenilemeyi dene)
        if (!tokenStore.hasToken(accountId)) return EmailAuthState.NeedsReauth

        return when (val result = authClient.silentRefresh(loginUrl, "")) {
            is EmailAuthResult.Success -> {
                tokenStore.save(accountId, result.sessionToken, result.expiresAtMs)
                EmailAuthState.Valid(result.sessionToken)
            }
            is EmailAuthResult.NeedsReauth -> EmailAuthState.NeedsReauth
            is EmailAuthResult.Error -> EmailAuthState.Error(result.message, result.cause)
        }
    }

    /** Kullanıcı e-posta ve şifresiyle giriş yapar; token kasaya yazılır. */
    suspend fun signIn(email: String, password: String): EmailAuthState {
        return when (val result = authClient.login(loginUrl, email, password)) {
            is EmailAuthResult.Success -> {
                tokenStore.save(accountId, result.sessionToken, result.expiresAtMs)
                EmailAuthState.Valid(result.sessionToken)
            }
            is EmailAuthResult.NeedsReauth -> EmailAuthState.NeedsReauth
            is EmailAuthResult.Error -> EmailAuthState.Error(result.message, result.cause)
        }
    }

    /** Oturumu kapatır; token kasadan silinir. */
    suspend fun signOut() {
        val token = tokenStore.getValid(accountId)?.sessionToken ?: ""
        tokenStore.clear(accountId)
        authClient.revoke(revokeUrl, token)
    }
}

sealed interface EmailAuthState {
    data class Valid(val sessionToken: String) : EmailAuthState
    data object NeedsReauth : EmailAuthState
    data class Error(val message: String, val cause: Throwable? = null) : EmailAuthState
}
