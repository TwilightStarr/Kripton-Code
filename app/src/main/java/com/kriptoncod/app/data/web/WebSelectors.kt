package com.kriptoncod.app.data.web

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Bir sağlayıcının web arayüzünde kullanılan CSS seçicileri. Web arayüzleri sık değiştiği için
 * hepsi tek yerde toplandı ve cihazdaki `web-selectors.json` ile (uygulamayı güncellemeden)
 * sağlayıcı bazında ezilebilir. VARSAYILANLAR CANLI SİTEYE KARŞI DOĞRULANMADI; bozulurlarsa
 * ilk bakılacak yer burasıdır.
 */
@Serializable
data class SiteSelectors(
    val input: List<String>,
    val send: List<String>,
    val stop: List<String>,
    val assistant: List<String>,
    /** Hata/uyarı/toast alanları; kota iletisi buralarda aranır. */
    val alerts: List<String> = listOf("[role=alert]", "[role=status]", "[class*=toast]", "[class*=Toast]"),
    /** İndirme bağlantısı sayılacak href kalıpları (düzenli ifade). */
    val downloadHref: List<String> = listOf("\\.zip(\\?|$)", "/download", "/files/", "interpreter/download", "^blob:"),
    /** Yanıt kesildiğinde görünen "devam et" düğmesi metni (düzenli ifade). */
    val continueText: String = "^\\s*(continue|devam|继续)",
    /** Bu kalıplardan biri uyarı alanında geçerse kota dolmuştur (düzenli ifade, küçük harf). */
    val limitPatterns: List<String> = listOf(
        "usage limit", "message limit", "rate limit", "too many requests", "reached (your|the) limit",
        "limit reached", "out of (free )?messages", "kota", "çok fazla istek", "已达上限", "额度", "请求过于频繁",
    ),
    /** Giriş alanı bulunamazsa oturum düşmüş sayılan URL parçaları. */
    val loginUrlHints: List<String> = listOf("/login", "/auth", "signin", "accounts.google.com", "/sign-in"),
    val timeoutMs: Long = 12 * 60 * 1000L,
)

object WebSelectors {

    fun defaults(provider: WebProvider): SiteSelectors = when (provider) {
        WebProvider.CLAUDE -> SiteSelectors(
            input = listOf("div.ProseMirror[contenteditable=true]", "[data-testid=chat-input]", "[contenteditable=true]"),
            send = listOf("button[aria-label='Send message']", "button[aria-label*='Send']"),
            stop = listOf("button[aria-label='Stop response']", "button[aria-label*='Stop']"),
            assistant = listOf("[data-testid=assistant-message]", ".font-claude-response", "[data-is-streaming]"),
            downloadHref = listOf("\\.zip(\\?|$)", "/download", "/files/", "^blob:"),
        )
        WebProvider.CHATGPT -> SiteSelectors(
            input = listOf("#prompt-textarea", "div.ProseMirror[contenteditable=true]", "textarea"),
            send = listOf("button[data-testid=send-button]", "button[aria-label*='Send']"),
            stop = listOf("button[data-testid=stop-button]", "button[aria-label*='Stop']"),
            assistant = listOf("div[data-message-author-role=assistant]"),
            downloadHref = listOf("interpreter/download", "/files/", "\\.zip(\\?|$)", "^sandbox:", "/download"),
        )
        WebProvider.KIMI -> SiteSelectors(
            input = listOf("div.chat-input-editor[contenteditable=true]", "[contenteditable=true]", "textarea"),
            send = listOf("[data-testid=msh-chatinput-send-button]", "div.send-button-container", "button[class*=send]"),
            stop = listOf("[class*=stop-button]", "[class*=stop]"),
            assistant = listOf(".segment-assistant", "[class*=markdown]", "[class*=assistant]"),
        )
        // Gemini web arayüzü kullanılmaz; yalnız AI Studio anahtar sayfası okunur.
        WebProvider.GEMINI -> SiteSelectors(input = emptyList(), send = emptyList(), stop = emptyList(), assistant = emptyList())
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

    /** Cihazdaki `web-selectors.json` (sağlayıcı kimliği -> SiteSelectors) varsa varsayılanı ezer. */
    fun load(provider: WebProvider, overrideFile: File?): SiteSelectors {
        val base = defaults(provider)
        val f = overrideFile?.takeIf { it.isFile && it.length() > 0 } ?: return base
        return runCatching {
            val map = json.decodeFromString<Map<String, SiteSelectors>>(f.readText())
            map[provider.id] ?: base
        }.getOrDefault(base)
    }

    /** Dışa aktarma/düzenleme için tüm varsayılanlar. */
    fun defaultsJson(): String =
        json.encodeToString(WebProvider.entries.filter { it.output != OutputStyle.API_KEY }.associate { it.id to defaults(it) })
}
