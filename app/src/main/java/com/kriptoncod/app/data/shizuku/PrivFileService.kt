package com.kriptoncod.app.data.shizuku

import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.system.exitProcess

/**
 * Shizuku'nun ayrı bir süreçte (adb: shell, root: root) örneklediği SALT-OKUNUR dosya servisi.
 * Bu sınıf uygulama sürecinde değil, ayrıcalıklı süreçte çalışır; Android bağlamı yoktur, yalnız java.io kullanır.
 * Yazma/silme/taşıma işlemi yoktur. Shizuku sınıfı ada göre örnekler (bkz. proguard-rules.pro); varsayılan kurucu şart.
 */
class PrivFileService : IPrivFileService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun uid(): Int = android.os.Process.myUid()

    override fun list(path: String?, offset: Int, limit: Int): String {
        val dir = fileOf(path) ?: return PrivProtocol.error(PrivError.NOT_FOUND)
        return runCatching {
            when {
                !dir.exists() -> PrivProtocol.error(PrivError.NOT_FOUND)

                !dir.isDirectory -> PrivProtocol.error(PrivError.NOT_DIR)

                else -> {
                    val names = dir.list() ?: return PrivProtocol.error(PrivError.DENIED)
                    names.sorted()
                        .asSequence()
                        .filter { '\n' !in it && '\t' !in it }
                        .drop(offset.coerceAtLeast(0))
                        .take(limit.coerceIn(1, PrivProtocol.PAGE))
                        .joinToString("\n") { n ->
                            val f = File(dir, n)
                            val d = runCatching { f.isDirectory }.getOrDefault(false)
                            PrivProtocol.formatEntry(
                                n,
                                d,
                                if (d) 0L else runCatching { f.length() }.getOrDefault(0L),
                                runCatching { f.lastModified() }.getOrDefault(0L),
                            )
                        }
                }
            }
        }.getOrElse { PrivProtocol.error(PrivError.IO) }
    }

    override fun stat(path: String?): String {
        val f = fileOf(path) ?: return PrivProtocol.error(PrivError.NOT_FOUND)
        return runCatching {
            if (!f.exists()) {
                PrivProtocol.error(PrivError.NOT_FOUND)
            } else {
                PrivProtocol.formatEntry(f.name, f.isDirectory, f.length(), f.lastModified()).substringBeforeLast('\t')
            }
        }.getOrElse { PrivProtocol.error(PrivError.IO) }
    }

    override fun openRead(path: String?): ParcelFileDescriptor? {
        val f = fileOf(path) ?: return null
        return runCatching {
            if (f.isFile) ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY) else null
        }.getOrNull()
    }

    override fun packages(): String {
        // root ise /data/data doğrudan listelenir; shell'de bu klasör kapalıdır, `pm list packages` kullanılır.
        File("/data/data").list()?.let { return it.sorted().joinToString("\n") }
        return runCatching {
            val p = ProcessBuilder("pm", "list", "packages").redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor()
            text
        }.getOrElse { PrivProtocol.error(PrivError.IO) }
    }

    /** Yalnız mutlak yollar; ".." ile kaçış, boş/NUL içeren yol reddedilir (kanonikleştirme servis tarafında yapılmaz, sistem izinleri sınırdır). */
    private fun fileOf(path: String?): File? {
        if (path.isNullOrEmpty() || !path.startsWith("/") || '\u0000' in path) return null
        if (path.split('/').any { it == ".." }) return null
        return File(path)
    }
}
