package com.kriptoncod.app.ui.desktop

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

/** Dosya türüne göre açma yolu. */
enum class FileKind { TEXT, MARKDOWN, PDF, IMAGE, ARCHIVE, OTHER }

/**
 * Dosya yöneticisi işlemleri (kopyala/taşı/yeniden adlandır/sil/özet). Saf java.io; birim testle doğrulanır.
 * Hata durumunda istisna fırlatmaz, [Result.failure] verir.
 */
object FileOps {
    const val MAX_TEXT_BYTES = 512 * 1024

    private val TEXT = setOf(
        "txt", "log", "json", "xml", "csv", "kt", "kts", "java", "py", "js", "ts", "sh", "gradle", "toml", "yml", "yaml",
        "ini", "cfg", "properties", "html", "htm", "css", "sql", "conf", "gitignore", "pro",
    )
    private val IMAGE = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    private val ARCHIVE = setOf("zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz")

    fun kindOf(name: String): FileKind {
        val lower = name.lowercase(Locale.ROOT)
        if (lower.endsWith(".tar.gz") || lower.endsWith(".tar.xz") || lower.endsWith(".tar.bz2")) return FileKind.ARCHIVE
        return when (val e = FileBrowse.extension(name)) {
            "md", "markdown" -> FileKind.MARKDOWN
            "pdf" -> FileKind.PDF
            in IMAGE -> FileKind.IMAGE
            in ARCHIVE -> FileKind.ARCHIVE
            in TEXT -> FileKind.TEXT
            else -> if (e.isEmpty() && name.startsWith(".")) FileKind.TEXT else FileKind.OTHER
        }
    }

    /** [dir] içinde [name] zaten varsa "ad (1).uzantı", "ad (2).uzantı"… */
    fun uniqueTarget(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val (base, ext) = if (dot <= 0) name to "" else name.substring(0, dot) to name.substring(dot)
        var i = 1
        while (f.exists()) {
            f = File(dir, "$base ($i)$ext")
            i++
        }
        return f
    }

    private fun intoItself(src: File, dstDir: File): Boolean =
        src.isDirectory && FileBrowse.isInside(dstDir, src)

    fun copy(src: File, dstDir: File): Result<File> = runCatching {
        require(src.exists()) { "Kaynak yok" }
        require(dstDir.isDirectory) { "Hedef klasör değil" }
        require(!intoItself(src, dstDir)) { "Klasör kendi içine kopyalanamaz" }
        val target = uniqueTarget(dstDir, src.name)
        if (src.isDirectory) src.copyRecursively(target, overwrite = false) else src.copyTo(target, overwrite = false)
        check(target.exists()) { "Kopyalanamadı" }
        target
    }

    fun move(src: File, dstDir: File): Result<File> = runCatching {
        require(src.exists()) { "Kaynak yok" }
        require(dstDir.isDirectory) { "Hedef klasör değil" }
        require(!intoItself(src, dstDir)) { "Klasör kendi içine taşınamaz" }
        if (src.parentFile?.canonicalFile == dstDir.canonicalFile) return@runCatching src
        val target = uniqueTarget(dstDir, src.name)
        if (!src.renameTo(target)) {
            // Farklı bölümler arası taşıma: önce kopyala, doğrula, sonra sil.
            copy(src, dstDir).getOrThrow().also { copied ->
                check(copied.exists()) { "Taşınamadı" }
                check(delete(src)) { "Kaynak silinemedi (kopya hedefte duruyor)" }
            }
        } else {
            target
        }
    }

    /** Ad boş olamaz; "/" ve "\u0000" içeremez; "." / ".." olamaz; aynı klasörde başka bir öğenin adı olamaz. */
    fun validName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\u0000' !in name && name.length <= 255

    fun rename(f: File, newName: String): Result<File> = runCatching {
        val n = newName.trim()
        require(validName(n)) { "Geçersiz ad" }
        val target = File(f.parentFile, n)
        if (target == f) return@runCatching f
        require(!target.exists()) { "Bu adla bir öğe zaten var" }
        check(f.renameTo(target)) { "Yeniden adlandırılamadı" }
        target
    }

    fun delete(f: File): Boolean = runCatching { if (f.isDirectory) f.deleteRecursively() else f.delete() }.getOrDefault(false)

    /** Klasörün toplam boyutu (bayt) ve içindeki dosya sayısı; sembolik bağlara girmez. */
    fun folderStats(dir: File): Pair<Long, Int> {
        var size = 0L
        var count = 0
        dir.walkTopDown().onEnter { !java.nio.file.Files.isSymbolicLink(it.toPath()) }
            .forEach { if (it.isFile) { size += it.length(); count++ } }
        return size to count
    }

    /** [algo]: "MD5" ya da "SHA-256". Akıştan okur; bellekte tutmaz. */
    fun checksum(input: InputStream, algo: String): String {
        val md = MessageDigest.getInstance(algo)
        input.use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Metin dosyasını okur (en çok [MAX_TEXT_BYTES]); kesildiyse ikinci değer true. */
    fun readText(input: InputStream, max: Int = MAX_TEXT_BYTES): Pair<String, Boolean> = input.use { s ->
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var truncated = false
        while (true) {
            val n = s.read(buf)
            if (n < 0) break
            if (out.size() + n > max) {
                out.write(buf, 0, max - out.size())
                truncated = true
                break
            }
            out.write(buf, 0, n)
        }
        String(out.toByteArray(), Charsets.UTF_8) to truncated
    }

    /** Kod/metin dosyasını renklendirmeli gösterim için çitli blok yapar (içerikteki ``` ile çakışmaz). */
    fun asFencedMarkdown(text: String, name: String): String {
        var fence = "```"
        while (fence in text) fence += "`"
        return "$fence${FileBrowse.extension(name)}\n$text\n$fence"
    }
}
