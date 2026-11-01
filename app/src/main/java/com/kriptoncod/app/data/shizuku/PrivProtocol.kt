package com.kriptoncod.app.data.shizuku

import java.util.Locale

/** Ayrıcalıklı servisin listelediği bir satır. */
data class PrivEntry(val name: String, val isDir: Boolean, val size: Long, val modified: Long)

/** Servis hata kodları ("ERR:<KOD>"). */
enum class PrivError { NOT_FOUND, NOT_DIR, DENIED, IO, UNKNOWN }

/**
 * Servis ile uygulama arasındaki düz metin protokolü. Saf mantık (Android'e bağlı değil); birim testle doğrulanır.
 * Satır: `d|f <TAB> boyut <TAB> değişiklik <TAB> ad`. Adında satır sonu ya da sekme olan dosyalar servis tarafında atlanır.
 */
object PrivProtocol {
    const val ERR_PREFIX = "ERR:"
    const val PAGE = 1000

    fun error(code: PrivError): String = ERR_PREFIX + code.name

    fun parseError(text: String?): PrivError? {
        if (text == null) return PrivError.IO
        if (!text.startsWith(ERR_PREFIX)) return null
        val code = text.removePrefix(ERR_PREFIX).trim()
        return PrivError.entries.firstOrNull { it.name == code } ?: PrivError.UNKNOWN
    }

    fun formatEntry(name: String, isDir: Boolean, size: Long, modified: Long): String =
        "${if (isDir) 'd' else 'f'}\t${if (isDir) 0L else size}\t$modified\t$name"

    /** Bozuk satırları atlar; sıralama servisten gelen sırayı korur. */
    fun parseEntries(text: String): List<PrivEntry> = text.lineSequence().mapNotNull { line ->
        val p = line.split('\t', limit = 4)
        if (p.size < 4 || p[3].isEmpty() || (p[0] != "d" && p[0] != "f")) {
            null
        } else {
            PrivEntry(p[3], p[0] == "d", p[1].toLongOrNull() ?: 0L, p[2].toLongOrNull() ?: 0L)
        }
    }.toList()

    /** Önce klasörler, sonra dosyalar; her grup ada göre (büyük/küçük harf duyarsız). [showHidden] false iken noktalılar gizlenir. */
    fun arrange(entries: List<PrivEntry>, showHidden: Boolean): List<PrivEntry> = entries
        .filter { showHidden || !it.name.startsWith(".") }
        .sortedWith(compareByDescending<PrivEntry> { it.isDir }.thenBy { it.name.lowercase(Locale.ROOT) })

    /** "package:com.x" ya da "com.x" satırlarından geçerli paket adları. */
    fun parsePackages(text: String): List<String> = text.lineSequence()
        .map { it.trim().removePrefix("package:") }
        .filter { PACKAGE.matches(it) }
        .distinct()
        .sorted()
        .toList()

    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

    /**
     * Bir paketin veri klasörü. Root (uid 0) ise gerçek /data/data; adb (shell) ise yalnız harici
     * `Android/data/<paket>` okunabilir (/data/data shell'e kapalıdır).
     */
    fun appDataPath(pkg: String, uid: Int, externalRoot: String): String =
        if (uid == 0) "/data/data/$pkg" else "${externalRoot.trimEnd('/')}/Android/data/$pkg"

    /** Kullanıcıya gösterilecek erişim düzeyi. */
    fun modeOf(uid: Int?): PrivMode = when (uid) {
        0 -> PrivMode.ROOT
        2000 -> PrivMode.ADB
        null -> PrivMode.UNKNOWN
        else -> PrivMode.OTHER
    }
}

enum class PrivMode { ROOT, ADB, OTHER, UNKNOWN }
