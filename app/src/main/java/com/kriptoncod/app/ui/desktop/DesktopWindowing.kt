package com.kriptoncod.app.ui.desktop

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Masaüstü modunun serbest pencere yöneticisi (saf mantık). Hiçbir Android/Compose türüne bağlı değildir; birim testle doğrulanır.
//
// Model:
//  - Her gömülebilir sekmenin (bkz. [DesktopState.tabs]) bir PENCERESİ vardır: [WinState]. Harici (pkg:) uygulamaların penceresi yoktur.
//  - Konum/boyut dp cinsinden Float'tır ve masaüstü ALANINA göre ölçülür (sol-üst = 0,0).
//  - [DesktopState.z] üst üste sırasıdır (son = en üstte). Odaklı pencere = en üstteki küçültülmemiş pencere ([DesktopState.activeId]).
//  - [WinState.geom] pencerenin "normal" (yüzen) dikdörtgenidir; büyütülmüşken de korunur. [WinState.restoreGeom], büyütmeden / yarım
//    ekran yaslamadan geri dönülecek dikdörtgendir (null: dönülecek bir şey yok). NORMAL modda restoreGeom doluysa pencere yaslıdır.

/** Pencere dikdörtgeni (dp). */
data class WinGeom(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h

    fun contains(px: Float, py: Float): Boolean = px >= x && px <= right && py >= y && py <= bottom

    /** Sonlu değerler ve pozitif boyut (konum eksi olabilir: sürüklemede taşan değer alana sıkıştırılır). */
    val isFiniteRect: Boolean get() = x.isFinite() && y.isFinite() && w.isFinite() && h.isFinite() && w > 0f && h > 0f

    /** Sonlu, eksi olmayan konum ve pozitif, makul boyut. Bozuk (NaN/sonsuz/negatif) kayıtları ayıklamak için. */
    val isSane: Boolean
        get() = x.isFinite() && y.isFinite() && w.isFinite() && h.isFinite() &&
            x >= 0f && y >= 0f && w > 0f && h > 0f &&
            x <= MAX_DIM && y <= MAX_DIM && w <= MAX_DIM && h <= MAX_DIM

    companion object {
        const val MAX_DIM = 20000f

        fun lerp(a: WinGeom, b: WinGeom, t: Float): WinGeom = WinGeom(
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t,
            a.w + (b.w - a.w) * t,
            a.h + (b.h - a.h) * t,
        )
    }
}

/** Masaüstü alanının boyutu (dp). */
data class WinArea(val w: Float, val h: Float) {
    val isUsable: Boolean get() = w.isFinite() && h.isFinite() && w > 0f && h > 0f

    /** Telefon / dar alan: yeni pencereler büyütülmüş açılır. */
    val narrow: Boolean get() = w < NARROW_WIDTH_DP

    val rect: WinGeom get() = WinGeom(0f, 0f, w, h)

    fun orDefault(): WinArea = if (isUsable) this else Default

    companion object {
        const val NARROW_WIDTH_DP = 600f
        val Default = WinArea(1280f, 720f)
    }
}

enum class WinMode { NORMAL, MAXIMIZED, MINIMIZED }

/** Pencere durumu. [beforeMin]: küçültülmeden önceki mod (yalnız NORMAL ya da MAXIMIZED); geri getirince ona dönülür. */
data class WinState(
    val geom: WinGeom,
    val mode: WinMode = WinMode.NORMAL,
    val restoreGeom: WinGeom? = null,
    val beforeMin: WinMode = WinMode.NORMAL,
) {
    val visible: Boolean get() = mode != WinMode.MINIMIZED

    /** Yarım/çeyrek ekrana yaslı mı (normal mod + dönülecek dikdörtgen var). */
    val snapped: Boolean get() = mode == WinMode.NORMAL && restoreGeom != null
}

