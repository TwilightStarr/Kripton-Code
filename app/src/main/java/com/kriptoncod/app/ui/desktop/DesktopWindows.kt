package com.kriptoncod.app.ui.desktop

import kotlin.math.max

// Masaüstü modunun uygulama/pencere yöneticisi (saf mantık). Hiçbir Android/Compose türüne bağlı değildir; birim testle doğrulanır.
//
// Model (PC masaüstü gibi):
//  - Alt çubukta (dock) sabitlenmiş uygulamalar durur; kullanıcı istediği uygulamayı ekler/çıkarır/sıralar.
//  - Bir uygulama açılınca bir SEKME (ve onun serbest PENCERESİ, bkz. [WinState] / [Windowing]) olur. Her uygulamanın en çok
//    bir sekmesi vardır; yalnız birden çok penceresi olabilen uygulamalar (bkz. [DesktopApps.allowsMultiple], ör. Dosyalar)
//    çubukta TEK öğe altında gruplanır (sayı rozeti + pencere kartları).
//  - Pencereler aynı anda görünür, üst üste biner, taşınır, boyutlanır, yaslanır; küçültülen pencere arkada açık kalır.
//    Hiç görünen pencere yokken (ya da "masaüstünü göster" ile) boş masaüstü görünür.
//  - Cihazdaki diğer Android uygulamaları ("pkg:" anahtarı) Android'in izin vermediği için pencere içine gömülemez:
//    tam ekran açılır, çubukta açık sekme olarak izlenir; dokununca yeniden öne gelir. Görünen sekme olamazlar ve kaydedilmez.

/** Açık bir sekme. [app] uygulama anahtarıdır (bkz. [DesktopApps]). */
data class DesktopTab(val id: Int, val app: String)

/**
 * Alt çubuktaki bir öğe: sabitlenmiş ve/veya açık bir uygulama. [tabId] null ise uygulama açık değil.
 * [tabIds]: uygulamanın bütün pencereleri (açılış sırasıyla); birden çoksa öğe bir GRUPTUR. [tabId] gruptan görünen
 * pencere (görünen yoksa ilk pencere).
 */
data class DockEntry(val app: String, val tabId: Int?, val pinned: Boolean, val tabIds: List<Int> = listOfNotNull(tabId)) {
    val open: Boolean get() = tabId != null
    val count: Int get() = tabIds.size
    val grouped: Boolean get() = tabIds.size > 1
}

/**
 * Masaüstünün durumu.
 *  - [tabs]: açık sekmeler (açılış sırasıyla). [pinned]: alt çubuğa sabitlenmiş uygulamalar (sıra = çubuktaki sıra).
 *  - [wins]: her gömülebilir sekmenin penceresi (harici uygulamaların yoktur). [z]: üst üste sırası, son = en üstte.
 *  - [shownDesktop]: "masaüstünü göster" ile küçültülen pencereler (geri getirmek için).
 *  - [activeId]: odaklı pencere = en üstteki küçültülmemiş pencere; null ise boş masaüstü görünür.
 */
data class DesktopState(
    val tabs: List<DesktopTab> = emptyList(),
    val pinned: List<String> = emptyList(),
    val nextId: Int = 1,
    val wins: Map<Int, WinState> = emptyMap(),
    val z: List<Int> = emptyList(),
    val shownDesktop: List<Int> = emptyList(),
) {
    val activeId: Int? get() = z.lastOrNull { wins[it]?.visible == true }

    val active: DesktopTab? get() = activeId?.let { id -> tabs.firstOrNull { it.id == id } }

    fun byApp(app: String): DesktopTab? = tabs.firstOrNull { it.app == app }

    /** [app]'in bütün pencereleri (açılış sırasıyla). */
    fun tabsOf(app: String): List<DesktopTab> = tabs.filter { it.app == app }

    /** [id] sekmesinin kendi uygulaması içindeki sırası (1'den); sekme yoksa 0. Aynı uygulamanın pencerelerini ayırt etmek için. */
    fun ordinalOf(id: Int): Int {
        val tab = byId(id) ?: return 0
        return tabsOf(tab.app).indexOfFirst { it.id == id } + 1
    }

    fun byId(id: Int): DesktopTab? = tabs.firstOrNull { it.id == id }

    fun isPinned(app: String): Boolean = app in pinned
}

/** Uygulama anahtarları. Anahtar kalıcı kaydedildiği için biçimi sabittir (`;` `,` `|` `=` içermez). */
object DesktopApps {
    const val FILES = "files"
    const val KRIPTON = "kripton"
    private const val AI_PREFIX = "ai:"
    private const val PKG_PREFIX = "pkg:"

