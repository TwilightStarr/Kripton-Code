package com.kriptoncod.app.launcher.search

import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Dosya aramasındaki bir kayıt. */
class IndexedFile(val file: File, val name: String, val folded: String, val isDir: Boolean)

/**
 * Depolamadaki dosya adlarının bellek içi dizini. İlk aramada (ya da dizin eskiyse) arka planda yeniden kurulur; içerik OKUNMAZ,
 * yalnız ad ve yol. Sınırlar: derinlik, toplam kayıt, `Android/data` ve `Android/obb` atlanır, sembolik bağlara girilmez.
 * Hiçbir şey diske yazılmaz.
 */
class FileIndex(private val root: () -> File) {
    @Volatile private var entries: List<IndexedFile> = emptyList()
    private val builtAt = AtomicLong(0L)
    private val building = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Dizin yoksa ya da [maxAgeMs]'den eskiyse yeniden kurar (çağıran arka plan iş parçacığında olmalı). Kurulum sürerken tekrar kurmaz. */
    fun ensure(maxAgeMs: Long = 5 * 60_000L, now: Long = System.currentTimeMillis()) {
        val fresh = builtAt.get() != 0L && now - builtAt.get() < maxAgeMs
        if (fresh || !building.compareAndSet(false, true)) return
        try {
            entries = build(root())
            builtAt.set(now)
        } finally {
            building.set(false)
        }
    }

    fun invalidate() = builtAt.set(0L)

    fun search(query: String, limit: Int = 8): List<IndexedFile> {
        val q = TextFold.fold(query).trim()
        if (q.length < 2) return emptyList()
        return SearchRank.rank(q, entries, limit) { it.name }
    }

    companion object {
        const val MAX_ENTRIES = 80_000
        const val MAX_DEPTH = 9
        private val SKIP = setOf("Android/data", "Android/obb")

        fun build(root: File): List<IndexedFile> {
            val out = ArrayList<IndexedFile>(4096)
            val base = runCatching { root.canonicalFile }.getOrNull() ?: return emptyList()
            fun walk(dir: File, depth: Int) {
                if (out.size >= MAX_ENTRIES || depth > MAX_DEPTH) return
                val kids = runCatching { dir.listFiles() }.getOrNull() ?: return
                for (f in kids) {
                    if (out.size >= MAX_ENTRIES) return
                    if (f.name.startsWith(".")) continue
                    val isDir = f.isDirectory
                    out.add(IndexedFile(f, f.name, TextFold.fold(f.name), isDir))
                    if (isDir) {
                        val rel = f.path.removePrefix(base.path).trim('/')
                        if (rel in SKIP) continue
                        if (java.nio.file.Files.isSymbolicLink(f.toPath())) continue
                        walk(f, depth + 1)
                    }
                }
            }
            walk(base, 0)
            return out
        }
    }
}
