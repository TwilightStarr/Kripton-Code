package com.kriptoncod.app.data.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.view.View
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
 * Gizli WebView ile gerçek web sohbetini sürer. Çerez deposu süreç geneli tek olduğundan oturumlar
 * bir [Mutex] ile sırayla açılır; her oturum kendi hesabının çerezleriyle başlar ve bittiğinde
 * yenilenen çerezleri geri kaydeder, depoyu temizler.
 */
class WebViewChatDriver(
    context: Context,
    private val sessions: WebSessionStore,
    private val selectorsFile: File? = null,
) : WebChatDriver {

    private val appContext = context.applicationContext
    private val lock = Mutex()

    override suspend fun open(account: AiAccount, provider: WebProvider): WebChatSession {
        val saved = sessions.load(account.id)?.takeIf { !it.isEmpty }
            ?: throw AiGenerationException(Kind.UNAUTHORIZED)
        lock.lock()
        val s = ChatSession(account, provider, WebSelectors.load(provider, selectorsFile))
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
    ) : WebChatSession {

        private var webView: WebView? = null
        private val released = AtomicBoolean(false)
        private var pageLoaded: CompletableDeferred<Unit> = CompletableDeferred()

        suspend fun start(saved: WebSession) {
            WebCookies.clearAll()
            WebCookies.restore(saved)
            withContext(Dispatchers.Main) { webView = createWebView() }
            load(provider.chatUrl)
            if (saved.localStorage.isNotEmpty()) {
                WebCookies.evalString(webView!!, WebScripts.writeLocalStorage(saved.localStorage))
                load(provider.chatUrl) // anahtarlar yazıldı; sayfa onlarla yeniden açılır
            }
        }

        @SuppressLint("SetJavaScriptEnabled")
        private fun createWebView(): WebView {
            val wv = WebView(appContext)
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = WebUserAgent.DESKTOP
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

        override suspend fun download(url: String): WebFile? {
            val wv = webView ?: return null
            if (!allowedUrl(url)) return null
            val js = """
                (function(u){window.__krdl={done:false,b64:'',name:'',err:''};
                fetch(u,{credentials:'include'}).then(function(r){
                  if(!r.ok) throw new Error('http '+r.status);
                  var cd=r.headers.get('content-disposition')||'';
                  var m=cd.match(/filename\*?=(?:UTF-8'')?"?([^";]+)/i); window.__krdl.name=m?decodeURIComponent(m[1]):'';
                  return r.blob();
                }).then(function(b){
                  if(b.size>$MAX_DOWNLOAD_BYTES) throw new Error('too big');
                  var fr=new FileReader();
                  fr.onload=function(){window.__krdl.b64=String(fr.result).split(',')[1]||'';window.__krdl.done=true;};
                  fr.onerror=function(){window.__krdl.err='read';window.__krdl.done=true;};
                  fr.readAsDataURL(b);
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
                val b64 = WebCookies.unquote(WebCookies.evalString(wv, "window.__krdl.b64"))
                val name = WebCookies.unquote(WebCookies.evalString(wv, "window.__krdl.name"))
                val bytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: return null
                return WebFile(name.ifBlank { url.substringBefore('?').substringAfterLast('/').ifBlank { "download" } }, bytes)
            }
            return null
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
                    val snap = WebCookies.capture(provider, wv)
                    // Oturum düştüyse (çerezler boşaldı) eski kaydı ezme; bir sonraki işte UNAUTHORIZED döner.
                    if (!snap.isEmpty) sessions.save(account.id, snap)
                    WebCookies.clearAll()
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
                        runCatching { wv.stopLoading(); wv.destroy() }
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
        const val DOWNLOAD_TIMEOUT_MS = 90_000L
        const val MAX_DOWNLOAD_BYTES = 12 * 1024 * 1024
    }
}
