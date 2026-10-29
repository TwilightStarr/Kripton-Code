package com.kriptoncod.app.data.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.ConcurrentHashMap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kriptoncod.app.data.ai.AiGenerationException
import com.kriptoncod.app.data.ai.AiGenerationException.Kind
import com.kriptoncod.app.data.db.AiAccount
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Gizli WebView ile gerçek web sohbetini sürer.
 *
 * Aynı anda çalışma: WebView sürümü ÇOKLU PROFİLİ destekliyorsa (androidx.webkit MULTI_PROFILE) her hesap kendi
 * profilinde (ayrı çerez + depo) çalışır; farklı hesapların oturumları aynı anda açılabilir, yalnız AYNI hesap
 * sıraya girer. Desteklenmiyorsa eski davranış sürer: süreç geneli tek çerez deposu bir [Mutex] ile sırayla kullanılır.
 * Her oturum kendi hesabının çerezleriyle başlar ve bittiğinde yenilenen çerezleri geri kaydeder, depoyu temizler.
 */
class WebViewChatDriver(
    context: Context,
    private val sessions: WebSessionStore,
    private val selectorsFile: File? = null,
    /** Açık oturumları "Canlı web" ekranına bildirir; null ise izleme yapılmaz. */
    private val monitor: WebMonitor? = null,
) : WebChatDriver {

    private val appContext = context.applicationContext
    private val globalLock = Mutex()
    private val accountLocks = ConcurrentHashMap<Long, Mutex>()

    /** Bu cihazın WebView'ı hesap başına ayrı profili destekliyor mu (ayrı çerez deposu = gerçek eşzamanlılık). */
    private fun profilesSupported(): Boolean = runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false)

    private fun profileName(accountId: Long) = "kripton-acc-$accountId"

    override suspend fun open(account: AiAccount, provider: WebProvider): WebChatSession = openAt(account, provider, provider.chatUrl)

    override suspend fun openInteractive(account: AiAccount, provider: WebProvider): WebChatSession = openAt(account, provider, provider.chatUrl, interactive = true)

    /** [chatUrl] bu sağlayıcıya ait bir https adresiyse O sohbeti açar; değilse yeni sohbet. */
    override suspend fun resume(account: AiAccount, provider: WebProvider, chatUrl: String?): WebChatSession {
        val target = chatUrl?.takeIf { isProviderUrl(provider, it) } ?: provider.chatUrl
        return openAt(account, provider, target)
    }

    private fun isProviderUrl(provider: WebProvider, url: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        val host = uri.host ?: return false
        return uri.scheme == "https" && provider.hostSuffixes.any { host == it || host.endsWith(".$it") }
    }

    private suspend fun openAt(account: AiAccount, provider: WebProvider, startUrl: String, interactive: Boolean = false): WebChatSession {
        val saved = sessions.load(account.id)?.takeIf { !it.isEmpty }
            ?: if (interactive) WebSession() else throw AiGenerationException(Kind.UNAUTHORIZED)
        val profiled = profilesSupported()
        val lock = if (profiled) accountLocks.getOrPut(account.id) { Mutex() } else globalLock
        lock.lock()
        val s = ChatSession(account, provider, WebSelectors.load(provider, selectorsFile), lock, profiled, startUrl, interactive)
        try {
            s.start(saved)
        } catch (t: Throwable) {
            s.release()
            throw t
        }
        return s
    }

    private inner class ChatSession(
        private val account: AiAccount,
        private val provider: WebProvider,
        private val selectors: SiteSelectors,
        private val lock: Mutex,
        private val profiled: Boolean,
        private val startUrl: String,
        /** true: giriş sayfasına düşmek hata sayılmaz (kullanıcı elle giriş yapar). */
        private val interactive: Boolean = false,
    ) : WebChatSession,
        WebMonitorHandle {

        private var cookieManager: CookieManager = CookieManager.getInstance()
        private var webStorage: WebStorage = WebStorage.getInstance()

        private var webView: WebView? = null
        private val released = AtomicBoolean(false)
        private var pageLoaded: CompletableDeferred<Unit> = CompletableDeferred()

        private var base: WebSession? = null

        /** [WebMonitor] kaydı; izleme kapalıysa ya da henüz açılmadıysa -1. */
        private var monitorId: Long = -1L

        private inline fun mon(block: (WebMonitor, Long) -> Unit) {
            val m = monitor
            val id = monitorId
            if (m != null && id >= 0) block(m, id)
        }

        suspend fun start(saved: WebSession) {
            base = saved
            withContext(Dispatchers.Main) {
                // Profil, WebView'a ayar/yükleme gibi HİÇBİR çağrı yapılmadan önce bağlanır (aksi hâlde setProfile reddedilebilir).
                val wv = WebView(appContext)
                if (profiled) {
                    val name = profileName(account.id)
                    val profile = ProfileStore.getInstance().getOrCreateProfile(name)
                    WebViewCompat.setProfile(wv, name)
                    cookieManager = profile.cookieManager
                    webStorage = profile.webStorage
                }
                configureWebView(wv)
                webView = wv
                monitorId = monitor?.open(account.id, account.displayName, provider, this@ChatSession) ?: -1L
            }
            WebCookies.clearAll(cookieManager, webStorage)
            WebCookies.restore(saved, cookieManager)
            load(startUrl)
            if (saved.localStorage.isNotEmpty()) {
                WebCookies.evalString(webView!!, WebScripts.writeLocalStorage(saved.localStorage))
                load(startUrl) // anahtarlar yazıldı; sayfa onlarla yeniden açılır
            }
            // Kayıtlı oturum kabul edilmediyse sayfa giriş ekranına yönlenir: 45 sn giriş alanı aramak yerine hemen bildir.
            if (!interactive && landedOnLogin()) {
                mon { m, id -> m.loginNeeded(id) }
                throw AiGenerationException(Kind.UNAUTHORIZED)
            }
            mon { m, id -> m.ready(id) }
        }

        /** Sayfanın adresi sağlayıcının giriş/oturum sayfası mı (yönlendirme yüklemeyi bitirdikten sonra bakılır). */
        private suspend fun landedOnLogin(): Boolean {
            val wv = webView ?: return false
            val url = withContext(Dispatchers.Main) { wv.url }.orEmpty().lowercase()
            if (url.isEmpty()) return false
            val path = runCatching { java.net.URI(url).path.orEmpty() }.getOrDefault("")
            // Yalnız adres YOLUNA bakılır ("/login", "/auth/..."); ana makine adındaki "auth" yanlış alarm vermesin.
            return selectors.loginUrlHints.any { h -> h.startsWith("/") && (path == h || path.startsWith("$h/") || path.startsWith("$h?")) }
        }

        @SuppressLint("SetJavaScriptEnabled")
        private fun configureWebView(wv: WebView): WebView {
            WebUserAgent.apply(wv.settings)
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mediaPlaybackRequiresUserGesture = true
            }
            wv.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) = Unit
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!pageLoaded.isCompleted) pageLoaded.complete(Unit)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val scheme = request?.url?.scheme.orEmpty()
                    return scheme != "https" && scheme != "about" && scheme != "blob"
                }
            }
            // Ekrana eklenmemiş WebView'da sayfa "gizli" sayılır; görünür gösterilir (bkz. WebScripts.VISIBILITY_SHIM).
            runCatching {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    WebViewCompat.addDocumentStartJavaScript(wv, WebScripts.VISIBILITY_SHIM, setOf("*"))
                }
            }
            // Ekran dışı ama ölçülü: getBoundingClientRect / görünürlük kontrolleri çalışsın.
            wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            layoutOffscreen(wv)
            wv.onResume()
            wv.resumeTimers()
            return wv
        }

        /** Ekrana eklenmemiş WebView'ı sabit 1280x2000 olarak ölçüp yerleştirir. */
        private fun layoutOffscreen(wv: WebView) {
            wv.measure(
                View.MeasureSpec.makeMeasureSpec(VIEW_W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEW_H, View.MeasureSpec.EXACTLY),
            )
            wv.layout(0, 0, VIEW_W, VIEW_H)
        }

        private suspend fun load(url: String) {
            pageLoaded = CompletableDeferred()
            withContext(Dispatchers.Main) { webView!!.loadUrl(url) }
            withTimeoutOrNull(PAGE_LOAD_TIMEOUT_MS) { pageLoaded.await() }
                ?: throw AiGenerationException(Kind.NETWORK)
            delay(SETTLE_MS) // SPA'nın giriş alanını çizmesi için kısa pay
        }

        override suspend fun send(prompt: String): WebReply {
            val wv = webView ?: return WebReply(WebReply.Status.ERROR, reason = "no_webview")
            mon { m, id -> m.promptSent(id, prompt) }
            WebCookies.evalString(wv, WebScripts.runChat(selectors, prompt))
            return pollReply(wv).also { r -> mon { m, id -> m.replied(id, r) } }
        }

        override suspend fun peekLast(): WebReply? {
            val wv = webView ?: return null
            mon { m, id -> m.promptSent(id, "", peek = true) }
            WebCookies.evalString(wv, WebScripts.peekChat(selectors))
            return pollReply(wv).also { r -> mon { m, id -> m.replied(id, r) } }
        }

        private suspend fun pollReply(wv: WebView): WebReply {
            val deadline = System.currentTimeMillis() + selectors.timeoutMs + 150_000L
            var tick = 0
            while (System.currentTimeMillis() < deadline) {
                delay(POLL_MS)
                val raw = WebCookies.evalString(wv, WebScripts.POLL)
                val text = WebCookies.unquote(raw)
                if (text.isEmpty()) {
                    if (monitor != null && ++tick % LIVE_EVERY == 0) probeLive(wv)
                    continue
                }
                if (text == "LOST") return WebReply(WebReply.Status.ERROR, reason = "lost")
                return ReplyParser.parse(text)
            }
            return WebReply(WebReply.Status.TIMEOUT, reason = "poll_timeout")
        }

        /** Yanıt beklenirken sayfadan yalnız okuma yapar; "AI çıktı veriyor mu" bilgisini izleme ekranına verir. */
        private suspend fun probeLive(wv: WebView) {
            val p = WebLiveProbe.parse(WebCookies.unquote(WebCookies.evalString(wv, WebLiveProbe.script(selectors)))) ?: return
            mon { m, id -> m.live(id, p.generating, p.chars, p.tail) }
        }

        // --- WebMonitorHandle (yalnız ana iş parçacığı) ---

        override fun mount(host: ViewGroup) {
            val wv = webView ?: return
            (wv.parent as? ViewGroup)?.removeView(wv)
            // Sabit 1280x2000: otomasyon masaüstü düzenine göre yazıldı, izleme sırasında sayfa yeniden düzenlenmemeli.
            host.addView(wv, FrameLayout.LayoutParams(VIEW_W, VIEW_H))
        }

        override fun mountResponsive(host: ViewGroup) {
            val wv = webView ?: return
            (wv.parent as? ViewGroup)?.removeView(wv)
            // Elle gezilen sayfa: donanım katmanı akıcı kaydırma verir (ekran dışı otomasyonda yazılım katmanı gerekliydi).
            wv.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            wv.scaleX = 1f
            wv.scaleY = 1f
            host.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        override fun unmount(host: ViewGroup) {
            val wv = webView
            if (wv != null && wv.parent === host) host.removeView(wv)
            if (wv != null && wv.parent == null) {
                wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                wv.scaleX = 1f
                wv.scaleY = 1f
                layoutOffscreen(wv)
            }
        }

        override fun currentUrl(): String? = webView?.url

        override suspend fun chatUrl(): String? {
            val wv = webView ?: return null
            val url = withContext(Dispatchers.Main) { wv.url } ?: return null
            if (!isProviderUrl(provider, url)) return null
            val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
            val path = uri.path.orEmpty().trimEnd('/')
            val home = runCatching { java.net.URI(provider.chatUrl).path.orEmpty() }.getOrDefault("").trimEnd('/')
            // Ana sayfa / "yeni sohbet" sayfası: sohbet henüz oluşmadı.
            if (path.isEmpty() || path == home) return null
            return url.substringBefore('#')
        }

        override suspend fun attach(name: String, bytes: ByteArray): Boolean {
            val wv = webView ?: return false
            if (bytes.isEmpty()) return false
            WebCookies.evalString(wv, WebScripts.ATTACH_BEGIN)
            var off = 0
            while (off < bytes.size) {
                val end = minOf(off + ATTACH_CHUNK_BYTES, bytes.size)
                val b64 = Base64.encodeToString(bytes.copyOfRange(off, end), Base64.NO_WRAP)
                val r = WebCookies.unquote(WebCookies.evalString(wv, WebScripts.attachChunk(b64)))
                if (r != "ok") return false
                off = end
            }
            WebCookies.evalString(wv, WebScripts.attachFinish(selectors, name, "application/zip"))
            val deadline = System.currentTimeMillis() + ATTACH_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                delay(1_000)
                val st = WebCookies.unquote(WebCookies.evalString(wv, WebScripts.ATTACH_POLL))
                if (st.isEmpty()) continue
                return st == "OK"
            }
            return false
        }

        override suspend fun download(url: String): WebFile? {
            val wv = webView ?: return null
            if (!allowedUrl(url)) return null
            // Dosya tek parça base64 olarak taşınmaz (dev dizgi WebView köprüsünü bozar): blob WebView'de
            // tutulur ve parça parça okunur; boyut sınırı yoktur.
            val js = """
                (function(u){window.__krdl={done:false,size:0,name:'',err:'',blob:null};
                fetch(u,{credentials:'include'}).then(function(r){
                  if(!r.ok) throw new Error('http '+r.status);
                  var cd=r.headers.get('content-disposition')||'';
                  var m=cd.match(/filename\*?=(?:UTF-8'')?"?([^";]+)/i); window.__krdl.name=m?decodeURIComponent(m[1]):'';
                  return r.blob();
                }).then(function(b){
                  window.__krdl.blob=b; window.__krdl.size=b.size; window.__krdl.done=true;
                }).catch(function(e){window.__krdl.err=String(e);window.__krdl.done=true;});
                return 'started';})(${Json.encodeToString(url)});
            """.trimIndent()
            WebCookies.evalString(wv, js)
            val end = System.currentTimeMillis() + DOWNLOAD_TIMEOUT_MS
            while (System.currentTimeMillis() < end) {
                delay(700)
                val state = WebCookies.unquote(
                    WebCookies.evalString(wv, "(function(){var d=window.__krdl;return d&&d.done?(d.err?'ERR':'OK'):'';})()"),
                )
                if (state.isEmpty()) continue
                if (state == "ERR") return null
                val size = WebCookies.unquote(WebCookies.evalString(wv, "String(window.__krdl.size)")).toLongOrNull()
                    ?: return null
                val name = WebCookies.unquote(WebCookies.evalString(wv, "window.__krdl.name"))
                val bytes = readBlobInChunks(wv, size) ?: return null
                runCatching { WebCookies.evalString(wv, "window.__krdl=null;window.__krc=null;0") }
                return WebFile(name.ifBlank { url.substringBefore('?').substringAfterLast('/').ifBlank { "download" } }, bytes)
            }
            return null
        }

        /** `window.__krdl.blob`'u [CHUNK_BYTES]'lık dilimlerle okuyup birleştirir; hata olursa null. */
        private suspend fun readBlobInChunks(wv: WebView, size: Long): ByteArray? {
            val out = java.io.ByteArrayOutputStream()
            var offset = 0L
            while (offset < size) {
                val endOff = minOf(offset + CHUNK_BYTES, size)
                WebCookies.evalString(
                    wv,
                    """(function(o,e){window.__krc={done:false,b64:'',err:''};var fr=new FileReader();
                    fr.onload=function(){window.__krc.b64=String(fr.result).split(',')[1]||'';window.__krc.done=true;};
                    fr.onerror=function(){window.__krc.err='read';window.__krc.done=true;};
                    fr.readAsDataURL(window.__krdl.blob.slice(o,e));return 'started';})($offset,$endOff)""",
                )
                val chunkEnd = System.currentTimeMillis() + CHUNK_TIMEOUT_MS
                var b64: String? = null
                while (System.currentTimeMillis() < chunkEnd) {
                    delay(40)
                    val st = WebCookies.unquote(
                        WebCookies.evalString(wv, "(function(){var d=window.__krc;return d&&d.done?(d.err?'ERR':'OK'):'';})()"),
                    )
                    if (st.isEmpty()) continue
                    if (st == "ERR") return null
                    b64 = WebCookies.unquote(WebCookies.evalString(wv, "window.__krc.b64"))
                    break
                }
                val raw = b64 ?: return null
                val bytes = runCatching { Base64.decode(raw, Base64.DEFAULT) }.getOrNull() ?: return null
                out.write(bytes)
                offset = endOff
            }
            return out.toByteArray()
        }

        private fun allowedUrl(url: String): Boolean {
            if (url.startsWith("blob:")) return true
            val host = runCatching { java.net.URI(url) }.getOrNull()?.takeIf { it.scheme == "https" }?.host
                ?: return false
            return provider.hostSuffixes.any { host == it || host.endsWith(".$it") }
        }

        override suspend fun close() {
            try {
                val wv = webView
                if (wv != null) {
                    val snap = WebCookies.capture(provider, wv, cookieManager)
                    // Oturum düştüyse (çerezler boşaldı) eski kaydı ezme; bir sonraki işte UNAUTHORIZED döner.
                    // Elle aktarılan ek localStorage kayıtları (ör. Kimi tokenları) ilk oturumdan sonra silinmesin.
                    val merged = base?.let { b -> snap.copy(localStorage = b.localStorage + snap.localStorage) } ?: snap
                    // Yalnız GİRİŞLİ oturum kaydedilir: girişsiz (anonim) çerezler çalışan eski oturumu ezmesin.
                    if (!snap.isEmpty && (provider.isLoggedIn(merged) || provider.isLoggedIn(base ?: WebSession()).not())) sessions.save(account.id, merged)
                    WebCookies.clearAll(cookieManager, webStorage)
                }
            } finally {
                release()
            }
        }

        fun release() {
            if (!released.compareAndSet(false, true)) return
            val wv = webView
            webView = null
            mon { m, id -> m.close(id) }
            try {
                if (wv != null) {
                    // Ana iş parçacığında yok et; çerez deposunu hemen temizle (hesaplar karışmasın).
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        runCatching {
                            // "Canlı web" ekranında açıksa önce oradan çıkar; ekrana bağlı WebView yok edilmez.
                            (wv.parent as? ViewGroup)?.removeView(wv)
                            wv.stopLoading()
                            wv.destroy()
                        }
                    }
                }
            } finally {
                lock.unlock()
            }
        }
    }

    private companion object {
        const val PAGE_LOAD_TIMEOUT_MS = 60_000L
        const val SETTLE_MS = 2_500L
        const val POLL_MS = 1_500L
        const val LIVE_EVERY = 2 // her 2 yoklamada (~3 sn) bir canlı durum okunur
        const val VIEW_W = 1280
        const val VIEW_H = 2000
        const val DOWNLOAD_TIMEOUT_MS = 30L * 60_000L // yalnız ağdan inmesi için; takılmayı sonlandırır
        const val CHUNK_BYTES = 1L * 1024 * 1024
        const val CHUNK_TIMEOUT_MS = 60_000L
        const val ATTACH_CHUNK_BYTES = 96 * 1024
        const val ATTACH_TIMEOUT_MS = 6L * 60_000L
    }
}
