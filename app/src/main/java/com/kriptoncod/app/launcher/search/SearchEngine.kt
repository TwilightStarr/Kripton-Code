package com.kriptoncod.app.launcher.search

import com.kriptoncod.app.launcher.LauncherApp
import com.kriptoncod.app.launcher.LauncherPrefs
import java.io.File

/** Arama sonucu. [id] liste anahtarıdır (tekrarsız). */
sealed interface SearchResult {
    val id: String

    data class App(val app: LauncherApp) : SearchResult {
        override val id get() = "app:${app.packageName}"
    }

    /** Kripton'un kendi uygulamaları (Kripton Code, Dosyalar). [app] masaüstü uygulama anahtarıdır. */
    data class Own(val app: String, val title: String) : SearchResult {
        override val id get() = "own:$app"
    }

    /** AI penceresini aç. */
    data class Ai(val accountId: Long, val name: String) : SearchResult {
        override val id get() = "ai:$accountId"
    }

    /** Yazılanı bu AI'a sor. */
    data class AskAi(val accountId: Long, val name: String, val prompt: String) : SearchResult {
        override val id get() = "ask:$accountId"
    }

    data class FileHit(val file: File, val isDir: Boolean) : SearchResult {
        override val id get() = "file:${file.path}"
    }

    data class Contact(val name: String, val number: String) : SearchResult {
        override val id get() = "contact:$name:$number"
    }

    data class Calc(val expression: String, val value: String) : SearchResult {
        override val id get() = "calc"
    }

    data class Web(val query: String) : SearchResult {
        override val id get() = "web"
    }

    /** Sistem ayarı kısayolu. [action] Settings.ACTION_* değeri. */
    data class Setting(val title: String, val action: String) : SearchResult {
        override val id get() = "set:$action"
    }
}

class SearchInput(
    val query: String,
    val apps: List<LauncherApp>,
    /** (uygulama anahtarı, görünen ad) */
    val own: List<Pair<String, String>>,
    /** (hesap kimliği, görünen ad) */
    val ai: List<Pair<Long, String>>,
    val settings: List<Pair<String, String>>,
    val prefs: LauncherPrefs,
)

/**
 * Anında hesaplanan arama bölümleri (uygulamalar, Kripton, AI, hesap makinesi, sistem ayarları, AI'a sor, web). Dosyalar ve
 * kişiler yavaş olduğundan arayüz onları ayrıca ekler. Saf mantık; birim testle doğrulanır.
 */
object SearchEngine {
    /** Boş sorguda önerilen uygulamalar: en çok başlatılanlar. */
    fun suggestions(apps: List<LauncherApp>, prefs: LauncherPrefs, limit: Int = 8): List<LauncherApp> = apps.filter { it.packageName !in prefs.hiddenApps && (prefs.launchCounts[it.packageName] ?: 0) > 0 }
        .sortedByDescending { prefs.launchCounts[it.packageName] ?: 0 }
        .take(limit)

    fun quick(input: SearchInput): List<SearchResult> {
        val q = input.query.trim()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<SearchResult>()

        if (input.prefs.searchCalc) {
            Calc.eval(q)?.let { out.add(SearchResult.Calc(q, Calc.format(it))) }
        }

        val visible = input.apps.filter { it.packageName !in input.prefs.hiddenApps }
        val scored = visible.map { it to (SearchRank.score(q, it.label) + bonus(input.prefs, it.packageName)) }
            .filter { SearchRank.score(q, it.first.label) > 0 }
            .sortedWith(compareByDescending<Pair<LauncherApp, Int>> { it.second }.thenBy { TextFold.fold(it.first.label) })
            .take(8)
        scored.forEach { out.add(SearchResult.App(it.first)) }

        SearchRank.rank(q, input.own, 4) { it.second }.forEach { out.add(SearchResult.Own(it.first, it.second)) }
        SearchRank.rank(q, input.ai, 6) { it.second }.forEach { out.add(SearchResult.Ai(it.first, it.second)) }
        SearchRank.rank(q, input.settings, 4) { it.first }.forEach { out.add(SearchResult.Setting(it.first, it.second)) }

        // "Sor": en az 2 harflik her yazı bir AI'a sorulabilir (en çok 4 AI).
        if (q.length >= 2) input.ai.take(4).forEach { out.add(SearchResult.AskAi(it.first, it.second, q)) }
        if (input.prefs.searchWeb && q.length >= 2) out.add(SearchResult.Web(q))
        return out.distinctBy { it.id }
    }

    private fun bonus(prefs: LauncherPrefs, pkg: String): Int = (prefs.launchCounts[pkg] ?: 0).coerceAtMost(10)

    /** Web araması adresi (varsayılan tarayıcıda açılır). */
    fun webUrl(query: String): String = "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(query, "UTF-8")
}
