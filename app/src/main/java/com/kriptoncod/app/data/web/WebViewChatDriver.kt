package com.kriptoncod.app.data.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.view.View
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
) : WebChatDriver {

    private val appContext = context.applicationContext
    private val globalLock = Mutex()
    private val accountLocks = ConcurrentHashMap<Long, Mutex>()

    /** Bu cihazın WebView'ı hesap başına ayrı profili destekliyor mu (ayrı çerez deposu = gerçek eşzamanlılık). */
    private fun profilesSupported(): Boolean = runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false)

    private fun profileName(accountId: Long) = "kripton-acc-$accountId"

    override suspend fun open(account: AiAccount, provider: WebProvider): WebChatSession = openAt(account, provider, provider.chatUrl)

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

    private suspend fun openAt(account: AiAccount, provider: WebProvider, startUrl: String): WebChatSession {
        val saved = sessions.load(account.id)?.takeIf { !it.isEmpty }
            ?: throw AiGenerationException(Kind.UNAUTHORIZED)
        val profiled = profilesSupported()
        val lock = if (profiled) accountLocks.getOrPut(account.id) { Mutex() } else globalLock
        lock.lock()
        val s = ChatSession(account, provider, WebSelectors.load(provider, selectorsFile), lock, profiled, startUrl)
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
    ) : WebChatSession {

        private var cookieManager: CookieManager = CookieManager.getInstance()
        private var webStorage: WebStorage = WebStorage.getInstance()

        private var webView: WebView? = null
        private val released = AtomicBoolean(false)
        private var pageLoaded: CompletableDeferred<Unit> = CompletableDeferred()

        private var base: WebSession? = null

        suspend fun start(saved: WebSession) {
            base = saved
            withContext(Dispatchers.Main) {
                // WebView oluşturulur oluşturulmaz (herhangi bir yükleme öncesi) hesabın profiline bağlanır.
                val wv = createWebView()
                if (profiled) {
                    val name = profileName(account.id)
                    val profile = ProfileStore.getInstance().getOrCreateProfile(name)
                    WebViewCompat.setProfile(wv, name)
                    cookieManager = profile.cookieManager
                    webStorage = profile.webStorage
                }
                webView = wv
            }
            WebCookies.clearAll(cookieManager, webStorage)
            WebCookies.restore(saved, cookieManager)
            load(startUrl)
            if (saved.localStorage.isNotEmpty()) {
                WebCookies.evalString(webView!!, WebScripts.writeLocalStorage(saved.localStorage))
                load(startUrl) // anahtarlar yazıldı; sayfa onlarla yeniden açılır
            }
        }

        @SuppressLint("SetJavaScriptEnabled")
        private fun createWebView(): WebView {
            val wv = WebView(appContext)
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
            // Ekran dışı ama ölçülü: getBoundingClientRect / görünürlük kontrolleri çalışsın.
            wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            wv.measure(
                View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY),
            )
            wv.layout(0, 0, 1280, 2000)
            wv.onResume()
            wv.resumeTimers()
            return wv
        }

        private suspend fun load(url: String) {
            pageLoaded = CompletableDeferred()
            withContext(Dispatchers.Main) { webView!!.loadUrl(url) }
            withTimeoutOrNull(PAGE_LOAD_TIMEOUT_MS) { pageLoaded.await() }
                ?: throw AiGenerationException(Kind.NETWORK)
            delay(SETTLE_MS) // SPA'nın giriş alanını çizmesi için kısa pay
        }

        override suspend fun send(prompt: String): WebReply {
            val wv = webView ?: return WebReply(WebReply.Status.ERROR)
            WebCookies.evalString(wv, WebScripts.runChat(selectors, prompt))
            return pollReply(wv)
        }

        override suspend fun peekLast(): WebReply? {
            val wv = webView ?: return null
            WebCookies.evalString(wv, WebScripts.peekChat(selectors))
            return pollReply(wv)
        }

        private suspend fun pollReply(wv: WebView): WebReply {
            val deadline = System.currentTimeMillis() + selectors.timeoutMs + 150_000L
            while (System.currentTimeMillis() < deadline) {
                delay(POLL_MS)
                val raw = WebCookies.evalString(wv, WebScripts.POLL)
                val text = WebCookies.unquote(raw)
                if (text.isEmpty()) continue
                if (text == "LOST") return WebReply(WebReply.Status.ERROR)
                return ReplyParser.parse(text)
            }
            return WebReply(WebReply.Status.TIMEOUT)
        }

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
                    if (!snap.isEmpty) sessions.save(account.id, merged)
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
            try {
                if (wv != null) {
                    // Ana iş parçacığında yok et; çerez deposunu hemen temizle (hesaplar karışmasın).
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        runCatching {
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
        const val DOWNLOAD_TIMEOUT_MS = 30L * 60_000L // yalnız ağdan inmesi için; takılmayı sonlandırır
        const val CHUNK_BYTES = 1L * 1024 * 1024
        const val CHUNK_TIMEOUT_MS = 60_000L
        const val ATTACH_CHUNK_BYTES = 96 * 1024
        const val ATTACH_TIMEOUT_MS = 6L * 60_000L
    }
}
