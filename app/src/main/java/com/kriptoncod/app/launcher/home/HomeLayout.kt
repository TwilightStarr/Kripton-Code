package com.kriptoncod.app.launcher.home

/**
 * Ana ekranın düzeni (saf mantık; Android/Compose'a bağlı değil, birim testle doğrulanır).
 *
 * Ana ekran SAYFALardan oluşur; her sayfa sıralı ÖĞE listesidir (uygulama kısayolu, yerleşik pano, Android widget'ı).
 * Alt çubuktaki (dock) sabit uygulamalar masaüstü kabuğundaki mevcut sekme/sabit sistemi tarafından yönetilir
 * ([com.kriptoncod.app.ui.desktop.DesktopState.pinned]); burada yalnız sayfalar tutulur.
 */
sealed interface HomeItem {
    /** Kalıcı anahtar: kayıt biçiminde `;` `,` `|` `=` içermez. */
    val key: String

    /** Uygulama kısayolu. [app] masaüstü uygulama anahtarıdır (`kripton`, `files`, `ai:3`, `pkg:com.x`). */
    data class App(val app: String) : HomeItem {
        override val key: String get() = "a:$app"
    }

    /** Kripton'un kendi panosu (saat, AI hızlı sor, çalışma durumu, depolama, bildirim özeti). */
    data class Panel(val kind: PanelKind) : HomeItem {
        override val key: String get() = "p:${kind.id}"
    }

    /** Başka uygulamanın Android widget'ı. [widgetId] AppWidgetHost kimliği, [rows] kaplayacağı satır sayısı. */
    data class Widget(val widgetId: Int, val rows: Int = 2) : HomeItem {
        override val key: String get() = "w:$widgetId:$rows"
    }
}

enum class PanelKind(val id: String, val rows: Int) {
    CLOCK("clock", 2),
    AI_ASK("ai", 2),
    RUN_STATUS("run", 2),
    STORAGE("storage", 2),
    NOTIFICATIONS("notif", 2),
    ;

    companion object {
        fun fromId(id: String): PanelKind? = entries.firstOrNull { it.id == id }
    }
}

data class HomePage(val items: List<HomeItem> = emptyList())

data class HomeState(val pages: List<HomePage> = listOf(HomePage())) {
    val pageCount: Int get() = pages.size

    /** Her uygulama anahtarı en çok bir kez bulunur: ekranda zaten olan uygulama ikinci kez eklenmez. */
    fun hasApp(app: String): Boolean = pages.any { p -> p.items.any { it is HomeItem.App && it.app == app } }

    fun hasPanel(kind: PanelKind): Boolean = pages.any { p -> p.items.any { it is HomeItem.Panel && it.kind == kind } }
}

object Home {
    const val MAX_PAGES = 8
    const val MAX_ITEMS_PER_PAGE = 60

    /** İlk açılış: Kripton'un kendi panoları + Kripton/Dosyalar kısayolu. */
    fun initial(apps: List<String>): HomeState = HomeState(
        listOf(
            HomePage(
                listOf<HomeItem>(
                    HomeItem.Panel(PanelKind.CLOCK),
                    HomeItem.Panel(PanelKind.AI_ASK),
                ) + apps.map { HomeItem.App(it) },
            ),
            HomePage(
                listOf<HomeItem>(
                    HomeItem.Panel(PanelKind.RUN_STATUS),
                    HomeItem.Panel(PanelKind.STORAGE),
                    HomeItem.Panel(PanelKind.NOTIFICATIONS),
                ),
            ),
        ),
    )

    fun addApp(s: HomeState, app: String, page: Int): HomeState {
        if (s.hasApp(app)) return s
        return addItem(s, HomeItem.App(app), page)
    }

    fun addItem(s: HomeState, item: HomeItem, page: Int): HomeState {
        val p = page.coerceIn(0, s.pages.lastIndex)
        val target = s.pages[p]
        if (target.items.size >= MAX_ITEMS_PER_PAGE) {
            // Sayfa dolu: sonraki sayfa (yoksa yeni sayfa) kullanılır.
            return if (p < s.pages.lastIndex) {
                addItem(s, item, p + 1)
            } else if (s.pages.size < MAX_PAGES) {
                s.copy(pages = s.pages + HomePage(listOf(item)))
            } else {
                s
            }
        }
        return s.copy(pages = s.pages.mapIndexed { i, pg -> if (i == p) pg.copy(items = pg.items + item) else pg })
    }

    /** [key] anahtarlı öğeyi bütün sayfalardan kaldırır; boşalan (son sayfa dışındaki) sayfalar silinmez, kullanıcı kendi siler. */
    fun remove(s: HomeState, key: String): HomeState =
        s.copy(pages = s.pages.map { p -> p.copy(items = p.items.filter { it.key != key }) })

    fun removeApp(s: HomeState, app: String): HomeState = remove(s, HomeItem.App(app).key)

