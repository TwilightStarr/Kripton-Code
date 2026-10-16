package com.kriptoncod.app.data.web

import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebStorage
import android.webkit.WebView
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Tüm WebView'lar tek çerez deposunu paylaşır; hesaplar arası izolasyon burada sağlanır. */
object WebUserAgent {
    /** Giriş ve sohbet WebView'ları AYNI kullanıcı-aracısını kullanmalı (oturum/bot kontrolü buna bağlı). */
    const val DESKTOP =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
}

object WebCookies {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Çerezleri ve sayfa depolarını siler (hesap değişiminde önceki hesabın izi kalmasın). */
    suspend fun clearAll() = withContext(Dispatchers.Main) {
        val cm = CookieManager.getInstance()
        suspendCancellableCoroutine<Unit> { c ->
            cm.removeAllCookies { c.resume(Unit) }
        }
        cm.flush()
        WebStorage.getInstance().deleteAllData()
    }

    /** Kaydedilmiş oturumun çerezlerini yükler. */
    suspend fun restore(session: WebSession) = withContext(Dispatchers.Main) {
        val cm = CookieManager.getInstance()
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
    suspend fun snapshot(provider: WebProvider): Map<String, String> = withContext(Dispatchers.Main) {
        val cm = CookieManager.getInstance()
        provider.cookieOrigins.mapNotNull { o -> cm.getCookie(o)?.takeIf { it.isNotBlank() }?.let { o to it } }.toMap()
    }

    suspend fun evalString(webView: WebView, script: String): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<String?> { c ->
            webView.evaluateJavascript(script, ValueCallback { raw -> c.resume(raw) })
        }
    }

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

    suspend fun capture(provider: WebProvider, webView: WebView?): WebSession {
        val ls = if (webView != null) readLocalStorage(webView, provider.localStorageKeys) else emptyMap()
        return WebSession(cookies = snapshot(provider), localStorage = ls)
    }
}
