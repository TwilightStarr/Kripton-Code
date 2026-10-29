package com.kriptoncod.app.data.web

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebSettings
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import android.webkit.WebStorage
import android.webkit.WebView
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Tüm WebView'lar tek çerez deposunu paylaşır; hesaplar arası izolasyon burada sağlanır. */
object WebUserAgent {
    /**
     * Cihazın WebView'ının sürümü okunamazsa kullanılan Chrome ana sürümü. Siteler eski tarayıcıyı ("This browser is out of date")
     * yalnız kullanıcı-aracısındaki sürümden anlar; bu yüzden sabit eski bir sürüm yerine GERÇEK WebView sürümü kullanılır.
     */
    const val FALLBACK_MAJOR = 142
    private const val MIN_MAJOR = 120
    private val CHROME = Regex("Chrome/(\\d+)")

    /** Giriş ve sohbet WebView'ları AYNI kullanıcı-aracısını kullanmalı (oturum/bot kontrolü buna bağlı). */
    @Volatile
    var DESKTOP: String = desktopUa(FALLBACK_MAJOR)
        private set

    fun desktopUa(major: Int): String = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"

    /** [ua] içindeki Chrome ana sürümü; yoksa ya da gerçekçi değilse [FALLBACK_MAJOR]. */
    fun majorOf(ua: String?): Int {
        val m = ua?.let { CHROME.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: return FALLBACK_MAJOR
        return if (m >= MIN_MAJOR) m else FALLBACK_MAJOR
    }

    /**
     * Giriş ve sohbet WebView'larının ayarı. Google/diğer siteler "uygulama içi tarayıcı"yı üç yerden anlar:
     * kullanıcı-aracısı, `X-Requested-With: <paket adı>` başlığı ve `Sec-CH-UA` ipuçları ("Android WebView").
     * Üçü de masaüstü Chrome ile tutarlı hâle getirilir. Sürüm numarası cihazdaki gerçek WebView'dan alınır.
     * Desteklenmeyen sürümlerde sessizce atlanır.
     */
    @SuppressLint("RestrictedApi")
    fun apply(settings: WebSettings) {
        // Ayar henüz ezilmemişse varsayılan kullanıcı-aracısı gerçek WebView sürümünü taşır.
        val major = majorOf(runCatching { settings.userAgentString }.getOrNull())
        val ua = desktopUa(major)
        DESKTOP = ua
        settings.userAgentString = ua
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
            }
        }
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
                val full = "$major.0.0.0"
                val brands = listOf(
                    UserAgentMetadata.BrandVersion.Builder().setBrand("Chromium").setMajorVersion("$major").setFullVersion(full).build(),
                    UserAgentMetadata.BrandVersion.Builder().setBrand("Google Chrome").setMajorVersion("$major").setFullVersion(full).build(),
                    UserAgentMetadata.BrandVersion.Builder().setBrand("Not?A_Brand").setMajorVersion("99").setFullVersion("99.0.0.0").build(),
                )
                val meta = UserAgentMetadata.Builder()
                    .setBrandVersionList(brands)
                    .setFullVersion(full)
                    .setPlatform("Linux")
                    .setPlatformVersion("6.5.0")
                    .setArchitecture("x86")
                    .setBitness(64)
                    .setModel("")
                    .setMobile(false)
                    .build()
                WebSettingsCompat.setUserAgentMetadata(settings, meta)
            }
        }
    }
}

object WebCookies {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Çerezleri ve sayfa depolarını siler (hesap değişiminde önceki hesabın izi kalmasın). */
    suspend fun clearAll(
        cm: CookieManager = CookieManager.getInstance(),
        storage: WebStorage = WebStorage.getInstance(),
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { c ->
            cm.removeAllCookies { c.resume(Unit) }
        }
        cm.flush()
        storage.deleteAllData()
    }

    /** Kaydedilmiş oturumun çerezlerini yükler. */
    suspend fun restore(session: WebSession, cm: CookieManager = CookieManager.getInstance()) = withContext(Dispatchers.Main) {
        cm.setAcceptCookie(true)
        for ((origin, header) in session.cookies) {
            for ((name, value) in CookieText.parse(header)) {
                val secure = if (origin.startsWith("https")) "; Secure" else ""
                cm.setCookie(origin, "$name=$value; Path=/$secure")
            }
        }
        cm.flush()
    }

    /** Sağlayıcı kökleri için güncel çerezleri okur. */
    suspend fun snapshot(provider: WebProvider, cm: CookieManager = CookieManager.getInstance()): Map<String, String> = withContext(Dispatchers.Main) {
        provider.cookieOrigins.mapNotNull { o -> cm.getCookie(o)?.takeIf { it.isNotBlank() }?.let { o to it } }.toMap()
    }

    /**
     * Sayfada betik çalıştırır. WebView çökmüş/duraklamışsa geri çağrı hiç gelmeyebilir; bu yüzden süre sınırı vardır
     * ve aşılırsa null döner (çağıranlar null'ı "yanıt yok" sayar). Aksi hâlde iş sessizce sonsuza dek takılırdı.
     */
    suspend fun evalString(webView: WebView, script: String): String? = withTimeoutOrNull(EVAL_TIMEOUT_MS) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<String?> { c ->
                webView.evaluateJavascript(script, ValueCallback { raw -> c.resume(raw) })
            }
        }
    }

    private const val EVAL_TIMEOUT_MS = 30_000L

    /** `evaluateJavascript` çıktısındaki JSON metin değerini düz metne çevirir ("null"/boş -> ""). */
    fun unquote(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty() || t == "null") return ""
        return runCatching {
            val el = json.parseToJsonElement(t)
            if (el is JsonPrimitive) el.contentOrNull.orEmpty() else t
        }.getOrDefault(t)
    }

    suspend fun readLocalStorage(webView: WebView, keys: List<String>): Map<String, String> {
        if (keys.isEmpty()) return emptyMap()
        val s = unquote(evalString(webView, WebScripts.readLocalStorage(keys)))
        val obj: JsonObject = runCatching { json.parseToJsonElement(s).jsonObject }.getOrNull() ?: return emptyMap()
        return obj.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }.toMap()
    }

    suspend fun capture(provider: WebProvider, webView: WebView?, cm: CookieManager = CookieManager.getInstance()): WebSession {
        val ls = if (webView != null) readLocalStorage(webView, provider.localStorageKeys) else emptyMap()
        return WebSession(cookies = snapshot(provider, cm), localStorage = ls)
    }
}
