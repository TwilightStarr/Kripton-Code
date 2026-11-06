package com.kriptoncod.app.data.web

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.kriptoncod.app.data.http.await
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Web sayfasındaki "indir" düğmelerinin (Claude'un verdiği zip/dosya bağlantıları) indirdiği dosyayı cihazın
 * İndirilenler klasörüne kaydeder. WebView kendiliğinden indirme yapmaz; [android.webkit.DownloadListener] kurulmazsa
 * düğmeye basınca hiçbir şey olmaz. Dosya adı çözümleme işlevleri saf Kotlin'dir (birim testlenebilir).
 */
object WebDownloadSaver {

    private const val MAX_REDIRECTS = 5

    // Yönlendirmeler elle izlenir: çerez her adımda YALNIZ o adresin kendi çerezleriyle gider (başka makineye sızmaz).
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private val STAR_NAME = Regex("""filename\*\s*=\s*([^']*)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE)
    private val PLAIN_NAME = Regex("""filename\s*=\s*(?:"([^"]*)"|([^;]+))""", RegexOption.IGNORE_CASE)
    private val BAD_CHARS = Regex("[\\u0000-\\u001F\"*:<>?|]")

    /** `Content-Disposition` başlığındaki dosya adı; yoksa null. */
    fun filenameFromDisposition(cd: String?): String? {
        if (cd.isNullOrBlank()) return null
        STAR_NAME.find(cd)?.let { m ->
            val charset = m.groupValues[1].ifBlank { "UTF-8" }
            val raw = m.groupValues[2].trim().trim('"')
            val decoded = runCatching { URLDecoder.decode(raw.replace("+", "%2B"), charset) }.getOrNull()
            if (!decoded.isNullOrBlank()) return decoded
        }
        PLAIN_NAME.find(cd)?.let { m ->
            val v = m.groupValues[1].ifEmpty { m.groupValues[2] }.trim()
            if (v.isNotEmpty()) return v
        }
        return null
    }

    /** Yol ayırıcıları ve dosya sisteminde sorunlu karakterleri temizler; boş kalırsa "download". */
    fun sanitizeName(raw: String?): String {
        val cleaned = raw.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(BAD_CHARS, "_")
            .trim()
            .trim('.')
        return cleaned.take(150).ifBlank { "download" }
    }

    /** Adresin son yol parçası (blob:/data: adreslerinde null). */
    private fun nameFromUrl(url: String): String? {
        if (url.startsWith("blob:") || url.startsWith("data:")) return null
        val path = runCatching { java.net.URI(url).path }.getOrNull().orEmpty()
        val last = path.substringAfterLast('/')
        if (last.isBlank()) return null
        return runCatching { URLDecoder.decode(last.replace("+", "%2B"), "UTF-8") }.getOrDefault(last).ifBlank { null }
    }

    private fun extensionFor(mime: String?): String? {
        val m = mime?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (m.isEmpty() || m == "application/octet-stream") return null
        return runCatching { MimeTypeMap.getSingleton().getExtensionFromMimeType(m) }.getOrNull()
    }

    private fun mimeFor(name: String, mime: String?): String {
        val m = mime?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (m.isNotEmpty() && m != "application/octet-stream") return m
        val ext = name.substringAfterLast('.', "").lowercase()
        return runCatching { MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) }.getOrNull() ?: "application/octet-stream"
    }

    /** Dosya adı: başlıktaki ad > adres sonu > "download"; uzantı yoksa içerik türünden eklenir. */
    fun resolveName(url: String, contentDisposition: String?, mime: String?): String {
        var name = sanitizeName(filenameFromDisposition(contentDisposition) ?: nameFromUrl(url))
        if (!name.contains('.')) extensionFor(mime)?.let { name += ".$it" }
        return name
    }

    /** Yazılacak hedef: [out]'a yazılır, bitince [finish] (son görünen adı döner), hata olursa [abort]. */
    private class Target(val out: OutputStream, val finish: () -> String, val abort: () -> Unit)

    private fun openTarget(context: Context, name: String, mime: String): Target {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("insert")
            val out = resolver.openOutputStream(uri) ?: run {
                runCatching { resolver.delete(uri, null, null) }
                throw IOException("open")
            }
            return Target(
                out = out,
                finish = {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                    runCatching {
                        resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { c ->
                            if (c.moveToFirst()) c.getString(0) else null
                        }
                    }.getOrNull() ?: name
                },
                abort = { runCatching { resolver.delete(uri, null, null) } },
            )
        }
        // Android 8-9: genel İndirilenler klasörü yazma izni ister; uygulamanın kendi indirme klasörüne kaydedilir.
        val dir = (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir).also { it.mkdirs() }
        val file = uniqueFile(dir, name)
        return Target(out = file.outputStream().buffered(), finish = { file.name }, abort = { runCatching { file.delete() } })
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var n = 1
        while (f.exists()) f = File(dir, "$base ($n)$ext").also { n++ }
        return f
    }

    private fun writeTo(context: Context, name: String, mime: String, writer: (OutputStream) -> Unit): String {
        val target = openTarget(context, name, mime)
        try {
            writer(target.out)
            target.out.flush()
            target.out.close()
            return target.finish()
        } catch (t: Throwable) {
            runCatching { target.out.close() }
            target.abort()
            throw t
        }
    }

    /** Bellekteki baytları (blob:/data: indirmeleri) kaydeder; kaydedilen dosya adını döner. */
    suspend fun saveBytes(context: Context, name: String, mime: String?, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val safe = sanitizeName(name)
        writeTo(context, safe, mimeFor(safe, mime)) { it.write(bytes) }
    }

    /** `data:` adresini çözüp kaydeder. */
    suspend fun saveDataUrl(context: Context, url: String, contentDisposition: String?, mime: String?): String {
        val comma = url.indexOf(',')
        require(url.startsWith("data:") && comma > 0) { "data url" }
        val meta = url.substring(5, comma)
        val payload = url.substring(comma + 1)
        val bytes = if (meta.endsWith(";base64", ignoreCase = true)) {
            Base64.decode(payload, Base64.DEFAULT)
        } else {
            URLDecoder.decode(payload.replace("+", "%2B"), "UTF-8").toByteArray()
        }
        val type = mime?.takeIf { it.isNotBlank() } ?: meta.substringBefore(';').ifBlank { null }
        return saveBytes(context, resolveName(url, contentDisposition, type), type, bytes)
    }

    /**
     * https adresini oturum çerezleriyle indirip kaydeder. [cookieFor] her adres (yönlendirmeler dahil) için kendi
     * çerezini verir. Kaydedilen dosya adını döner; hata olursa [IOException] fırlatır.
     */
    suspend fun saveHttp(
        context: Context,
        url: String,
        userAgent: String?,
        referer: String?,
        contentDisposition: String?,
        mime: String?,
        cookieFor: (String) -> String?,
    ): String = withContext(Dispatchers.IO) {
        var current = url
        for (hop in 0..MAX_REDIRECTS) {
            val request = Request.Builder().url(current).apply {
                if (!userAgent.isNullOrBlank()) header("User-Agent", userAgent)
                if (!referer.isNullOrBlank()) header("Referer", referer)
                cookieFor(current)?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
            }.build()
            val response = client.newCall(request).await()
            if (response.isRedirect) {
                val next = response.header("Location")?.let { response.request.url.resolve(it)?.toString() }
                response.close()
                current = next ?: throw IOException("redirect")
                continue
            }
            return@withContext response.use { saveResponse(context, it, current, contentDisposition, mime) }
        }
        throw IOException("too many redirects")
    }

    private fun saveResponse(context: Context, response: Response, url: String, cd: String?, mime: String?): String {
        if (!response.isSuccessful) throw IOException("http ${response.code}")
        val body = response.body ?: throw IOException("empty body")
        val disposition = cd?.takeIf { it.isNotBlank() } ?: response.header("Content-Disposition")
        val type = response.header("Content-Type")?.takeIf { it.isNotBlank() } ?: mime
        val name = resolveName(url, disposition, type)
        return writeTo(context, name, mimeFor(name, type)) { out -> body.byteStream().use { it.copyTo(out) } }
    }

    /** Kısa bildirim; her iş parçacığından çağrılabilir. */
    fun toast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            runCatching { Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show() }
        }
    }
}
