package com.kriptoncod.app.data.files

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Kalıcı dosya yazmaları: geçici dosya + fsync + atomik rename. */
object AtomicFiles {
    const val TMP_SUFFIX = ".tmp"

    fun write(target: File, bytes: ByteArray): Unit = write(target) { it.write(bytes) }

    fun write(target: File, block: (OutputStream) -> Unit) {
        val dir = target.absoluteFile.parentFile ?: error("Üst klasör yok: $target")
        dir.mkdirs()
        val tmp = File.createTempFile(target.name + ".", TMP_SUFFIX, dir)
        try {
            FileOutputStream(tmp).use { out ->
                block(out)
                out.flush()
                out.fd.sync()
            }
            move(tmp, target)
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
    }

    /** Aynı dizin içinde atomik rename; hedef varsa değiştirilir. */
    fun move(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Dizindeki (özyinelemeli) yarım kalmış geçici dosyaları siler; silinen sayısını döndürür. */
    fun sweepTemp(dir: File): Int {
        if (!dir.isDirectory) return 0
        return dir.walkTopDown().filter { it.isFile && it.name.endsWith(TMP_SUFFIX) && it.delete() }.count()
    }
}