    /** İlk açılışta alt çubukta sabit gelenler. */
    val DEFAULT_PINNED: List<String> = listOf(KRIPTON, FILES)

    fun ai(accountId: Long): String = "$AI_PREFIX$accountId"

    /** `ai:<kimlik>` ise hesap kimliği; değilse null. */
    fun aiAccountId(app: String): Long? = if (app.startsWith(AI_PREFIX)) app.removePrefix(AI_PREFIX).toLongOrNull() else null

    /** Cihazdaki bir Android uygulaması. */
    fun pkg(packageName: String): String = "$PKG_PREFIX$packageName"

    /** `pkg:<paket>` ise paket adı; değilse null. */
    fun packageOf(app: String): String? = if (app.startsWith(PKG_PREFIX)) app.removePrefix(PKG_PREFIX).takeIf { it.isNotBlank() } else null

    /** Pencereye gömülemeyen (ayrı Android görevi olarak açılan) uygulama mı? */
    fun isExternal(app: String): Boolean = app.startsWith(PKG_PREFIX)

    /**
     * Birden çok penceresi olabilen uygulama mı? Yalnız durumu pencereye ait olanlar (Dosyalar: her pencerenin kendi klasörü
     * var). AI sekmeleri hesap başına tek web oturumudur, Kripton Code tek uygulama yığınıdır; ikisi de tek kalır.
     */
    fun allowsMultiple(app: String): Boolean = app == FILES
}

object Desktop {
    // ------------------------------------------------------------------------------------------------------------
    // Açma / kapatma / odak
    // ------------------------------------------------------------------------------------------------------------

    /**
     * [app]'i açar. Zaten açıksa yeni pencere yapılmaz; mevcut pencere öne gelir (küçültülmüşse geri gelir). Yeni pencere
     * [area] içinde cascade konumla açılır (dar alanda büyütülmüş). Gömülemeyen (harici) uygulamanın sekmesi yalnız çubukta
     * izlenir: penceresi yoktur, odak değişmez.
     */
    fun open(s: DesktopState, app: String, area: WinArea = WinArea.Default): DesktopState {
        val external = DesktopApps.isExternal(app)
        val existing = Windowing.pickWindow(s, app)
        if (existing != null) return if (external) s else Windowing.focus(s, existing.id)
        val tab = DesktopTab(s.nextId, app)
        val next = s.copy(tabs = s.tabs + tab, nextId = s.nextId + 1)
        return if (external) next else Windowing.addWindow(next, tab, area)
    }

    /** Pencereyi öne alır (küçültülmüşse geri getirir). Harici uygulama sekmesi öne gelemez. */
    fun activate(s: DesktopState, id: Int): DesktopState {
        val tab = s.byId(id) ?: return s
        return if (DesktopApps.isExternal(tab.app)) s else Windowing.focus(s, id)
    }

    /**
     * [app] için HER ZAMAN yeni pencere açar ve öne alır ([DesktopApps.allowsMultiple] olan uygulamalarda); diğerlerinde
     * [open] gibi davranır.
     */
    fun openNew(s: DesktopState, app: String, area: WinArea = WinArea.Default): DesktopState {
        if (!DesktopApps.allowsMultiple(app)) return open(s, app, area)
        val tab = DesktopTab(s.nextId, app)
        return Windowing.addWindow(s.copy(tabs = s.tabs + tab, nextId = s.nextId + 1), tab, area)
    }

    /**
     * Aynı uygulamanın pencereleri arasında bir sonrakine geçer (sonuncudan ilkine döner). Odaklı pencere bu uygulamadan değilse
     * ilk pencereyi öne getirir; tek pencere varsa bir şey yapmaz.
     */
    fun cycleInGroup(s: DesktopState, app: String): DesktopState {
        val group = s.tabsOf(app)
        if (group.size < 2) return s
        val at = group.indexOfFirst { it.id == s.activeId }
        return activate(s, group[(at + 1) % group.size].id)
    }

    /** Bütün görünen pencereleri küçültür (boş masaüstü görünür); pencereler açık kalır. Bkz. [Windowing.showDesktop]. */
    fun showDesktop(s: DesktopState): DesktopState = Windowing.showDesktop(s)

    /** Masaüstünü göster / geri getir (Win+D gibi): görünen pencere varsa hepsini küçültür, yoksa önceki pencereleri geri getirir. */
    fun toggleDesktop(s: DesktopState): DesktopState = Windowing.toggleDesktop(s)