/** Yaslama bölgeleri: kenarlar yarım ekran, üst = büyüt, köşeler = çeyrek. */
enum class SnapZone { LEFT, RIGHT, TOP, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** Yeniden boyutlandırma tutamacı (8 yön). */
enum class ResizeHandle(val west: Boolean = false, val east: Boolean = false, val north: Boolean = false, val south: Boolean = false) {
    N(north = true),
    S(south = true),
    W(west = true),
    E(east = true),
    NW(west = true, north = true),
    NE(east = true, north = true),
    SW(west = true, south = true),
    SE(east = true, south = true),
}

object Windowing {
    const val MIN_W = 320f
    const val MIN_H = 240f

    /** İlk cascade konumu ve ardışık pencerelerin kayma adımı (dp). */
    const val CASCADE_ORIGIN = 24f
    const val CASCADE_STEP = 32f

    /** İşaretçi alan kenarına bu kadar yakınsa yaslama bölgesi etkinleşir; köşe bölgesi kenar boyunca bu kadar uzanır. */
    const val SNAP_EDGE = 24f
    const val SNAP_CORNER = 72f

    private const val EPS = 1f

    /** [v]'yi [lo, hi] aralığına sıkıştırır; hi < lo ise lo döner (coerceIn gibi istisna fırlatmaz). */
    fun clampSafe(v: Float, lo: Float, hi: Float): Float = max(lo, min(v, hi))

    // ------------------------------------------------------------------------------------------------------------
    // Geometri
    // ------------------------------------------------------------------------------------------------------------

    /**
     * [g]'yi [area] içine sığdırır: alandan büyükse küçültür, dışarı taşıyorsa geri iter. [enforceMin] true ise en az
     * [MIN_W]x[MIN_H] kalır (alan bundan küçükse pencere taşar); false ise yalnız ekranda gösterirken (ör. klavye açıkken) kullanılır.
     */
    fun fitGeom(g: WinGeom, area: WinArea, enforceMin: Boolean = true): WinGeom {
        if (!area.isUsable) return g
        val minW = if (enforceMin) MIN_W else 1f
        val minH = if (enforceMin) MIN_H else 1f
        val w = max(minW, min(g.w, area.w))
        val h = max(minH, min(g.h, area.h))
        return WinGeom(clampSafe(g.x, 0f, area.w - w), clampSafe(g.y, 0f, area.h - h), w, h)
    }

    /** Pencerenin şu an ekranda kapladığı dikdörtgen (büyütülmüşse bütün alan). Yalnız gösterim içindir; durumu değiştirmez. */
    fun rectOf(w: WinState, area: WinArea): WinGeom {
        val effective = if (w.mode == WinMode.MINIMIZED) w.beforeMin else w.mode
        return if (effective == WinMode.MAXIMIZED && area.isUsable) area.rect else fitGeom(w.geom, area, enforceMin = false)
    }

    /** Sürükleme sırasında konum: boyut sabit, pencere alanın içinde kalır. */
    fun moveGeom(g: WinGeom, dx: Float, dy: Float, area: WinArea): WinGeom = fitGeom(g.copy(x = g.x + dx, y = g.y + dy), area, enforceMin = false)

    /**
     * [handle] tutamacını ([dx], [dy]) kadar sürükleyince oluşan dikdörtgen: karşı kenar sabit kalır, en az [MIN_W]x[MIN_H],
     * alanın dışına taşmaz.
     */
    fun resizeBy(g: WinGeom, handle: ResizeHandle, dx: Float, dy: Float, area: WinArea): WinGeom {
        val a = area.orDefault()
        var left = g.x
        var top = g.y
        var right = g.right
        var bottom = g.bottom
        if (handle.west) left = clampSafe(left + dx, 0f, right - MIN_W)
        if (handle.east) right = clampSafe(right + dx, left + MIN_W, a.w)
        if (handle.north) top = clampSafe(top + dy, 0f, bottom - MIN_H)
        if (handle.south) bottom = clampSafe(bottom + dy, top + MIN_H, a.h)
        return WinGeom(left, top, right - left, bottom - top)
    }

    // ------------------------------------------------------------------------------------------------------------
    // Yeni pencere: varsayılan boyut ve cascade
    // ------------------------------------------------------------------------------------------------------------

