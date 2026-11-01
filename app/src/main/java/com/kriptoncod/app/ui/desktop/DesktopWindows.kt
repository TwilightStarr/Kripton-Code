package com.kriptoncod.app.ui.desktop

import kotlin.math.max

// Masaüstü modunun sekme yöneticisi (saf mantık). Hiçbir Android/Compose türüne bağlı değildir; birim testle doğrulanır.
//
// Model (Linux masaüstü başlatıcısı gibi):
//  - Alt çubukta (dock) sabitlenmiş uygulamalar durur; kullanıcı istediği uygulamayı ekler/çıkarır/sıralar.
//  - Bir uygulama açılınca SEKME olur: aynı anda tek sekme görünür. Her uygulamanın en çok bir sekmesi vardır; yalnız
//    birden çok penceresi olabilen uygulamalar (bkz. [DesktopApps.allowsMultiple], ör. Dosyalar) çubukta TEK öğe altında
//    gruplanır (sayı rozeti + pencere kartları).
//  - Sekme yokken (ya da "masaüstünü göster" ile) boş masaüstü görünür; sekmeler arkada açık kalır.
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
 * Masaüstünün durumu. [activeId]: görünen sekme; null ise boş masaüstü görünür.
 * [pinned]: alt çubuğa sabitlenmiş uygulamalar (sıra = çubuktaki sıra).
 */