    /**
     * Pencereyi kapatır. Odaklı pencere kapanırsa odak z-sırasında bir sonrakine (en üstteki küçültülmemiş pencereye) geçer;
     * hepsi küçültülmüşse boş masaüstü görünür.
     */
    fun close(s: DesktopState, id: Int): DesktopState {
        if (s.tabs.none { it.id == id }) return s
        return Windowing.removeWindow(s.copy(tabs = s.tabs.filter { it.id != id }), id)
    }

    /** [app]'in bütün pencerelerini kapatır (odak sırayla komşuya geçer; bkz. [close]). */
    fun closeApp(s: DesktopState, app: String): DesktopState = s.tabsOf(app).fold(s) { acc, tab -> close(acc, tab.id) }

    /** Bütün sekmeleri kapatır (sabitler kalır); boş masaüstü görünür. Kimlik sayacı korunur. */
    fun closeAll(s: DesktopState): DesktopState =
        if (s.tabs.isEmpty()) s else s.copy(tabs = emptyList(), wins = emptyMap(), z = emptyList(), shownDesktop = emptyList())

    // ------------------------------------------------------------------------------------------------------------
    // Alt çubuk (sabitleme)
    // ------------------------------------------------------------------------------------------------------------

    fun pin(s: DesktopState, app: String): DesktopState = if (app in s.pinned) s else s.copy(pinned = s.pinned + app)

    fun unpin(s: DesktopState, app: String): DesktopState = if (app !in s.pinned) s else s.copy(pinned = s.pinned.filter { it != app })

    fun togglePin(s: DesktopState, app: String): DesktopState = if (app in s.pinned) unpin(s, app) else pin(s, app)

    /** Sabit uygulamayı çubukta [delta] kadar kaydırır (negatif: sola). */
    fun movePinned(s: DesktopState, app: String, delta: Int): DesktopState {
        val from = s.pinned.indexOf(app)
        if (from < 0) return s
        val to = (from + delta).coerceIn(0, s.pinned.lastIndex)
        if (from == to) return s
        val list = s.pinned.toMutableList()
        list.removeAt(from)
        list.add(to, app)
        return s.copy(pinned = list)
    }

    /**
     * Çubuktaki sıra: önce sabitler (sabitleme sırasıyla), sonra sabit olmayan açık uygulamalar (ilk pencerelerinin açılış
     * sırasıyla). Bir uygulamanın birden çok penceresi tek öğede toplanır ([DockEntry.tabIds]); öğenin temsilci penceresi
     * [Windowing.dockRep]'tir.
     */
    fun dock(s: DesktopState): List<DockEntry> {
        fun entry(app: String, pinned: Boolean): DockEntry {
            val ids = s.tabsOf(app).map { it.id }
            return DockEntry(app, Windowing.dockRep(s, ids), pinned, ids)
        }
        val pinned = s.pinned.map { entry(it, pinned = true) }
        val extra = s.tabs.map { it.app }.distinct().filter { it !in s.pinned }.map { entry(it, pinned = false) }
        return pinned + extra
    }

    /** [keep] false verdiği uygulamaların sekmelerini kapatır ve sabitlerini kaldırır (ör. silinen AI hesapları). */
    fun retain(s: DesktopState, keep: (String) -> Boolean): DesktopState {
        var next = s
        s.tabs.filter { !keep(it.app) }.forEach { next = close(next, it.id) }
        val pins = next.pinned.filter(keep)
        return if (pins.size == next.pinned.size) next else next.copy(pinned = pins)
    }
}

/**
 * Kalıcı metin biçimi (sürüm 3):
 * `v3;next=5;t=<kayıt>|...;z=<kimlik>|...;sd=<kimlik>|...;p=<app>|<app>`.
 * Pencere kaydı 12 alandır: `kimlik,app,x,y,w,h,mod,küçültmeÖncesi,rx,ry,rw,rh` (mod: n normal, x büyütülmüş, m küçültülmüş;
 * rx..rh dönülecek dikdörtgen, yoksa boş). Konum/boyut dp'dir. `z` üst üste sırasıdır (son = en üstte), `sd` "masaüstünü göster"
 * ile küçültülenlerdir.
 *
 * Harici (Android) uygulama sekmeleri kaydedilmez. Bozuk/bilinmeyen parçalar atlanır; bozuk, negatif ya da NaN geometri varsayılan
 * cascade konumuna döner; okunamayan metin için null döner. Sürüm 1 (yüzen pencereler) ve sürüm 2 (tek görünen sekme) okunur ve
 * cascade pencerelere çevrilir (v2'de yalnız görünen sekme açık pencere olur, diğerleri küçültülmüş; dar alanda büyütülmüş).
 */
