package com.kriptoncod.app.data.google

import android.app.Activity

/**
 * Google Authorization API'sinin test edilebilir soyutlaması.
 * Üretimde [RealGoogleAuthClient], testlerde [FakeGoogleAuthClient] kullanılır.
 *
 * Kapsam: Generative Language API için yalnızca gerekli en dar kapsam.
 */
interface GoogleAuthClient {
    /** Oturum başlatır ya da yeniler; erişim belirtecini döndürür. */
    suspend fun authorize(activity: Activity): GoogleAuthResult

    /**
     * Arka planda sessiz yenileme. Kullanıcı etkileşimi gerekmiyorsa belirteci döndürür,
     * gerekirse [GoogleAuthResult.NeedsReauth] döner (UI yönlendirmesi gerekir).
     */
    suspend fun silentRefresh(): GoogleAuthResult

    /** Oturumu kapatır / izni iptal eder. */
    suspend fun revoke()
}

sealed interface GoogleAuthResult {
    /** Başarılı; [accessToken] geçerli erişim belirteci, [expiresAtMs] epoch-ms bitiş zamanı. */
    data class Success(val accessToken: String, val expiresAtMs: Long) : GoogleAuthResult

    /** Sessiz yenileme yetersiz; kullanıcı yeniden giriş yapmalı. */
    data object NeedsReauth : GoogleAuthResult

    /** Hata; [message] günlük için. */
    data class Error(val message: String, val cause: Throwable? = null) : GoogleAuthResult
}

/** Generative Language API için en dar kapsam. */
internal const val GENAI_SCOPE = "https://www.googleapis.com/auth/generative-language"