    /** Öğeyi sayfasında [delta] kadar kaydırır (negatif: öne). */
    fun move(s: HomeState, key: String, delta: Int): HomeState {
        val pi = s.pages.indexOfFirst { p -> p.items.any { it.key == key } }
        if (pi < 0) return s
        val items = s.pages[pi].items
        val from = items.indexOfFirst { it.key == key }
        val to = (from + delta).coerceIn(0, items.lastIndex)
        if (from == to) return s
        val list = items.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        return s.copy(pages = s.pages.mapIndexed { i, p -> if (i == pi) p.copy(items = list) else p })
    }

    /** Öğeyi başka sayfaya (sayfanın sonuna) taşır. */
    fun moveToPage(s: HomeState, key: String, page: Int): HomeState {
        val target = page.coerceIn(0, s.pages.lastIndex)
        val item = s.pages.flatMap { it.items }.firstOrNull { it.key == key } ?: return s
        if (s.pages[target].items.any { it.key == key }) return s
        if (s.pages[target].items.size >= MAX_ITEMS_PER_PAGE) return s
        val removed = remove(s, key)
        return removed.copy(pages = removed.pages.mapIndexed { i, p -> if (i == target) p.copy(items = p.items + item) else p })
    }

    fun addPage(s: HomeState): HomeState = if (s.pages.size >= MAX_PAGES) s else s.copy(pages = s.pages + HomePage())

    /** Sayfayı siler; son kalan sayfa silinmez (boşaltılır). */
    fun removePage(s: HomeState, page: Int): HomeState {
        if (page !in s.pages.indices) return s
        if (s.pages.size == 1) return s.copy(pages = listOf(HomePage()))
        return s.copy(pages = s.pages.filterIndexed { i, _ -> i != page })
    }

    /** [keep] false verdiği uygulamaları (ör. kaldırılan paketler, silinen AI hesapları) bütün sayfalardan temizler. */
    fun retainApps(s: HomeState, keep: (String) -> Boolean): HomeState =
        s.copy(pages = s.pages.map { p -> p.copy(items = p.items.filter { it !is HomeItem.App || keep(it.app) }) })
}

/**
 * Kalıcı metin biçimi: `h1;p=<öğe>|<öğe>/<öğe>|...` — sayfalar `/` ile, öğeler `|` ile ayrılır.
 * Öğe: `a:<uygulama>`, `p:<pano>`, `w:<kimlik>:<satır>`. Bozuk/bilinmeyen parçalar atlanır; okunamayan metin için null.
 */
object HomeCodec {
    private const val V1 = "h1"

    fun encode(s: HomeState): String =
        V1 + ";p=" + s.pages.joinToString("/") { p -> p.items.joinToString("|") { it.key } }

    fun decode(text: String?): HomeState? {
        if (text.isNullOrBlank()) return null
        val parts = text.split(';', limit = 2)
        if (parts.firstOrNull() != V1) return null
        val body = parts.getOrNull(1)?.takeIf { it.startsWith("p=") }?.removePrefix("p=") ?: return null
        val seenApps = HashSet<String>()
        val pages = body.split('/').take(Home.MAX_PAGES).map { raw ->
            HomePage(raw.split('|').mapNotNull { decodeItem(it, seenApps) }.take(Home.MAX_ITEMS_PER_PAGE))
        }
        return HomeState(pages.ifEmpty { listOf(HomePage()) })
    }

    private fun decodeItem(raw: String, seenApps: MutableSet<String>): HomeItem? {
        if (raw.length < 3) return null
        return when (raw.substring(0, 2)) {
            "a:" -> raw.substring(2).takeIf { it.isNotBlank() && seenApps.add(it) }?.let { HomeItem.App(it) }
            "p:" -> PanelKind.fromId(raw.substring(2))?.let { HomeItem.Panel(it) }
            "w:" -> {
                val a = raw.substring(2).split(':')
                val id = a.getOrNull(0)?.toIntOrNull()?.takeIf { it >= 0 } ?: return null
                val rows = (a.getOrNull(1)?.toIntOrNull() ?: 2).coerceIn(1, 8)
                HomeItem.Widget(id, rows)
            }
            else -> null
        }
    }
}

/** Ana ekran sayfasının çizim satırı: bir pano/widget tek satır tutar, art arda uygulamalar sütun sayısına göre satırlara bölünür. */
sealed interface HomeRow {
    data class Single(val item: HomeItem) : HomeRow
    data class Apps(val apps: List<HomeItem.App>) : HomeRow
}

object HomeRows {
    fun build(items: List<HomeItem>, columns: Int): List<HomeRow> {
        val cols = columns.coerceAtLeast(1)
        val out = ArrayList<HomeRow>()
        var run = ArrayList<HomeItem.App>()
        fun flush() {
            run.chunked(cols).forEach { out.add(HomeRow.Apps(it)) }
            run = ArrayList()
        }
        for (it in items) {
            if (it is HomeItem.App) {
                run.add(it)
            } else {
                flush()
                out.add(HomeRow.Single(it))
            }
        }
        flush()
        return out
    }
}