    private fun sizeFraction(app: String): Float = when (app) {
        DesktopApps.KRIPTON -> 0.70f
        DesktopApps.FILES -> 0.55f
        else -> 0.65f
    }

    /** [app]'in varsayılan boyutu (alanın %70'i Kripton, %55'i Dosyalar, %65'i AI/diğer); en az [MIN_W]x[MIN_H], alandan büyük değil. */
    fun defaultSize(app: String, area: WinArea): WinGeom {
        val a = area.orDefault()
        val f = sizeFraction(app)
        return WinGeom(0f, 0f, max(MIN_W, min(a.w * f, a.w)), max(MIN_H, min(a.h * f, a.h)))
    }

    private fun slotCount(size: WinGeom, a: WinArea): Int {
        val span = min(a.w - size.w, a.h - size.h) - CASCADE_ORIGIN
        return if (span <= 0f) 1 else (span / CASCADE_STEP).toInt() + 1
    }

    /** [slot]. cascade konumundaki varsayılan dikdörtgen; yuva sayısı aşılınca başa sarar. */
    fun cascadeGeom(app: String, area: WinArea, slot: Int): WinGeom {
        val a = area.orDefault()
        val size = defaultSize(app, a)
        val k = slot.mod(slotCount(size, a))
        val off = CASCADE_ORIGIN + k * CASCADE_STEP
        return WinGeom(clampSafe(off, 0f, a.w - size.w), clampSafe(off, 0f, a.h - size.h), size.w, size.h)
    }

    /**
     * Yeni pencere için cascade dikdörtgeni: görünen normal pencerelerin köşesine denk gelmeyen ilk yuva; hepsi doluysa sıradaki
     * yuvaya sarar.
     */
    fun cascadeFor(s: DesktopState, app: String, area: WinArea): WinGeom {
        val a = area.orDefault()
        val shown = s.z.mapNotNull { s.wins[it] }.filter { it.mode == WinMode.NORMAL }
        val count = slotCount(defaultSize(app, a), a)
        for (k in 0 until count) {
            val g = cascadeGeom(app, a, k)
            if (shown.none { abs(it.geom.x - g.x) < EPS && abs(it.geom.y - g.y) < EPS }) return g
        }
        return cascadeGeom(app, a, shown.size)
    }

    /** Yeni pencerenin durumu: cascade konum; dar alanda (< 600dp) büyütülmüş. */
    fun newWin(s: DesktopState, app: String, area: WinArea): WinState {
        val a = area.orDefault()
        val geom = cascadeFor(s, app, a)
        return if (a.narrow) WinState(geom, WinMode.MAXIMIZED, restoreGeom = geom) else WinState(geom)
    }

    /** [tab] için pencere oluşturur ve en üste koyar. Harici uygulama için çağrılmaz. */
    fun addWindow(s: DesktopState, tab: DesktopTab, area: WinArea): DesktopState = s.copy(wins = s.wins + (tab.id to newWin(s, tab.app, area)), z = s.z.filter { it != tab.id } + tab.id)

    fun removeWindow(s: DesktopState, id: Int): DesktopState = if (id !in s.wins && id !in s.z) {
        s
    } else {
        s.copy(wins = s.wins - id, z = s.z.filter { it != id }, shownDesktop = s.shownDesktop.filter { it != id })
    }

    // ------------------------------------------------------------------------------------------------------------
    // Odak / z-sırası
    // ------------------------------------------------------------------------------------------------------------

    /** Pencereyi öne alır; küçültülmüşse küçültmeden önceki moda geri getirir. Pencere yoksa durum aynı kalır. */
    fun focus(s: DesktopState, id: Int): DesktopState {
        val w = s.wins[id] ?: return s
        val restored = if (w.mode == WinMode.MINIMIZED) w.copy(mode = w.beforeMin) else w
        val z = if (s.z.lastOrNull() == id) s.z else s.z.filter { it != id } + id
        if (restored == w && z == s.z) return s
        return s.copy(wins = if (restored == w) s.wins else s.wins + (id to restored), z = z)
    }

