package com.kriptoncod.app.launcher.search

/**
 * Arama sıralaması (saf mantık). Puan ne kadar yüksekse sonuç o kadar üstte çıkar; 0 = eşleşme yok.
 *
 *  - ada tam eşit: 100
 *  - ad sorguyla başlıyor: 80
 *  - bir sözcük sorguyla başlıyor: 60 (daha önceki sözcük biraz daha yüksek)
 *  - ad sorguyu içeriyor: 40
 *  - harfler sırayla geçiyor (\"krpt\" -> \"Kripton\"): 20 (yalnız 3+ harfli sorguda)
 */
object SearchRank {
    fun score(query: String, name: String): Int {
        val q = TextFold.fold(query).trim()
        if (q.isEmpty()) return 0
        val n = TextFold.fold(name)
        if (n.isEmpty()) return 0
        if (n == q) return 100
        if (n.startsWith(q)) return 80
        val words = TextFold.words(n)
        val wordAt = words.indexOfFirst { it.startsWith(q) }
        if (wordAt >= 0) return 60 - wordAt.coerceAtMost(9)
        if (n.contains(q)) return 40
        if (q.length >= 3 && isSubsequence(q, n)) return 20
        return 0
    }

    /** [needle]'ın harfleri [haystack] içinde sırayla (aralarda boşluk olabilir) geçiyor mu? */
    fun isSubsequence(needle: String, haystack: String): Boolean {
        var i = 0
        for (c in haystack) {
            if (i < needle.length && c == needle[i]) i++
        }
        return i == needle.length
    }

    /** [items] içinden eşleşenleri puana (sonra ada) göre sıralar; en çok [limit] öğe. */
    fun <T> rank(query: String, items: List<T>, limit: Int = 50, nameOf: (T) -> String): List<T> {
        if (query.isBlank()) return emptyList()
        return items.asSequence()
            .map { it to score(query, nameOf(it)) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<T, Int>> { it.second }.thenBy { TextFold.fold(nameOf(it.first)) })
            .take(limit)
            .map { it.first }
            .toList()
    }
}
