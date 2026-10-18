package com.kriptoncod.app.data.web

/**
 * Web oturumuyla kullanılabilen sağlayıcılar. [id], [com.kriptoncod.app.data.providers.ProviderCatalog]
 * kimlikleriyle aynıdır (hesabın `providerId`'si).
 *
 * [ToS riskli]: Claude ve Gemini'de hesap kapatma riski uygulama içinde zorunlu uyarıyla gösterilir.
 */
enum class WebProvider(
    val id: String,
    val displayName: String,
    val loginUrl: String,
    val chatUrl: String,
    /** Çerezlerin okunacağı/yazılacağı kökler. */
    val cookieOrigins: List<String>,
    /** Giriş sonrası oturumun açık sayılacağı ana makine ekleri. */
    val hostSuffixes: List<String>,
    /** Uygulama içinde "hesabın kapatılabilir" uyarısı zorunlu mu. */
    val requiresBanWarning: Boolean,
    /** localStorage içinden saklanacak anahtarlar (çerez yetmeyen sağlayıcılar için). */
    val localStorageKeys: List<String> = emptyList(),
    val output: OutputStyle,
    /** Hesabın güç sırası (otomatik atamada yüksek = önce). */
    val powerRank: Int,
) {
    CLAUDE(
        id = "claude",
        displayName = "Claude",
        loginUrl = "https://claude.ai/login",
        chatUrl = "https://claude.ai/new",
        cookieOrigins = listOf("https://claude.ai", "https://www.claude.ai"),
        hostSuffixes = listOf("claude.ai"),
        requiresBanWarning = true,
        output = OutputStyle.ZIP_FILE,
        powerRank = 90,
    ),
    CHATGPT(
        id = "openai",
        displayName = "ChatGPT",
        loginUrl = "https://chatgpt.com/auth/login",
        chatUrl = "https://chatgpt.com/",
        cookieOrigins = listOf("https://chatgpt.com", "https://auth.openai.com", "https://openai.com"),
        hostSuffixes = listOf("chatgpt.com", "openai.com"),
        requiresBanWarning = false,
        output = OutputStyle.ZIP_FILE,
        powerRank = 80,
    ),
    KIMI(
        id = "kimi",
        displayName = "Kimi",
        loginUrl = "https://www.kimi.com/",
        chatUrl = "https://www.kimi.com/",
        cookieOrigins = listOf("https://www.kimi.com", "https://kimi.com"),
        hostSuffixes = listOf("kimi.com", "moonshot.cn"),
        requiresBanWarning = false,
        localStorageKeys = listOf("access_token", "refresh_token"),
        output = OutputStyle.LONG_CODE_BLOCK,
        powerRank = 60,
    ),
    GEMINI(
        id = "gemini",
        displayName = "Gemini",
        loginUrl = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Faistudio.google.com%2Fapikey",
        chatUrl = "https://aistudio.google.com/apikey",
        cookieOrigins = listOf("https://accounts.google.com", "https://aistudio.google.com", "https://google.com"),
        hostSuffixes = listOf("google.com"),
        requiresBanWarning = true,
        output = OutputStyle.API_KEY,
        powerRank = 70,
    ),
    ;

    /**
     * Yakalanan oturum GERÇEKTEN giriş yapılmış bir oturum mu? Yalnız bilinen oturum çerezi/anahtarı varsa true;
     * bilinmiyorsa (ör. Gemini) false döner ve kullanıcı "Girişi tamamladım" düğmesini kullanır.
     * Böylece anonim (girişsiz) bir oturum yanlışlıkla kaydedilmez.
     */
    fun isLoggedIn(session: WebSession): Boolean = when (this) {
        CLAUDE -> session.hasCookie { it == "sessionKey" }
        CHATGPT -> session.hasCookie { it.contains("session-token") }
        KIMI -> !session.localStorage["access_token"].isNullOrBlank()
        GEMINI -> false
    }

    companion object {
        fun fromId(id: String?): WebProvider? = entries.firstOrNull { it.id == id }
    }
}

private fun WebSession.hasCookie(match: (String) -> Boolean): Boolean = cookies.values.any { header -> CookieText.parse(header).any { (name, value) -> value.isNotEmpty() && match(name) } }

/** Sağlayıcının çıktı verme biçimi; çıktı adaptörünü ve istem ekini belirler. */
enum class OutputStyle {
    /** Sohbet dosya/zip üretir: indirme bağlantısı yakalanır, yoksa kod bloklarına düşülür. */
    ZIP_FILE,

    /** Zip üretemez; uzun kod bloğu verir, kesilirse otomatik "devam et" gönderilir. */
    LONG_CODE_BLOCK,

    /** Web arayüzü kullanılmaz; çıktı resmi API anahtarıyla alınır. */
    API_KEY,
}