object DesktopCodec {
    private const val V3 = "v3"
    private const val V2 = "v2"
    private const val V1 = "v1"
    private const val V3_FIELDS = 12

    fun encode(s: DesktopState): String = buildString {
        append(V3)
        append(";next=").append(s.nextId)
        append(";t=").append(s.tabs.filter { !DesktopApps.isExternal(it.app) }.joinToString("|") { encodeTab(it, s.wins[it.id]) })
        append(";z=").append(s.z.joinToString("|"))
        append(";sd=").append(s.shownDesktop.joinToString("|"))
        append(";p=").append(s.pinned.joinToString("|"))
    }

    private fun encodeTab(tab: DesktopTab, w: WinState?): String {
        val win = w ?: WinState(Windowing.cascadeGeom(tab.app, WinArea.Default, 0))
        val g = win.geom
        val r = win.restoreGeom
        return listOf(
            tab.id.toString(),
            tab.app,
            g.x.toString(),
            g.y.toString(),
            g.w.toString(),
            g.h.toString(),
            modeChar(win.mode),
            modeChar(win.beforeMin),
            r?.x?.toString().orEmpty(),
            r?.y?.toString().orEmpty(),
            r?.w?.toString().orEmpty(),
            r?.h?.toString().orEmpty(),
        ).joinToString(",")
    }

    private fun modeChar(m: WinMode): String = when (m) {
        WinMode.NORMAL -> "n"
        WinMode.MAXIMIZED -> "x"
        WinMode.MINIMIZED -> "m"
    }

    private fun parseMode(c: String): WinMode = when (c) {
        "x" -> WinMode.MAXIMIZED
        "m" -> WinMode.MINIMIZED
        else -> WinMode.NORMAL
    }

    /** Küçültme öncesi modu yalnız NORMAL ya da MAXIMIZED olabilir. */
    private fun parseBeforeMin(c: String): WinMode = if (c == "x") WinMode.MAXIMIZED else WinMode.NORMAL

    fun decode(text: String?, area: WinArea = WinArea.Default): DesktopState? {
        if (text.isNullOrBlank()) return null
        val parts = text.split(';')
        val fields = parts.drop(1).mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        val a = area.orDefault()
        return when (parts.firstOrNull()) {
            V3 -> decodeV3(fields, a)
            V2 -> decodeV2(fields, a)
            V1 -> decodeV1(fields, a)
            else -> null
        }
    }

    /** Dört alanı geometriye çevirir; bozuk (NaN, negatif, sıfır boyut, boş) ise null. Boyut asgarinin altındaysa asgariye çıkarılır. */
    private fun parseGeom(a: List<String>, from: Int): WinGeom? {
        val x = a[from].toFloatOrNull() ?: return null
        val y = a[from + 1].toFloatOrNull() ?: return null
        val w = a[from + 2].toFloatOrNull() ?: return null
        val h = a[from + 3].toFloatOrNull() ?: return null
        val g = WinGeom(x, y, w, h)
        return if (g.isSane) g.copy(w = max(g.w, Windowing.MIN_W), h = max(g.h, Windowing.MIN_H)) else null
    }

    private fun decodeV3(f: Map<String, String>, area: WinArea): DesktopState {
        val seenIds = HashSet<Int>()
        val seenApps = HashSet<String>()
        val tabs = ArrayList<DesktopTab>()
        val wins = LinkedHashMap<Int, WinState>()
        f["t"].orEmpty().split('|').forEach { item ->
            val a = item.split(',')
            if (a.size != V3_FIELDS) return@forEach
            val id = a[0].toIntOrNull()?.takeIf { it > 0 } ?: return@forEach
            val app = a[1].takeIf { it.isNotBlank() && !DesktopApps.isExternal(it) } ?: return@forEach
            if (!seenIds.add(id) || (!DesktopApps.allowsMultiple(app) && !seenApps.add(app))) return@forEach
            val geom = parseGeom(a, 2) ?: Windowing.cascadeGeom(app, area, tabs.size)
            tabs += DesktopTab(id, app)
            wins[id] = WinState(geom, parseMode(a[6]), parseGeom(a, 8), parseBeforeMin(a[7]))
        }
        val zSaved = f["z"].orEmpty().split('|').mapNotNull { it.toIntOrNull() }.filter { it in wins }.distinct()
        val z = zSaved + wins.keys.filter { it !in zSaved }
        val shown = f["sd"].orEmpty().split('|').mapNotNull { it.toIntOrNull() }.filter { it in wins }.distinct()
        val pinned = f["p"].orEmpty().split('|').filter { it.isNotBlank() }.distinct()
        val next = max(f["next"]?.toIntOrNull() ?: 1, (tabs.maxOfOrNull { it.id } ?: 0) + 1)
        return DesktopState(tabs, pinned, next, wins, z, shown)
    }