data class DesktopState(
    val tabs: List<DesktopTab> = emptyList(),
    val activeId: Int? = null,
    val pinned: List<String> = emptyList(),
    val nextId: Int = 1,
) {
    val active: DesktopTab? get() = tabs.firstOrNull { it.id == activeId }

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
    // Açma / kapatma / geçiş
    // ------------------------------------------------------------------------------------------------------------

    /**
     * [app]'i açar. Zaten açıksa yeni sekme yapılmaz; görünür yapılır. Gömülemeyen (harici) uygulamanın sekmesi yalnız
     * çubukta izlenir, görünen sekme olmaz ([DesktopState.activeId] değişmez).
     */
    fun open(s: DesktopState, app: String): DesktopState {
        val external = DesktopApps.isExternal(app)
        // Birden çok pencereli uygulamada zaten görünen pencere varsa o korunur; yoksa ilk pencere öne gelir.
        val existing = s.tabs.firstOrNull { it.app == app && it.id == s.activeId } ?: s.byApp(app)
        if (existing != null) return if (external) s else s.copy(activeId = existing.id)
        val tab = DesktopTab(s.nextId, app)
        return s.copy(
            tabs = s.tabs + tab,
            nextId = s.nextId + 1,
            activeId = if (external) s.activeId else tab.id,
        )
    }

    /** Sekmeyi görünür yapar (harici uygulama sekmesi görünür olamaz). */
    fun activate(s: DesktopState, id: Int): DesktopState {
        val tab = s.byId(id) ?: return s
        return if (DesktopApps.isExternal(tab.app)) s else s.copy(activeId = id)
    }

    /**
     * [app] için HER ZAMAN yeni pencere açar ve görünür yapar ([DesktopApps.allowsMultiple] olan uygulamalarda); diğerlerinde
     * [open] gibi davranır.
     */
    fun openNew(s: DesktopState, app: String): DesktopState {
        if (!DesktopApps.allowsMultiple(app)) return open(s, app)
        val tab = DesktopTab(s.nextId, app)
        return s.copy(tabs = s.tabs + tab, nextId = s.nextId + 1, activeId = tab.id)
    }

    /**
     * Aynı uygulamanın pencereleri arasında bir sonrakine geçer (sonuncudan ilkine döner). Görünen sekme bu uygulamadan değilse
     * ilk pencereyi öne getirir; tek pencere varsa bir şey yapmaz.
     */
    fun cycleInGroup(s: DesktopState, app: String): DesktopState {
        val group = s.tabsOf(app)
        if (group.size < 2) return s
        val at = group.indexOfFirst { it.id == s.activeId }
        return activate(s, group[(at + 1) % group.size].id)
    }

    /** Boş masaüstünü gösterir; sekmeler açık kalır. */
    fun showDesktop(s: DesktopState): DesktopState = if (s.activeId == null) s else s.copy(activeId = null)

    /**
     * Sekmeyi kapatır. Görünen sekme kapanırsa yerine sağındaki (yoksa solundaki) sekme gelir; hiçbiri yoksa masaüstü görünür.
     */
    fun close(s: DesktopState, id: Int): DesktopState {
        val index = s.tabs.indexOfFirst { it.id == id }
        if (index < 0) return s
        val rest = s.tabs.filter { it.id != id }
        val active = if (s.activeId != id) {
            s.activeId
        } else {
            rest.drop(index).firstOrNull { !DesktopApps.isExternal(it.app) }?.id
                ?: rest.take(index).lastOrNull { !DesktopApps.isExternal(it.app) }?.id
        }
        return s.copy(tabs = rest, activeId = active)
    }

    /** [app]'in bütün pencerelerini kapatır (görünen kapanırsa komşusu gelir; bkz. [close]). */
    fun closeApp(s: DesktopState, app: String): DesktopState = s.tabsOf(app).fold(s) { acc, tab -> close(acc, tab.id) }

    /** Bütün sekmeleri kapatır (sabitler kalır); boş masaüstü görünür. Kimlik sayacı korunur. */
    fun closeAll(s: DesktopState): DesktopState = if (s.tabs.isEmpty()) s else s.copy(tabs = emptyList(), activeId = null)

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
     * sırasıyla). Bir uygulamanın birden çok penceresi tek öğede toplanır ([DockEntry.tabIds]).
     */
    fun dock(s: DesktopState): List<DockEntry> {
        fun entry(app: String, pinned: Boolean): DockEntry {
            val ids = s.tabsOf(app).map { it.id }
            return DockEntry(app, ids.firstOrNull { it == s.activeId } ?: ids.firstOrNull(), pinned, ids)
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
 * Kalıcı metin biçimi (sürüm 2): `v2;next=5;active=3;t=<id>,<app>|...;p=<app>|<app>`.
 * Harici (Android) uygulama sekmeleri kaydedilmez. Bozuk/bilinmeyen parçalar atlanır; okunamayan metin için null döner.
 * Eski sürüm 1 (yüzen pencereler) okunur ve sekmelere çevrilir.
 */
object DesktopCodec {
    private const val V2 = "v2"
    private const val V1 = "v1"

    fun encode(s: DesktopState): String = buildString {
        append(V2)
        append(";next=").append(s.nextId)
        append(";active=").append(s.activeId?.toString().orEmpty())
        append(";t=").append(s.tabs.filter { !DesktopApps.isExternal(it.app) }.joinToString("|") { "${it.id},${it.app}" })
        append(";p=").append(s.pinned.joinToString("|"))
    }

    fun decode(text: String?): DesktopState? {
        if (text.isNullOrBlank()) return null
        val parts = text.split(';')
        val fields = parts.drop(1).mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        return when (parts.firstOrNull()) {
            V2 -> decodeV2(fields)
            V1 -> decodeV1(fields)
            else -> null
        }
    }

    private fun decodeV2(f: Map<String, String>): DesktopState {
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
        return DesktopState(tabs, active, pinned, next)
    }

    /** Sürüm 1: `w=<id>,<app>,<x>,<y>,<w>,<h>,<mod>,<küçük>|...` (liste sırası = üst üste sırası, sonuncu en üstte). */
    private fun decodeV1(f: Map<String, String>): DesktopState {
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
        val active = old.lastOrNull { !it.second }?.first?.id
        val next = max(f["next"]?.toIntOrNull() ?: 1, (tabs.maxOfOrNull { it.id } ?: 0) + 1)
        return DesktopState(tabs, active, DesktopApps.DEFAULT_PINNED, next)
    }
}

/** Başlıkta küçük bir saat için: 0-1439 dakikayı "SS:dd" yapar. */
fun formatClock(minuteOfDay: Int): String {
    val m = ((minuteOfDay % 1440) + 1440) % 1440
    return (m / 60).toString().padStart(2, '0') + ":" + (m % 60).toString().padStart(2, '0')
}
