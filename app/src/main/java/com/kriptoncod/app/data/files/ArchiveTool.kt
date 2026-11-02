package com.kriptoncod.app.data.files

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Desteklenen arşiv türleri. 7z/rar için sistemde dış kitaplık gerekir; bunlar [UNSUPPORTED] sayılır ve başka uygulamayla açılır. */
enum class ArchiveFormat { ZIP, TAR, TAR_GZ, GZ, UNSUPPORTED }

/** Arşivin içindeki bir kayıt. [path] her zaman `/` ayraçlıdır, başında `/` yoktur; klasörlerde sonda `/` yoktur. */
data class ArchiveEntry(val path: String, val isDir: Boolean, val size: Long, val modified: Long) {
    val name: String get() = path.substringAfterLast('/')
    val parent: String get() = path.substringBeforeLast('/', "")
}

/** Uzun işlerde ilerleme: [done] / [total] bayt (total bilinmiyorsa 0). */
fun interface ArchiveProgress {
    fun update(done: Long, total: Long)
}

class ArchiveCancelled : IOException("İptal edildi")

/**
 * Kripton'un kendi arşiv aracı (ZArchiver benzeri): zip, tar, tar.gz/tgz ve tek dosya gz; açma, içine bakma, tek kayıt çıkarma,
 * zip ve tar.gz oluşturma. Yalnız java.io / java.util.zip kullanır (ek kitaplık yok). Güvenlik: zip-slip (`../`) ve mutlak
 * yollar engellenir, kayıt sayısı ve toplam boyut sınırlıdır, hedef diskte yer kontrol edilir. Saf mantık; birim testle doğrulanır.
 * Parolalı (şifreli) arşivler desteklenmez.
 */
object ArchiveTool {
    const val MAX_ENTRIES = 200_000
    private const val BUF = 64 * 1024

