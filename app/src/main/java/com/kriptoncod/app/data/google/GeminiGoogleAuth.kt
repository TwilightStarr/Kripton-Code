package com.kriptoncod.app.data.google

import android.app.Activity
import com.kriptoncod.app.data.providers.AuthMethod
import com.kriptoncod.app.domain.AuthType
import okhttp3.Request

/**
 * Gemini'ye özgü Google OAuth [AuthMethod] uygulaması.
 *
 * [authorize] → UI akışını başlatır; token KeyVault'ta saklanır.
 * [authorizeRequest] → saklı geçerli token'ı başlığa ekler.
 * [silentRefreshIfNeeded] → süresi dolan token için sessiz yenileme dener;
 *   başarısız olursa [GoogleAuthState.NeedsReauth] döner.
 */
class GeminiGoogleAuth(
    private val tokenStore: GoogleTokenStore,
    private val authClient: GoogleAuthClient,
    private val accountId: Long,
) : AuthMethod {

    override val type: AuthType = AuthType.GOOGLE
    override val isAvailable: Boolean = true

    /**
     * OkHttp isteği için: kasadan geçerli token'ı okur.
     * Token yoksa veya süresi dolmuşsa boş "Bearer" gönderir;
     * çağıran katman önceden [silentRefreshIfNeeded] çalıştırmalıdır.
     */
    override fun authorize(request: Request.Builder, credential: String): Request.Builder = request.header("Authorization", "Bearer $credential")

    /** Kasadan geçerli access token'ı döndürür; yoksa null. */
    suspend fun loadToken(): String? = tokenStore.getValid(accountId)?.accessToken

    /**
     * Sessiz yenileme: token hâlâ geçerliyse [GoogleAuthState.Valid],
     * yenileme başarısızsa [GoogleAuthState.NeedsReauth],
     * hata varsa [GoogleAuthState.Error].
     */
    suspend fun silentRefreshIfNeeded(): GoogleAuthState {
        // Kasada geçerli token var mı?
        val existing = tokenStore.getValid(accountId)
        if (existing != null) return GoogleAuthState.Valid(existing.accessToken)

        return when (val result = authClient.silentRefresh()) {
            is GoogleAuthResult.Success -> {
                tokenStore.save(accountId, result.accessToken, result.expiresAtMs)
                GoogleAuthState.Valid(result.accessToken)
            }

            is GoogleAuthResult.NeedsReauth -> GoogleAuthState.NeedsReauth

            is GoogleAuthResult.Error -> GoogleAuthState.Error(result.message, result.cause)
        }
    }

    /** Kullanıcı etkileşimiyle oturum açar; token'ı kasaya yazar. */
    suspend fun signIn(activity: Activity): GoogleAuthState = when (val result = authClient.authorize(activity)) {
        is GoogleAuthResult.Success -> {
            tokenStore.save(accountId, result.accessToken, result.expiresAtMs)
            GoogleAuthState.Valid(result.accessToken)
        }

        is GoogleAuthResult.NeedsReauth -> GoogleAuthState.NeedsReauth

        is GoogleAuthResult.Error -> GoogleAuthState.Error(result.message, result.cause)
    }

    /** Oturumu kapatır; token'ı kasadan siler. */
    suspend fun signOut() {
        tokenStore.clear(accountId)
        authClient.revoke()
    }
}

sealed interface GoogleAuthState {
    data class Valid(val accessToken: String) : GoogleAuthState
    data object NeedsReauth : GoogleAuthState
    data class Error(val message: String, val cause: Throwable? = null) : GoogleAuthState
}
