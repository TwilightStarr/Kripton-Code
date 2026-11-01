package com.kriptoncod.app.data.github

import java.net.URLEncoder

/**
 * Kripton Code'un GitHub'dan ihtiyaç duyduğu EN DAR izinler ve bunları hazır dolduran token oluşturma bağlantısı.
 *
 * Neden ince ayarlı (fine-grained) token: klasik `repo` token'ı hesaptaki TÜM repolara erişir. İnce ayarlı token yalnız seçilen
 * repolara, yalnız listelenen izinlerle erişir ve süresi dolar. Repo seçimi bağlantıyla doldurulamaz; kullanıcı elle seçer.
 *
 * İzinlerin dayanağı ([GitHubService] uçları):
 *  - contents=write : repo zip indirme (zipball), blob/tree/commit/ref yazma
 *  - actions=read   : workflow çalıştırmalarını listeleme ve hata günlüklerini indirme
 *  - metadata=read  : GitHub otomatik ekler
 *  - workflows=write: YALNIZ `.github/workflows/*` dosyaları değiştirilecekse (aksi halde GitHub push'u reddeder)
 */
object GitHubTokenGuide {
    private const val BASE = "https://github.com/settings/personal-access-tokens/new"
    const val EXPIRES_DAYS = 90

    /** İzin adı → seviye. Sıra sabit (testler ve bağlantı kararlı kalsın). */
    val REQUIRED: Map<String, String> = linkedMapOf("contents" to "write", "actions" to "read")
    val OPTIONAL_WORKFLOWS: Pair<String, String> = "workflows" to "write"

    /** [withWorkflows]: AI'ın workflow dosyalarını da değiştirmesi için Workflows izni eklenir. */
    fun createUrl(withWorkflows: Boolean = false): String {
        val perms = LinkedHashMap(REQUIRED)
        if (withWorkflows) perms[OPTIONAL_WORKFLOWS.first] = OPTIONAL_WORKFLOWS.second
        val params = linkedMapOf(
            "name" to "Kripton Code",
            "description" to "Kripton Code: repo indirme, commit ve CI sonuclari",
            "expires_in" to EXPIRES_DAYS.toString(),
        ) + perms
        return BASE + "?" + params.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
    }

    /** Klasik token'lar (ghp_/gho_...) repo bazında sınırlanamaz; ince ayarlı olanlar `github_pat_` ile başlar. */
    fun isClassic(token: String): Boolean {
        val t = token.trim()
        return t.isNotEmpty() && !t.startsWith("github_pat_")
    }
}