    /** [app]'in açılırken öne gelecek sekmesi: görünen pencerelerin en üsttekisi; görünen yoksa ilk sekme. */
    fun pickWindow(s: DesktopState, app: String): DesktopTab? {
        val group = s.tabsOf(app)
        if (group.isEmpty()) return null
        if (DesktopApps.isExternal(app)) return group.first()
        val top = s.z.lastOrNull { id -> s.wins[id]?.visible == true && group.any { it.id == id } }
        return group.firstOrNull { it.id == top } ?: group.first()
    }

    /**
     * Çubuk öğesinin temsilci penceresi ([ids]: uygulamanın pencereleri): görünenlerin en üsttekisi (odaklı pencere de budur);
     * görünen yoksa en son kullanılan (z-sırasında en üstteki), o da yoksa ilk pencere.
     */
    fun dockRep(s: DesktopState, ids: List<Int>): Int? {
        if (ids.isEmpty()) return null
        return s.z.lastOrNull { it in ids && s.wins[it]?.visible == true }
            ?: s.z.lastOrNull { it in ids }
            ?: ids.first()
    }

    // ------------------------------------------------------------------------------------------------------------
    // Küçült / büyüt / geri al
    // ------------------------------------------------------------------------------------------------------------

    fun minimize(s: DesktopState, id: Int): DesktopState {
        val w = s.wins[id] ?: return s
        if (w.mode == WinMode.MINIMIZED) return s
        return s.copy(wins = s.wins + (id to w.copy(mode = WinMode.MINIMIZED, beforeMin = w.mode)))
    }

    /** Küçültülmüş pencereyi önceki moduyla geri getirir ve öne alır (küçültülmemişse yalnız öne alır). */
    fun restoreFromMin(s: DesktopState, id: Int): DesktopState = focus(s, id)

    fun maximize(s: DesktopState, id: Int): DesktopState {
        val base = focus(s, id)
        val w = base.wins[id] ?: return s
        if (w.mode == WinMode.MAXIMIZED) return base
        return base.copy(wins = base.wins + (id to w.copy(mode = WinMode.MAXIMIZED, restoreGeom = w.restoreGeom ?: w.geom)))
    }

    /** Büyütülmüşse ya da yaslıysa dönülecek dikdörtgene döner (normal mod); küçültülmüşse geri getirir. */
    fun restore(s: DesktopState, id: Int): DesktopState {
        val base = focus(s, id)
        val w = base.wins[id] ?: return s
        if (w.mode == WinMode.NORMAL && w.restoreGeom == null) return base
        val back = w.restoreGeom ?: w.geom
        return base.copy(wins = base.wins + (id to w.copy(mode = WinMode.NORMAL, geom = back, restoreGeom = null)))
    }

    fun toggleMaximize(s: DesktopState, id: Int): DesktopState {
        val w = s.wins[id] ?: return s
        return if (w.mode == WinMode.MAXIMIZED) restore(s, id) else maximize(s, id)
    }

    // ------------------------------------------------------------------------------------------------------------
    // Taşı / boyutlandır / alana sığdır
    // ------------------------------------------------------------------------------------------------------------

    /** Normal moddaki pencerenin dikdörtgenini kalıcı yapar (alana sığdırılır, yaslama iptal olur). */
    fun setGeom(s: DesktopState, id: Int, g: WinGeom, area: WinArea): DesktopState {
        val w = s.wins[id] ?: return s
        if (w.mode != WinMode.NORMAL || !g.isFiniteRect) return s
        return s.copy(wins = s.wins + (id to w.copy(geom = fitGeom(g, area), restoreGeom = null)))
    }

    fun move(s: DesktopState, id: Int, x: Float, y: Float, area: WinArea): DesktopState {
        val w = s.wins[id] ?: return s
        return setGeom(s, id, w.geom.copy(x = x, y = y), area)
    }

    /** Yeniden boyutlandırmayı kalıcı yapar (en az [MIN_W]x[MIN_H]); pencereyi öne alır. */
    fun resize(s: DesktopState, id: Int, g: WinGeom, area: WinArea): DesktopState = focus(setGeom(s, id, g, area), id)