    /**
     * Eski sürümlerden gelen sekmeleri cascade pencerelere çevirir. [minimized]: küçültülmüş başlayacaklar; [order]: z-sırası
     * (son = en üstte). Dar alanda pencereler büyütülmüştür.
     */
    private fun migrate(tabs: List<DesktopTab>, order: List<Int>, minimized: Set<Int>, pinned: List<String>, next: Int, area: WinArea): DesktopState {
        val base = if (area.narrow) WinMode.MAXIMIZED else WinMode.NORMAL
        val wins = LinkedHashMap<Int, WinState>()
        tabs.forEachIndexed { i, tab ->
            val geom = Windowing.cascadeGeom(tab.app, area, i)
            val restore = if (base == WinMode.MAXIMIZED) geom else null
            wins[tab.id] = WinState(geom, if (tab.id in minimized) WinMode.MINIMIZED else base, restore, base)
        }
        val z = order.filter { it in wins }.distinct().let { saved -> saved + wins.keys.filter { it !in saved } }
        return DesktopState(tabs, pinned, next, wins, z)
    }

    private fun decodeV2(f: Map<String, String>, area: WinArea): DesktopState {
        val seenIds = HashSet<Int>()
        val seenApps = HashSet<String>()
        val tabs = f["t"].orEmpty().split('|').mapNotNull { item ->
            val a = item.split(',')
            if (a.size != 2) return@mapNotNull null
            val id = a[0].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val app = a[1].takeIf { it.isNotBlank() && !DesktopApps.isExternal(it) } ?: return@mapNotNull null
            if (!seenIds.add(id) || (!DesktopApps.allowsMultiple(app) && !seenApps.add(app))) return@mapNotNull null
            DesktopTab(id, app)
        }
        val pinned = f["p"].orEmpty().split('|').filter { it.isNotBlank() }.distinct()
        val active = f["active"]?.toIntOrNull()?.takeIf { id -> tabs.any { it.id == id } }
        val next = max(f["next"]?.toIntOrNull() ?: 1, (tabs.maxOfOrNull { it.id } ?: 0) + 1)
        // Sürüm 2'de yalnız etkin sekme görünürdü: o açık pencere olur ve en üste gelir, diğerleri küçültülmüş başlar.
        val order = tabs.map { it.id }.filter { it != active } + listOfNotNull(active)
        return migrate(tabs, order, tabs.map { it.id }.filter { it != active }.toSet(), pinned, next, area)
    }

    /** Sürüm 1: `w=<id>,<app>,<x>,<y>,<w>,<h>,<mod>,<küçük>|...` (liste sırası = üst üste sırası, sonuncu en üstte). */
    private fun decodeV1(f: Map<String, String>, area: WinArea): DesktopState {
        val seenIds = HashSet<Int>()
        val seenApps = HashSet<String>()
        val old = f["w"].orEmpty().split('|').mapNotNull { item ->
            val a = item.split(',')
            if (a.size != 8) return@mapNotNull null
            val id = a[0].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val app = a[1].takeIf { it.isNotBlank() && !DesktopApps.isExternal(it) } ?: return@mapNotNull null
            if (!seenIds.add(id) || !seenApps.add(app)) return@mapNotNull null
            DesktopTab(id, app) to (a[7] == "1")
        }
        val tabs = old.map { it.first }.sortedBy { it.id }
        val next = max(f["next"]?.toIntOrNull() ?: 1, (tabs.maxOfOrNull { it.id } ?: 0) + 1)
        return migrate(tabs, old.map { it.first.id }, old.filter { it.second }.map { it.first.id }.toSet(), DesktopApps.DEFAULT_PINNED, next, area)
    }
}

/** Başlıkta küçük bir saat için: 0-1439 dakikayı "SS:dd" yapar. */
fun formatClock(minuteOfDay: Int): String {
    val m = ((minuteOfDay % 1440) + 1440) % 1440
    return (m / 60).toString().padStart(2, '0') + ":" + (m % 60).toString().padStart(2, '0')
}
