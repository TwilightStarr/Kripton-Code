package com.kriptoncod.app.data.providers

import com.kriptoncod.app.domain.AuthType
import okhttp3.Request

/** Bir sağlayıcıya kimlik doğrulama yöntemi. Kimlik bilgisi asla bu nesnede saklanmaz. */
interface AuthMethod {
    val type: AuthType

    /** Yöntem şu an kullanılabilir mi. */
    val isAvailable: Boolean

    fun authorize(request: Request.Builder, credential: String): Request.Builder
}

/** API anahtarı başlıkla gönderilir. Varsayılan: `Authorization: Bearer <anahtar>`. */
data class ApiKey(
    val headerName: String = "Authorization",
    val scheme: String? = "Bearer",
) : AuthMethod {
    override val type: AuthType = AuthType.API_KEY
    override val isAvailable: Boolean = true

    override fun authorize(request: Request.Builder, credential: String): Request.Builder =
        request.header(headerName, if (scheme.isNullOrEmpty()) credential else "$scheme $credential")
}

/**
 * Google OAuth yöntemi — yalnız Gemini'de.
 * Gerçek uygulama [com.kriptoncod.app.data.google.GeminiGoogleAuth]'dadır;
 * bu nesne sağlayıcı kataloğunda yer tutar.
 */
data object Google : AuthMethod {
    override val type: AuthType = AuthType.GOOGLE
    override val isAvailable: Boolean = true

    override fun authorize(request: Request.Builder, credential: String): Request.Builder =
        request.header("Authorization", "Bearer $credential")
}

/**
 * E-posta/şifre oturum açma yöntemi — Claude (Anthropic Console) ve ChatGPT (OpenAI).
 * Gerçek uygulama [com.kriptoncod.app.data.email.EmailPasswordAuth]'dadır;
 * bu nesne sağlayıcı kataloğunda yer tutar.
 */
data object EmailPassword : AuthMethod {
    override val type: AuthType = AuthType.EMAIL
    override val isAvailable: Boolean = true

    override fun authorize(request: Request.Builder, credential: String): Request.Builder =
        request.header("Authorization", "Bearer $credential")
}
