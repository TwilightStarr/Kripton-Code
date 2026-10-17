package com.kriptoncod.app.data.google

import android.app.Activity
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Üretim uygulaması: Google Identity Services [AuthorizationClient] kullanır.
 * [clientId] yoksa tüm çağrılar [GoogleAuthResult.Error] döner.
 */
class RealGoogleAuthClient(private val clientId: suspend () -> String?) : GoogleAuthClient {

    private val scopes = listOf(Scope(GENAI_SCOPE))

    private fun buildRequest() = AuthorizationRequest.builder()
        .setRequestedScopes(scopes)
        .build()

    private fun client(activity: Activity): AuthorizationClient =
        Identity.getAuthorizationClient(activity)

    override suspend fun authorize(activity: Activity): GoogleAuthResult {
        if (clientId().isNullOrBlank()) {
            return GoogleAuthResult.Error("OAuth istemci kimliği tanımlanmamış")
        }
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                client(activity).authorize(buildRequest())
                    .addOnSuccessListener { result ->
                        val token = result.accessToken
                        if (token != null) {
                            // GIS tokenları genellikle 3600 sn; varsayılan 55 dk yenileme payı
                            val expiresAt = System.currentTimeMillis() + TOKEN_LIFETIME_MS
                            cont.resume(GoogleAuthResult.Success(token, expiresAt))
                        } else if (result.hasResolution()) {
                            // İzin ekranı bu akışta gösterilmiyor; sessizce "yeniden giriş" demek kullanıcıyı
                            // döngüye sokar. Açık hata ver ve güvenilir yola (API anahtarı) yönlendir.
                            cont.resume(
                                GoogleAuthResult.Error(
                                    "Google izin ekranı gerekiyor ve bu yöntemde desteklenmiyor. " +
                                        "Gemini için Google AI Studio'dan API anahtarı alıp yapıştır.",
                                ),
                            )
                        } else {
                            cont.resume(GoogleAuthResult.Error("Erişim belirteci alınamadı"))
                        }
                    }
                    .addOnFailureListener { e ->
                        cont.resume(GoogleAuthResult.Error(e.message ?: "Bilinmeyen hata", e))
                    }
            }
        }
    }

    override suspend fun silentRefresh(): GoogleAuthResult {
        // GIS AuthorizationClient Activity gerektiriyor; Activity bağlamı olmadan çağrılamaz.
        // Token süresi dolmuşsa NeedsReauth döner; UI katmanı yeniden authorize() tetikler.
        return GoogleAuthResult.NeedsReauth
    }

    override suspend fun revoke() {
        // GIS revocation doğrudan Activity gerektirmiyor; no-op kabul edilir.
        // Gerçek iptal için Google OAuth revocation endpoint kullanılabilir.
    }

    companion object {
        /** Token ömrü: 55 dakika (3600 - 300 saniyelik güvenlik payı). */
        internal const val TOKEN_LIFETIME_MS = 55L * 60 * 1000
    }
}
