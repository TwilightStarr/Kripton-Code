package com.kriptoncod.app.data.providers

import androidx.annotation.StringRes
import com.kriptoncod.app.R
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Çözümlenmiş, istek atmaya hazır uç noktalar. */
data class ProviderEndpoint(
    val providerId: String,
    val baseUrl: HttpUrl,
    val modelsUrl: HttpUrl,
    val chatUrl: HttpUrl,
    /** Anahtarı doğrulayan istek (çoğu sağlayıcıda modelsUrl ile aynıdır). */
    val validationUrl: HttpUrl,
    val authMethod: AuthMethod,
    /** Ücretsiz modelleri belirten model kimliği eki (ör. OpenRouter ":free"). */
    val freeModelMarker: String? = null,
)

data class Provider(
    val id: String,
    @StringRes val nameRes: Int,
    /** Sabit taban adres; özel sağlayıcıda null (adres kullanıcıdan alınır). */
    val baseUrl: String?,
    val hasFreeTier: Boolean,
    /** Anahtar alma sayfası; özel sağlayıcıda null. */
    val keyUrl: String?,
    val authMethod: AuthMethod = ApiKey(),
    /**
     * Sağlayıcı Google OAuth destekliyor mu (yalnız Gemini).
     * true ise hesap ekranında "Google ile bağlan" kartı gösterilir.
     */
    val supportsGoogleAuth: Boolean = false,
    val modelsPath: String = "models",
    val chatPath: String = "chat/completions",
    val validationPath: String = modelsPath,
    val freeModelMarker: String? = null,
) {
    val isCustom: Boolean get() = baseUrl == null

    /**
     * Uç noktaları üretir. Özel sağlayıcıda [customBaseUrl] zorunludur; sabit sağlayıcıda yok sayılır.
     * Yalnız https kabul edilir; kullanıcı bilgisi/sorgu/parça içeren adresler reddedilir → null.
     */
    fun resolve(customBaseUrl: String? = null): ProviderEndpoint? {
        val raw = baseUrl ?: customBaseUrl ?: return null
        val url = raw.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
        if (!url.isHttps || url.query != null || url.fragment != null ||
            url.username.isNotEmpty() || url.password.isNotEmpty()
        ) {
            return null
        }
        fun at(path: String): HttpUrl = url.newBuilder().addPathSegments(path).build()
        return ProviderEndpoint(
            providerId = id,
            baseUrl = url,
            modelsUrl = at(modelsPath),
            chatUrl = at(chatPath),
            validationUrl = at(validationPath),
            authMethod = authMethod,
            freeModelMarker = freeModelMarker,
        )
    }
}

/** Desteklenen sağlayıcılar. Hepsi OpenAI uyumlu /models ve /chat/completions sunar. */
object ProviderCatalog {
    const val CUSTOM_ID = "custom"
    const val GEMINI_ID = "gemini"

    val all: List<Provider> = listOf(
        Provider(
            id = GEMINI_ID,
            nameRes = R.string.provider_gemini,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            hasFreeTier = true,
            keyUrl = "https://aistudio.google.com/apikey",
            authMethod = Google,
            supportsGoogleAuth = true,
        ),
        Provider(
            id = "groq",
            nameRes = R.string.provider_groq,
            baseUrl = "https://api.groq.com/openai/v1",
            hasFreeTier = true,
            keyUrl = "https://console.groq.com/keys",
        ),
        Provider(
            id = "openrouter",
            nameRes = R.string.provider_openrouter,
            baseUrl = "https://openrouter.ai/api/v1",
            hasFreeTier = true,
            keyUrl = "https://openrouter.ai/settings/keys",
            validationPath = "key",
            freeModelMarker = ":free",
        ),
        Provider(
            id = "mistral",
            nameRes = R.string.provider_mistral,
            baseUrl = "https://api.mistral.ai/v1",
            hasFreeTier = true,
            keyUrl = "https://console.mistral.ai/api-keys",
        ),
        Provider(
            id = "cerebras",
            nameRes = R.string.provider_cerebras,
            baseUrl = "https://api.cerebras.ai/v1",
            hasFreeTier = true,
            keyUrl = "https://cloud.cerebras.ai",
        ),
        Provider(
            id = "deepseek",
            nameRes = R.string.provider_deepseek,
            baseUrl = "https://api.deepseek.com",
            hasFreeTier = false,
            keyUrl = "https://platform.deepseek.com/api_keys",
        ),
        Provider(
            id = CUSTOM_ID,
            nameRes = R.string.provider_custom,
            baseUrl = null,
            hasFreeTier = false,
            keyUrl = null,
        ),
    )

    fun byId(id: String): Provider? = all.firstOrNull { it.id == id }
}