    /**
     * Sürükleme bitince: pencere [g] konumuna bırakılır. Büyütülmüş ya da yaslı pencere sürüklenince normale döner (yaslama
     * bilgisi silinir) ve öne alınır.
     */
    fun placeFromDrag(s: DesktopState, id: Int, g: WinGeom, area: WinArea): DesktopState {
        val w = s.wins[id] ?: return s
        if (!g.isFiniteRect) return s
        val placed = w.copy(mode = WinMode.NORMAL, geom = fitGeom(g, area), restoreGeom = null)
        return focus(s.copy(wins = s.wins + (id to placed)), id)
    }

    /**
     * Büyütülmüş ya da yaslı pencerenin sürükleme başlangıç dikdörtgeni: pencere eski (geri alınmış) boyutuna döner ve işaretçinin
     * ([pointerX], alan koordinatı) pencere üzerindeki oransal noktası aynı kalır. Normal pencerede kendi dikdörtgenidir.
     */
    fun dragStartGeom(w: WinState, pointerX: Float, area: WinArea): WinGeom {
        val cur = rectOf(w, area)
        if (w.mode != WinMode.MAXIMIZED && !w.snapped) return cur
        val size = fitGeom(w.restoreGeom ?: w.geom, area, enforceMin = true)
        val frac = if (cur.w > 0f) ((pointerX - cur.x) / cur.w).coerceIn(0f, 1f) else 0.5f
        return fitGeom(WinGeom(pointerX - frac * size.w, cur.y, size.w, size.h), area, enforceMin = false)
    }

    /** Alan ya da ekran yönü değişince bütün pencereleri geri sığdırır (kalıcı). Hiçbir şey değişmiyorsa aynı durum döner. */
    fun clampToArea(s: DesktopState, area: WinArea): DesktopState {
        if (!area.isUsable || s.wins.isEmpty()) return s
        var changed = false
        val next = s.wins.mapValues { (_, w) ->
            val g = fitGeom(w.geom, area)
            val r = w.restoreGeom?.let { fitGeom(it, area) }
            if (g == w.geom && r == w.restoreGeom) {
                w
            } else {
                changed = true
                w.copy(geom = g, restoreGeom = r)
            }
        }
        return if (changed) s.copy(wins = next) else s
    }

    // ------------------------------------------------------------------------------------------------------------
    // Yaslama (snap)
    // ------------------------------------------------------------------------------------------------------------

    /** İşaretçi ([px], [py]: alan koordinatı, dp) hangi yaslama bölgesinde; hiçbirinde değilse null. Alt kenar bölge değildir. */
    fun snapZoneAt(px: Float, py: Float, area: WinArea): SnapZone? {
        if (!area.isUsable) return null
        val left = px <= SNAP_EDGE
        val right = px >= area.w - SNAP_EDGE
        val top = py <= SNAP_EDGE
        return when {
            left && py <= SNAP_CORNER -> SnapZone.TOP_LEFT
            left && py >= area.h - SNAP_CORNER -> SnapZone.BOTTOM_LEFT
            left -> SnapZone.LEFT
            right && py <= SNAP_CORNER -> SnapZone.TOP_RIGHT
            right && py >= area.h - SNAP_CORNER -> SnapZone.BOTTOM_RIGHT
            right -> SnapZone.RIGHT
            top -> SnapZone.TOP
            else -> null
        }
    }