    fun formatOf(name: String): ArchiveFormat {
        val n = name.lowercase(Locale.ROOT)
        return when {
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> ArchiveFormat.TAR_GZ
            n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".apk") || n.endsWith(".aar") -> ArchiveFormat.ZIP
            n.endsWith(".tar") -> ArchiveFormat.TAR
            n.endsWith(".gz") -> ArchiveFormat.GZ
            else -> ArchiveFormat.UNSUPPORTED
        }
    }

    fun isSupported(name: String): Boolean = formatOf(name) != ArchiveFormat.UNSUPPORTED

    /** Arşiv adından klasör adı: "proje.tar.gz" -> "proje". */
    fun baseName(name: String): String {
        val n = name.lowercase(Locale.ROOT)
        val cut = when {
            n.endsWith(".tar.gz") -> 7
            n.endsWith(".tgz") -> 4
            n.endsWith(".zip") || n.endsWith(".tar") || n.endsWith(".jar") || n.endsWith(".apk") || n.endsWith(".aar") -> 4
            n.endsWith(".gz") -> 3
            else -> 0
        }
        return name.dropLast(cut).ifBlank { name }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Yol güvenliği
    // ----------------------------------------------------------------------------------------------------------------

    /** Kayıt yolunu `/` ayraçlı, göreli ve `..` içermeyen hâle getirir; güvenli değilse null. */
    fun cleanPath(raw: String): String? {
        val p = raw.replace('\\', '/').trimStart('/')
        if (p.isBlank() || '\u0000' in p) return null
        val parts = p.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    /** [dest] içinde kalan hedef dosya (zip-slip koruması); dışarı taşıyorsa null. */
    fun safeTarget(dest: File, entryPath: String): File? {
        val clean = cleanPath(entryPath) ?: return null
        val root = runCatching { dest.canonicalFile }.getOrNull() ?: return null
        val target = runCatching { File(root, clean).canonicalFile }.getOrNull() ?: return null
        return if (target.path == root.path || target.path.startsWith(root.path.trimEnd('/') + File.separator)) target else null
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Listeleme
    // ----------------------------------------------------------------------------------------------------------------

    /** Arşivdeki bütün kayıtlar (klasörler dahil, yola göre sıralı). Okunamayan arşivde [IOException]. */
    fun list(archive: File): List<ArchiveEntry> {
        val out = ArrayList<ArchiveEntry>()
        when (formatOf(archive.name)) {
            ArchiveFormat.ZIP -> withZip(archive) { zf ->
                val en = zf.entries()
                while (en.hasMoreElements()) {
                    val e = en.nextElement()
                    val p = cleanPath(e.name) ?: continue
                    out.add(ArchiveEntry(p.trimEnd('/'), e.isDirectory, if (e.isDirectory) 0L else e.size.coerceAtLeast(0L), e.time.coerceAtLeast(0L)))
                    if (out.size > MAX_ENTRIES) throw IOException("Arşivde çok fazla kayıt var")
                }
            }

            ArchiveFormat.TAR, ArchiveFormat.TAR_GZ -> openTar(archive).use { tar ->
                while (true) {
                    val h = tar.next() ?: break
                    val p = cleanPath(h.name)
                    if (p != null && (h.isDir || h.isFile)) {
                        out.add(ArchiveEntry(p.trimEnd('/'), h.isDir, if (h.isDir) 0L else h.size, h.mtime))
                        if (out.size > MAX_ENTRIES) throw IOException("Arşivde çok fazla kayıt var")
                    }
                    tar.skipContent()
                }
            }

            ArchiveFormat.GZ -> out.add(ArchiveEntry(baseName(archive.name), false, gzipSize(archive), archive.lastModified()))

            ArchiveFormat.UNSUPPORTED -> throw IOException("Bu arşiv türü desteklenmiyor")
        }
        return out.sortedBy { it.path.lowercase(Locale.ROOT) }
    }

    /** Tek gz dosyasının açılmış boyutu (sondaki 4 bayt; 4 GB üstünde yanlış olabilir, yalnız gösterim için). */
    private fun gzipSize(f: File): Long = runCatching {
        java.io.RandomAccessFile(f, "r").use { r ->
            if (r.length() < 4) return@use 0L
            r.seek(r.length() - 4)
            val b = ByteArray(4)
            r.readFully(b)
            (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
        }
    }.getOrDefault(0L)

    /** [prefix] klasörünün doğrudan altındaki kayıtlar (arşivi klasör gibi gezmek için); ara klasörler kendiliğinden üretilir. */
    fun children(all: List<ArchiveEntry>, prefix: String): List<ArchiveEntry> {
        val base = prefix.trim('/')
        val seen = LinkedHashMap<String, ArchiveEntry>()
        for (e in all) {
            val rest = if (base.isEmpty()) e.path else if (e.path.startsWith("$base/")) e.path.removePrefix("$base/") else continue
            if (rest.isEmpty()) continue
            val first = rest.substringBefore('/')
            val childPath = if (base.isEmpty()) first else "$base/$first"
            val deeper = rest.contains('/')
            if (deeper) {
                seen.putIfAbsent(childPath, ArchiveEntry(childPath, true, 0L, 0L))
            } else {
                seen[childPath] = e
            }
        }
        return seen.values.sortedWith(compareByDescending<ArchiveEntry> { it.isDir }.thenBy { it.name.lowercase(Locale.ROOT) })
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Açma
    // ----------------------------------------------------------------------------------------------------------------

    /**
     * Arşivi [dest] klasörüne açar. [only] doluysa yalnız bu kayıtlar (ve klasörse altındakiler) açılır.
     * Hedefte aynı ad varsa üzerine YAZILMAZ: "ad (1).uzantı" olarak açılır. Dönen değer: yazılan dosya sayısı.
     */
    fun extract(
        archive: File,
        dest: File,
        only: Set<String>? = null,
        progress: ArchiveProgress? = null,
        cancelled: () -> Boolean = { false },
    ): Result<Int> = runCatching {
        require(archive.isFile) { "Arşiv bulunamadı" }
        dest.mkdirs()
        require(dest.isDirectory) { "Hedef klasör açılamadı" }
        val format = formatOf(archive.name)
        require(format != ArchiveFormat.UNSUPPORTED) { "Bu arşiv türü desteklenmiyor" }
        val entries = list(archive)
        val wanted = entries.filter { matches(it.path, only) }
        val total = wanted.sumOf { it.size }
        if (dest.usableSpace in 1 until total) throw IOException("Diskte yeterli yer yok")
        var done = 0L
        var written = 0
        fun tick(n: Int) {
            if (cancelled()) throw ArchiveCancelled()
            done += n
            progress?.update(done, total)
        }
        when (format) {
            ArchiveFormat.ZIP -> withZip(archive) { zf ->
                val en = zf.entries()
                while (en.hasMoreElements()) {
                    if (cancelled()) throw ArchiveCancelled()
                    val ze = en.nextElement()
                    val p = cleanPath(ze.name)?.trimEnd('/') ?: continue
                    if (!matches(p, only)) continue
                    val e = ArchiveEntry(p, ze.isDirectory, if (ze.isDirectory) 0L else ze.size.coerceAtLeast(0L), ze.time.coerceAtLeast(0L))
                    if (writeOne(dest, e, if (ze.isDirectory) null else zf.getInputStream(ze), ::tick)) written++
                }
            }

            ArchiveFormat.TAR, ArchiveFormat.TAR_GZ -> openTar(archive).use { tar ->
                while (true) {
                    val h = tar.next() ?: break
                    val p = cleanPath(h.name)
                    if (p == null || !(h.isDir || h.isFile) || !matches(p.trimEnd('/'), only)) {
                        tar.skipContent()
                        continue
                    }
                    val e = ArchiveEntry(p.trimEnd('/'), h.isDir, h.size, h.mtime)
                    if (writeOne(dest, e, if (h.isDir) null else tar.contentStream(), ::tick)) written++
                    tar.skipContent()
                }
            }

            ArchiveFormat.GZ -> {
                val target = uniqueIn(dest, baseName(archive.name))
                GZIPInputStream(BufferedInputStream(archive.inputStream(), BUF)).use { gz ->
                    BufferedOutputStream(target.outputStream(), BUF).use { o -> copy(gz, o, ::tick) }
                }
                written++
            }

            ArchiveFormat.UNSUPPORTED -> Unit
        }
        written
    }

    private fun matches(path: String, only: Set<String>?): Boolean {
        if (only.isNullOrEmpty()) return true
        return only.any { sel ->
            val s = sel.trim('/')
            path == s || path.startsWith("$s/")
        }
    }

    /** Tek kaydı yazar; yazıldıysa true. Klasörse oluşturur. */
    private fun writeOne(dest: File, e: ArchiveEntry, input: InputStream?, tick: (Int) -> Unit): Boolean {
        val target = safeTarget(dest, e.path) ?: return false
        if (e.isDir) {
            target.mkdirs()
            return false
        }
        target.parentFile?.mkdirs()
        val out = uniqueIn(target.parentFile ?: dest, target.name)
        input?.use { i -> BufferedOutputStream(out.outputStream(), BUF).use { o -> copy(i, o, tick) } }
        if (e.modified > 0L) out.setLastModified(e.modified)
        return true
    }

    private fun uniqueIn(dir: File, name: String): File {
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

    /** Tek bir kaydı belirli bir dosyaya çıkarır (önizleme için). Üzerine yazar. */
    fun extractEntry(archive: File, entryPath: String, target: File, maxBytes: Long = 200L * 1024 * 1024): Result<File> = runCatching {
        val clean = cleanPath(entryPath) ?: throw IOException("Geçersiz kayıt yolu")
        target.parentFile?.mkdirs()
        var found = false
        fun writeTo(i: InputStream) {
            var total = 0L
            BufferedOutputStream(target.outputStream(), BUF).use { o ->
                copy(i, o) { n ->
                    total += n
                    if (total > maxBytes) throw IOException("Kayıt çok büyük")
                }
            }
            found = true
        }
        when (formatOf(archive.name)) {
            ArchiveFormat.ZIP -> withZip(archive) { zf ->
                val en = zf.entries()
                while (!found && en.hasMoreElements()) {
                    val ze = en.nextElement()
                    if (!ze.isDirectory && cleanPath(ze.name)?.trimEnd('/') == clean) zf.getInputStream(ze).use { writeTo(it) }
                }
            }

            ArchiveFormat.TAR, ArchiveFormat.TAR_GZ -> openTar(archive).use { tar ->
                while (!found) {
                    val h = tar.next() ?: break
                    if (h.isFile && cleanPath(h.name)?.trimEnd('/') == clean) writeTo(tar.contentStream()) else tar.skipContent()
                }
            }

            ArchiveFormat.GZ -> GZIPInputStream(BufferedInputStream(archive.inputStream(), BUF)).use { writeTo(it) }

            ArchiveFormat.UNSUPPORTED -> throw IOException("Bu arşiv türü desteklenmiyor")
        }
        if (!found) throw IOException("Kayıt bulunamadı")
        target
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Oluşturma
    // ----------------------------------------------------------------------------------------------------------------

    /**
     * [sources] (dosya ve klasörler) tek arşive yazılır. [format]: [ArchiveFormat.ZIP] ya da [ArchiveFormat.TAR_GZ].
     * Kayıt adları kaynağın kendi adıyla başlar ("foto/a.png"). [dest] varsa üzerine yazılmaz: hedef ad benzersizleştirilir.
     * İptal ya da hatada yarım dosya silinir. Dönen değer: oluşan arşiv.
     */
    fun compress(
        sources: List<File>,
        dest: File,
        format: ArchiveFormat = ArchiveFormat.ZIP,
        level: Int = 6,
        progress: ArchiveProgress? = null,
        cancelled: () -> Boolean = { false },
    ): Result<File> = runCatching {
        require(sources.isNotEmpty()) { "Sıkıştırılacak öğe yok" }
        require(format == ArchiveFormat.ZIP || format == ArchiveFormat.TAR_GZ) { "Bu tür oluşturulamaz" }
        val files = ArrayList<Pair<File, String>>() // (dosya, kayıt yolu)
        val dirs = ArrayList<String>()
        for (src in sources) {
            require(src.exists()) { "${src.name} bulunamadı" }
            collect(src, src.name, files, dirs)
            if (files.size + dirs.size > MAX_ENTRIES) throw IOException("Çok fazla dosya")
        }
        val total = files.sumOf { it.first.length() }
        val target = uniqueIn(dest.parentFile ?: File("."), dest.name)
        target.parentFile?.mkdirs()
        var done = 0L
        fun tick(n: Int) {
            if (cancelled()) throw ArchiveCancelled()
            done += n
            progress?.update(done, total)
        }
        try {
            when (format) {
                ArchiveFormat.ZIP -> ZipOutputStream(BufferedOutputStream(target.outputStream(), BUF)).use { z ->
                    z.setLevel(level.coerceIn(0, 9))
                    for (d in dirs) {
                        z.putNextEntry(ZipEntry("$d/"))
                        z.closeEntry()
                    }
                    for ((f, path) in files) {
                        if (cancelled()) throw ArchiveCancelled()
                        val ze = ZipEntry(path)
                        ze.time = f.lastModified()
                        z.putNextEntry(ze)
                        f.inputStream().use { i -> copy(i, z, ::tick) }
                        z.closeEntry()
                    }
                }

                else -> GZIPOutputStream(BufferedOutputStream(target.outputStream(), BUF)).use { gz ->
                    val tar = TarWriter(gz)
                    for (d in dirs) tar.writeDir(d)
                    for ((f, path) in files) {
                        if (cancelled()) throw ArchiveCancelled()
                        tar.writeFile(path, f.length(), f.lastModified()) { o -> f.inputStream().use { i -> copy(i, o, ::tick) } }
                    }
                    tar.finish()
                }
            }
        } catch (t: Throwable) {
            runCatching { target.delete() }
            throw t
        }
        target
    }

    private fun collect(f: File, path: String, files: MutableList<Pair<File, String>>, dirs: MutableList<String>) {
        if (java.nio.file.Files.isSymbolicLink(f.toPath())) return
        if (f.isDirectory) {
            dirs.add(path)
            val kids = f.listFiles()?.sortedBy { it.name } ?: return
            for (k in kids) collect(k, "$path/${k.name}", files, dirs)
        } else if (f.isFile) {
            files.add(f to path)
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Ortak yardımcılar
    // ----------------------------------------------------------------------------------------------------------------

    private fun copy(i: InputStream, o: OutputStream, tick: (Int) -> Unit) {
        val buf = ByteArray(BUF)
        while (true) {
            val n = i.read(buf)
            if (n < 0) break
            o.write(buf, 0, n)
            tick(n)
        }
    }

    private inline fun <T> withZip(f: File, block: (ZipFile) -> T): T {
        val zf = try {
            ZipFile(f, Charsets.UTF_8)
        } catch (_: Exception) {
            // Eski araçların yerel kod sayfasıyla yazdığı adlar UTF-8 olarak geçersiz olabilir.
            ZipFile(f, Charset.forName("ISO-8859-1"))
        }
        return zf.use(block)
    }

    private fun openTar(f: File): TarReader {
        val raw = BufferedInputStream(f.inputStream(), BUF)
        return TarReader(if (formatOf(f.name) == ArchiveFormat.TAR_GZ) GZIPInputStream(raw, BUF) else raw)
    }

    // ----------------------------------------------------------------------------------------------------------------
    // TAR (ustar + GNU uzun ad + pax yol)
    // ----------------------------------------------------------------------------------------------------------------

    internal class TarHeader(val name: String, val size: Long, val type: Char, val mtime: Long) {
        val isDir: Boolean get() = type == '5' || name.endsWith("/")
        val isFile: Boolean get() = type == '0' || type == '\u0000'
    }

    internal class TarReader(private val input: InputStream) : java.io.Closeable {
        private var remaining = 0L
        private var pad = 0L

        fun next(): TarHeader? {
            skipContent()
            var longName: String? = null
            var paxPath: String? = null
            while (true) {
                val block = ByteArray(512)
                if (!readFully(block)) return null
                if (block.all { it == 0.toByte() }) return null // arşiv sonu
                val rawName = cString(block, 0, 100)
                val size = octal(block, 124, 12)
                val type = if (block[156].toInt() == 0) '\u0000' else (block[156].toInt() and 0xFF).toChar()
                val mtime = octal(block, 136, 12) * 1000L
                val prefix = cString(block, 345, 155)
                when (type) {
                    'L' -> { // GNU uzun ad
                        longName = readSmall(size)
                        continue
                    }

                    'x' -> { // pax: yalnız path alınır
                        paxPath = parsePaxPath(readSmall(size)) ?: paxPath
                        continue
                    }

                    'g' -> { // genel pax: yok sayılır
                        readSmall(size)
                        continue
                    }
                }
                val name = paxPath ?: longName ?: if (prefix.isNotEmpty()) "$prefix/$rawName" else rawName
                remaining = size
                pad = (512 - size % 512) % 512
                return TarHeader(name, size, type, mtime)
            }
        }

        /** Geçerli kaydın içeriği (en çok [TarHeader.size] bayt verir). */
        fun contentStream(): InputStream = object : InputStream() {
            override fun read(): Int {
                if (remaining <= 0) return -1
                val b = input.read()
                if (b >= 0) remaining--
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val n = input.read(b, off, minOf(len.toLong(), remaining).toInt())
                if (n > 0) remaining -= n
                return n
            }

            override fun close() = Unit // asıl akışı kapatma
        }

        fun skipContent() {
            skipFully(remaining + pad)
            remaining = 0
            pad = 0
        }

        private fun readSmall(size: Long): String {
            require(size in 0..65_536) { "Bozuk tar başlığı" }
            val data = ByteArray(size.toInt())
            if (!readFully(data)) throw IOException("Tar beklenmedik şekilde bitti")
            skipFully((512 - size % 512) % 512)
            val end = data.indexOf(0.toByte()).let { if (it < 0) data.size else it }
            return String(data, 0, end, Charsets.UTF_8)
        }

        private fun parsePaxPath(text: String): String? {
            // Her kayıt: "<uzunluk> anahtar=değer\n"
            for (line in text.split('\n')) {
                val sp = line.indexOf(' ')
                if (sp < 0) continue
                val kv = line.substring(sp + 1)
                if (kv.startsWith("path=")) return kv.removePrefix("path=")
            }
            return null
        }

        private fun readFully(b: ByteArray): Boolean {
            var off = 0
            while (off < b.size) {
                val n = input.read(b, off, b.size - off)
                if (n < 0) return false
                off += n
            }
            return true
        }

        private fun skipFully(count: Long) {
            var left = count
            val sink = ByteArray(8192)
            while (left > 0) {
                val n = input.read(sink, 0, minOf(left, sink.size.toLong()).toInt())
                if (n < 0) throw IOException("Tar beklenmedik şekilde bitti")
                left -= n
            }
        }

        override fun close() = input.close()

        private fun cString(b: ByteArray, off: Int, len: Int): String {
            var end = off
            while (end < off + len && b[end].toInt() != 0) end++
            return String(b, off, end - off, Charsets.UTF_8)
        }

        private fun octal(b: ByteArray, off: Int, len: Int): Long {
            if (b[off].toInt() and 0x80 != 0) { // GNU taban-256 gösterimi
                var v = 0L
                for (i in 1 until len) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
                return v
            }
            var v = 0L
            for (i in off until off + len) {
                val c = b[i].toInt()
                if (c in '0'.code..'7'.code) v = v * 8 + (c - '0'.code) else if (v > 0 || c == 0) break
            }
            return v
        }
    }

    internal class TarWriter(private val out: OutputStream) {
        fun writeDir(path: String) = header("$path/", 0, '5', System.currentTimeMillis())

        fun writeFile(path: String, size: Long, mtime: Long, body: (OutputStream) -> Unit) {
            header(path, size, '0', mtime)
            val counting = object : OutputStream() {
                var n = 0L
                override fun write(b: Int) { out.write(b); n++ }
                override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); n += len }
            }
            body(counting)
            // Dosya yazarken boyutu değiştiyse arşiv bozulmasın: başlıktaki boyut esas alınır.
            if (counting.n != size) throw IOException("Dosya yazılırken değişti: $path")
            val pad = ((512 - size % 512) % 512).toInt()
            if (pad > 0) out.write(ByteArray(pad))
        }

        fun finish() {
            out.write(ByteArray(1024))
            out.flush()
        }

        private fun header(path: String, size: Long, type: Char, mtime: Long) {
            val bytes = path.toByteArray(Charsets.UTF_8)
            if (bytes.size > 100) {
                // GNU uzun ad kaydı
                val data = ByteArrayOutputStream().apply { write(bytes); write(0) }.toByteArray()
                block("././@LongLink".toByteArray(), data.size.toLong(), 'L', 0L)
                out.write(data)
                val pad = ((512 - data.size % 512) % 512)
                if (pad > 0) out.write(ByteArray(pad))
                block(bytes.copyOf(100), size, type, mtime)
            } else {
                block(bytes, size, type, mtime)
            }
        }

        private fun block(name: ByteArray, size: Long, type: Char, mtime: Long) {
            val b = ByteArray(512)
            System.arraycopy(name, 0, b, 0, minOf(name.size, 100))
            put(b, 100, if (type == '5') "0000755" else "0000644", 8)
            put(b, 108, "0000000", 8)
            put(b, 116, "0000000", 8)
            put(b, 124, octalText(size, 11), 12)
            put(b, 136, octalText(mtime / 1000L, 11), 12)
            for (i in 148 until 156) b[i] = ' '.code.toByte() // sağlama toplamı alanı: hesap için boşluk
            b[156] = type.code.toByte()
            put(b, 257, "ustar", 6)
            put(b, 263, "00", 2)
            var sum = 0
            for (x in b) sum += x.toInt() and 0xFF
            put(b, 148, octalText(sum.toLong(), 6), 7)
            b[154] = 0
            b[155] = ' '.code.toByte()
            out.write(b)
        }

        private fun octalText(v: Long, digits: Int): String = java.lang.Long.toOctalString(v.coerceAtLeast(0L)).padStart(digits, '0')

        private fun put(b: ByteArray, off: Int, text: String, max: Int) {
            val t = text.toByteArray(Charsets.US_ASCII)
            System.arraycopy(t, 0, b, off, minOf(t.size, max))
        }
    }
}
