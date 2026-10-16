package com.kriptoncod.app.data.providers

import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.domain.AuthType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Proxy'nin arkasındaki (yukarı akış) model sağlayıcısı. Çıktı normalizer'ı ve hesabın
 * `providerId` değeri bununla belirlenir. [id], [ProviderCatalog]'daki kimliklerle aynıdır.
 */
enum class UpstreamProvider(val id: String) {
    CLAUDE("claude"),
    GEMINI("gemini"),
    OPENAI("openai"),
    DEEPSEEK("deepseek"),
    KIMI("kimi"),
    OTHER("other"),
    ;

    companion object {
        /** Seçilebilir (adlandırılmış) sağlayıcılar; [OTHER] hariç. */
        val NAMED: List<UpstreamProvider> = listOf(CLAUDE, GEMINI, OPENAI, DEEPSEEK, KIMI)

        fun fromId(id: String?): UpstreamProvider? = entries.firstOrNull { it.id == id }

        /** Önce model kimliğine, sonra `owned_by` ipucuna bakar; hiçbiri tanınmazsa [OTHER]. */
        fun detect(modelId: String, ownedBy: String? = null): UpstreamProvider =
            fromModelId(modelId) ?: fromOwner(ownedBy) ?: OTHER

        /** Yalnız model kimliğinden; tanınmazsa null (ör. "llama-3"). */
        fun fromModelId(modelId: String): UpstreamProvider? {
            val id = modelId.lowercase()
            if (id.isBlank()) return null
            val tokens = id.split(TOKEN_SPLIT).filter { it.isNotEmpty() }
            return when {
                "claude" in id || "anthropic" in id || tokens.any { it in CLAUDE_TOKENS } -> CLAUDE
                "gemini" in id || "gemma" in id -> GEMINI
                "deepseek" in id -> DEEPSEEK
                "kimi" in id || "moonshot" in id -> KIMI
                "gpt" in id || "chatgpt" in id || "codex" in id || tokens.any { O_SERIES.matches(it) } -> OPENAI
                else -> null
            }
        }

        private fun fromOwner(ownedBy: String?): UpstreamProvider? {
            val o = ownedBy?.trim()?.lowercase().orEmpty()
            return when {
                o.isEmpty() -> null
                "anthropic" in o || o == "claude" -> CLAUDE
                "google" in o || "gemini" in o || "antigravity" in o -> GEMINI
                "openai" in o || o == "codex" -> OPENAI
                "deepseek" in o -> DEEPSEEK
                "moonshot" in o || "kimi" in o -> KIMI
                else -> null
            }
        }

        private val TOKEN_SPLIT = Regex("[^a-z0-9]+")
        private val CLAUDE_TOKENS = setOf("opus", "sonnet", "haiku")
        private val O_SERIES = Regex("o[1-9]\\d?")
    }
}

/** Taban adres doğrulama hatası; arayüzde Türkçe iletiye çevrilir. */
enum class ProxyUrlError { EMPTY, MALFORMED, HAS_CREDENTIALS, HAS_QUERY, INSECURE_PUBLIC_HOST }

sealed interface ProxyUrlResult {
    /** [baseUrl] normalleştirilmiştir: sonu `/v1`, sonda eğik çizgi yok. */
    data class Valid(val baseUrl: HttpUrl) : ProxyUrlResult {
        override fun toString(): String = "Valid($baseUrl)"
    }

    data class Invalid(val error: ProxyUrlError) : ProxyUrlResult
}

/**
 * "Proxy (CLIProxyAPI uyumlu)" hesap türü: kullanıcının kendi (ya da arkadaşının) çalıştırdığı
 * CLIProxyAPI benzeri, OpenAI biçimli bir ağ geçidi. `GET <taban>/models`, `POST <taban>/chat/completions`,
 * kimlik doğrulama `Authorization: Bearer <anahtar>`.
 *
 * Güvenlik: düz `http` yalnız yerel/özel ağ adreslerine (localhost, LAN, Tailscale vb.) izinlidir;
 * açık internet adresleri için `https` zorunludur. Adreste kullanıcı bilgisi/sorgu olamaz.
 */
object ProxyProvider {
    const val ID = "proxy"

