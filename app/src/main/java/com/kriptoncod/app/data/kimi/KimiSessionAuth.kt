package com.kriptoncod.app.data.kimi

import com.kriptoncod.app.data.providers.AuthMethod
import com.kriptoncod.app.domain.AuthType
import okhttp3.Request

class KimiSessionAuth(
    private val tokenStore: KimiTokenStore,
    private val authClient: KimiAuthClient,
    private val accountId: Long,
    private val loginUrl: String,
    private val revokeUrl: String? = null,
) : AuthMethod {

    override val type: AuthType = AuthType.KIMI
    override val isAvailable: Boolean = true

    override fun authorize(request: Request.Builder, credential: String): Request.Builder =
        request.header("Authorization", "Bearer $credential")

    suspend fun loadToken(): String? = tokenStore.getValid(accountId)?.sessionToken

    suspend fun silentRefreshIfNeeded(): KimiAuthState {
        tokenStore.getValid(accountId)?.let { return KimiAuthState.Valid(it.sessionToken) }

        val expired = tokenStore.getAny(accountId)
        val refreshToken = expired?.refreshToken ?: return KimiAuthState.NeedsReauth

        return when (val r = authClient.silentRefresh(refreshUrl(), refreshToken)) {
            is KimiAuthResult.Success -> {
                tokenStore.save(accountId, r.sessionToken, r.expiresAtMs, r.refreshToken ?: refreshToken)
                KimiAuthState.Valid(r.sessionToken)
            }
            is KimiAuthResult.NeedsReauth -> KimiAuthState.NeedsReauth
            is KimiAuthResult.Error -> KimiAuthState.Error(r.message, r.cause)
        }
    }

    suspend fun signIn(email: String, password: String): KimiAuthState {
        return when (val r = authClient.login(loginUrl, email, password)) {
            is KimiAuthResult.Success -> {
                tokenStore.save(accountId, r.sessionToken, r.expiresAtMs, r.refreshToken)
                KimiAuthState.Valid(r.sessionToken)
            }
            is KimiAuthResult.NeedsReauth -> KimiAuthState.NeedsReauth
            is KimiAuthResult.Error -> KimiAuthState.Error(r.message, r.cause)
        }
    }

    suspend fun signOut() {
        val token = tokenStore.getAny(accountId)?.sessionToken ?: ""
        tokenStore.clear(accountId)
        authClient.revoke(revokeUrl, token)
    }

    private fun refreshUrl(): String {
        val base = loginUrl.trimEnd('/')
        return if (base.endsWith("/login")) base.substringBeforeLast("/login") + "/refresh"
        else "$base/refresh"
    }
}

sealed interface KimiAuthState {
    data class Valid(val sessionToken: String) : KimiAuthState
    data object NeedsReauth : KimiAuthState
    data class Error(val message: String, val cause: Throwable? = null) : KimiAuthState
}
