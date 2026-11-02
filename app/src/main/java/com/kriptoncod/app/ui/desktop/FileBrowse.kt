package com.kriptoncod.app.ui.desktop

import java.io.File
import java.util.Locale

// Masaüstü dosya yöneticisinin saf mantığı (java.io dışında hiçbir şeye bağlı değil; birim testle doğrulanır).

/** Listedeki bir satır. */
data class FileEntry(
    val file: File,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
)

object FileBrowse {
    /** Yüklenebilecek en büyük dosya (bayt). Sayfaya base64 parçalarla taşındığı için sınırlıdır. */
    const val MAX_UPLOAD_BYTES = 30L * 1024 * 1024

    /**
     * [dir] içeriği: önce klasörler, sonra dosyalar; her grup ada göre (büyük/küçük harf duyarsız).
     * Okunamayan klasör boş liste verir (çökme yok). [showHidden] false iken noktayla başlayanlar gizlenir.
     */
    fun list(dir: File, showHidden: Boolean): List<FileEntry> {
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return emptyList()
        return children.asSequence()
            .filter { showHidden || !it.name.startsWith(".") }
            .map { f ->
                val d = runCatching { f.isDirectory }.getOrDefault(false)
                FileEntry(f, f.name, d, if (d) 0L else runCatching { f.length() }.getOrDefault(0L), runCatching { f.lastModified() }.getOrDefault(0L))
            }
            .sortedWith(compareByDescending<FileEntry> { it.isDir }.thenBy { it.name.lowercase(Locale.ROOT) })
            .toList()
    }

    /** [dir]'in bir üst klasörü; [root]'tan yukarı çıkmaz (null = zaten kökte). */
    fun parentWithin(dir: File, root: File): File? {
        val d = canonical(dir)
        val r = canonical(root)
        if (d == r) return null
        val p = d.parentFile ?: return null
        return if (isInside(p, r)) p else null
    }

    /** [dir] [root]'un içinde (ya da kendisi) mi? */
    fun isInside(dir: File, root: File): Boolean {
        val d = canonical(dir).path
        val r = canonical(root).path
        return d == r || d.startsWith(r.trimEnd('/') + "/")
    }

    /** Konum çubuğu: kökten [dir]'e kadar her klasör için (görünen ad, klasör). Kök için [rootLabel] kullanılır. */
    fun breadcrumbs(dir: File, root: File, rootLabel: String): List<Pair<String, File>> {
        val r = canonical(root)
        var cur: File? = canonical(dir)
        val out = ArrayList<Pair<String, File>>()
        while (cur != null && isInside(cur, r)) {
            out.add(0, (if (cur == r) rootLabel else cur.name) to cur)
            if (cur == r) break
            cur = cur.parentFile
        }
        if (out.isEmpty()) out.add(rootLabel to r)
        return out
    }

    /** 1536 -> "1,5 KB" (yerel ayardan bağımsız, her zaman virgül). */
    fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = -1
        while (v >= 1024 && i < units.lastIndex) {
            v /= 1024
            i++
        }
        val tenths = Math.round(v * 10).toInt()
        val text = if (tenths % 10 == 0 || tenths >= 1000) (tenths / 10).toString() else "${tenths / 10},${tenths % 10}"
        return "$text ${units[i]}"
    }

    /** "rapor.PDF" -> "pdf"; uzantısız ya da ".gizli" gibi noktayla başlayan adlarda "". */
    fun extension(name: String): String {
        val i = name.lastIndexOf('.')
        return if (i <= 0 || i == name.lastIndex) "" else name.substring(i + 1).lowercase(Locale.ROOT)
    }

    /** [showExtension] false iken uzantıyı gizler ("a.kt" -> "a"); klasör adına dokunmaz. */
    fun displayName(entry: FileEntry, showExtension: Boolean): String {
        if (showExtension || entry.isDir || extension(entry.name).isEmpty()) return entry.name
        return entry.name.substring(0, entry.name.lastIndexOf('.'))
    }

    /** Simge rozeti: uzantının ilk 3 harfi, büyük harf ("kotlin" -> "KOT"). */
    fun badge(name: String): String = extension(name).take(3).uppercase(Locale.ROOT)

    /** Uzantıdan MIME türü; bilinmiyorsa genel ikili tür. */
    fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "txt", "log", "kt", "kts", "java", "py", "js", "ts", "sh", "gradle", "toml", "yml", "yaml", "xml", "ini", "cfg" -> "text/plain"
        "md", "markdown" -> "text/markdown"
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        else -> "application/octet-stream"
    }

    /** Dosyayı yüklemek için okur; klasör, okunamayan, boş ya da [maxBytes]'tan büyük dosyada null. */
    fun readForUpload(file: File, maxBytes: Long = MAX_UPLOAD_BYTES): UploadFile? {
        if (!file.isFile || !file.canRead()) return null
        val len = file.length()
        if (len <= 0L || len > maxBytes) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return UploadFile(file.name, mimeFor(file.name), bytes)
    }

    /** Akıştan (ör. başka uygulamanın bıraktığı içerik) okur; [maxBytes] aşılırsa null. */
    fun readStream(name: String, mime: String?, open: () -> java.io.InputStream?, maxBytes: Long = MAX_UPLOAD_BYTES): UploadFile? {
        val input = runCatching { open() }.getOrNull() ?: return null
        return input.use { s ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = runCatching { s.read(buf) }.getOrDefault(-1)
                if (n < 0) break
                total += n
                if (total > maxBytes) return null
                out.write(buf, 0, n)
            }
            if (total == 0L) null else UploadFile(name, mime?.takeIf { it.isNotBlank() && it != "*/*" } ?: mimeFor(name), out.toByteArray())
        }
    }

    /** Shizuku ile okunan dosya: [FileEntry]'ye eşler ([dir] altındaki öğe). */
    fun fromPriv(dir: File, e: com.kriptoncod.app.data.shizuku.PrivEntry): FileEntry = FileEntry(File(dir, e.name), e.name, e.isDir, e.size, e.modified)

    private fun canonical(f: File): File = runCatching { f.canonicalFile }.getOrDefault(f.absoluteFile)
}

/** Sayfaya verilecek dosya. */
class UploadFile(val name: String, val mime: String, val bytes: ByteArray)