    fun parseBaseUrl(raw: String): ProxyUrlResult {
        var text = raw.trim()
        if (text.isEmpty()) return ProxyUrlResult.Invalid(ProxyUrlError.EMPTY)
        if (!text.contains("://")) {
            // Şema yazılmadıysa: yerel adresse http, değilse https varsay.
            val host = ("https://$text").toHttpUrlOrNull()?.host
            text = (if (host != null && isPrivateHost(host)) "http://" else "https://") + text
        }
        val url = text.toHttpUrlOrNull() ?: return ProxyUrlResult.Invalid(ProxyUrlError.MALFORMED)
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            return ProxyUrlResult.Invalid(ProxyUrlError.HAS_CREDENTIALS)
        }
        if (url.query != null || url.fragment != null) return ProxyUrlResult.Invalid(ProxyUrlError.HAS_QUERY)
        if (!url.isHttps && !isPrivateHost(url.host)) {
            return ProxyUrlResult.Invalid(ProxyUrlError.INSECURE_PUBLIC_HOST)
        }

        // Kullanıcı ".../v1/chat/completions" ya da ".../v1/models" yapıştırmış olabilir.
        val segments = url.pathSegments.filter { it.isNotEmpty() }.toMutableList()
        if (segments.size >= 2 && segments[segments.size - 2] == "chat" && segments.last() == "completions") {
            repeat(2) { segments.removeAt(segments.lastIndex) }
        } else if (segments.lastOrNull() == "models") {
            segments.removeAt(segments.lastIndex)
        }
        if (segments.lastOrNull() != "v1") segments += "v1"

        val builder = url.newBuilder().encodedPath("/")
        segments.forEach { builder.addPathSegment(it) }
        return ProxyUrlResult.Valid(builder.build())
    }

    /** Normalleştirilmiş taban adres metni; geçersizse null. */
    fun normalizeBaseUrl(raw: String): String? =
        (parseBaseUrl(raw) as? ProxyUrlResult.Valid)?.baseUrl?.toString()

    /** [baseUrl] çözümlenemezse null. [upstream], hesabın `providerId`'si olarak uç noktaya yazılır. */
    fun endpoint(baseUrl: String, upstream: UpstreamProvider? = null): ProviderEndpoint? {
        val base = (parseBaseUrl(baseUrl) as? ProxyUrlResult.Valid)?.baseUrl ?: return null
        fun at(path: String): HttpUrl = base.newBuilder().addPathSegments(path).build()
        return ProviderEndpoint(
            providerId = upstream?.id ?: ID,
            baseUrl = base,
            modelsUrl = at("models"),
            chatUrl = at("chat/completions"),
            validationUrl = at("models"),
            authMethod = ApiKey(),
        )
    }

    /** Ağ sorgusu yapmadan: loopback, özel (RFC 1918), link-local, CGNAT (Tailscale), ULA, .local/.lan, tek etiketli ad. */
    internal fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (h.isEmpty()) return false
        if (h == "localhost" || PRIVATE_SUFFIXES.any { h.endsWith(it) }) return true
        if (':' in h) return isPrivateV6(h)
        parseIpv4(h)?.let { return isPrivateV4(it) }
        return '.' !in h // "mini-pc" gibi tek etiketli ağ adları
    }

    private val PRIVATE_SUFFIXES = listOf(".localhost", ".local", ".lan", ".internal", ".home.arpa")

    private fun parseIpv4(h: String): IntArray? {
        val parts = h.split('.')
        if (parts.size != 4) return null
        val out = IntArray(4)
        for (i in 0..3) {
            val p = parts[i]
            if (p.isEmpty() || p.length > 3 || !p.all { it in '0'..'9' }) return null
            val v = p.toInt()
            if (v > 255) return null
            out[i] = v
        }
        return out
    }

    private fun isPrivateV4(a: IntArray): Boolean = when {
        a[0] == 10 || a[0] == 127 -> true
        a[0] == 172 && a[1] in 16..31 -> true
        a[0] == 192 && a[1] == 168 -> true
        a[0] == 169 && a[1] == 254 -> true
        a[0] == 100 && a[1] in 64..127 -> true
        else -> false
    }

    private fun isPrivateV6(h: String): Boolean {
        if (h == "::1") return true
        val first = h.substringBefore(':')
        if (first.length != 4) return false
        return first.startsWith("fc") || first.startsWith("fd") || first.take(3) in setOf("fe8", "fe9", "fea", "feb")
    }
}

/** Hesaptan istek atılacak uç noktayı çözer: proxy hesabında kendi taban adresi, diğerlerinde katalog. */
object AccountEndpoints {
    fun resolve(account: AiAccount): ProviderEndpoint? =
        if (account.authType == AuthType.PROXY) {
            ProxyProvider.endpoint(account.baseUrl.orEmpty(), UpstreamProvider.fromId(account.providerId))
        } else {
            ProviderCatalog.byId(account.providerId)?.resolve()
        }
}