    /** Bölgenin dikdörtgeni (önizleme ve yaslanmış pencere); asgari boyuttan küçük kalırsa kenara dayalı büyütülür. */
    fun snapRect(zone: SnapZone, area: WinArea): WinGeom {
        val a = area.orDefault()
        val hw = a.w / 2f
        val hh = a.h / 2f
        val raw = when (zone) {
            SnapZone.LEFT -> WinGeom(0f, 0f, hw, a.h)
            SnapZone.RIGHT -> WinGeom(hw, 0f, hw, a.h)
            SnapZone.TOP -> WinGeom(0f, 0f, a.w, a.h)
            SnapZone.TOP_LEFT -> WinGeom(0f, 0f, hw, hh)
            SnapZone.TOP_RIGHT -> WinGeom(hw, 0f, hw, hh)
            SnapZone.BOTTOM_LEFT -> WinGeom(0f, hh, hw, hh)
            SnapZone.BOTTOM_RIGHT -> WinGeom(hw, hh, hw, hh)
        }
        val w = max(raw.w, MIN_W)
        val h = max(raw.h, MIN_H)
        val x = if (raw.x > 0f) max(0f, a.w - w) else raw.x
        val y = if (raw.y > 0f) max(0f, a.h - h) else raw.y
        return WinGeom(x, y, w, h)
    }

    /** Pencereyi bölgeye yaslar: TOP büyütür; diğerleri yarım/çeyrek ekran. Dönülecek dikdörtgen korunur (ilk yaslamada eski konum). */
    fun snap(s: DesktopState, id: Int, zone: SnapZone, area: WinArea): DesktopState {
        if (zone == SnapZone.TOP) return maximize(s, id)
        val base = focus(s, id)
        val w = base.wins[id] ?: return s
        val back = w.restoreGeom ?: w.geom
        val next = w.copy(mode = WinMode.NORMAL, geom = snapRect(zone, area), restoreGeom = back)
        return base.copy(wins = base.wins + (id to next))
    }

    // ------------------------------------------------------------------------------------------------------------
    // Masaüstünü göster
    // ------------------------------------------------------------------------------------------------------------

    /** Görünen bütün pencereleri küçültür ve hangilerinin görünür olduğunu (z-sırasıyla) hatırlar. Görünen yoksa durum aynı kalır. */
    fun showDesktop(s: DesktopState): DesktopState {
        val visible = s.z.filter { s.wins[it]?.visible == true }
        if (visible.isEmpty()) return s
        val wins = s.wins.mapValues { (id, w) ->
            if (id in visible) w.copy(mode = WinMode.MINIMIZED, beforeMin = w.mode) else w
        }
        return s.copy(wins = wins, shownDesktop = visible)
    }

    /**
     * "Masaüstünü göster"den dönüş: hatırlanan pencereler (hâlâ küçültülmüşse) önceki z-sırasıyla geri gelir. Hatırlanan yoksa
     * yalnız en son kullanılan küçültülmüş pencere geri gelir (elle küçültülmüş hepsini birden açmaz).
     */
    fun restoreDesktop(s: DesktopState): DesktopState {
        val remembered = s.shownDesktop.filter { s.wins[it]?.mode == WinMode.MINIMIZED }
        val targets = remembered.ifEmpty { listOfNotNull(s.z.lastOrNull { s.wins[it]?.mode == WinMode.MINIMIZED }) }
        if (targets.isEmpty()) return if (s.shownDesktop.isEmpty()) s else s.copy(shownDesktop = emptyList())
        val ordered = s.z.filter { it in targets }
        return ordered.fold(s) { acc, id -> focus(acc, id) }.copy(shownDesktop = emptyList())
    }

    /** Görünen pencere varsa hepsini küçültür, yoksa geri getirir (Win+D gibi). */
    fun toggleDesktop(s: DesktopState): DesktopState = if (s.activeId != null) showDesktop(s) else restoreDesktop(s)

    // ------------------------------------------------------------------------------------------------------------
    // Çubuk (dock) davranışı
    // ------------------------------------------------------------------------------------------------------------

    /**
     * PC görev çubuğu mantığı: küçültülmüşse geri getirir ve öne alır; odaklıysa küçültür; görünür ama arkadaysa öne alır.
     * (Pencere kartları da aynı işlemi kullanır.)
     */
    fun dockTap(s: DesktopState, id: Int): DesktopState {
        val w = s.wins[id] ?: return s
        return when {
            w.mode == WinMode.MINIMIZED -> restoreFromMin(s, id)
            id == s.activeId -> minimize(s, id)
            else -> focus(s, id)
        }
    }
}
