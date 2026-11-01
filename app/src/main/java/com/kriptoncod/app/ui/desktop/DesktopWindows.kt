package com.kriptoncod.app.ui.desktop

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Masaüstü modunun pencere yöneticisi (saf mantık). Hiçbir Android/Compose türüne bağlı değildir; birim testle doğrulanır.
// Tüm ölçüler dp cinsindendir ve masaüstü alanına (görev çubuğu hariç) göredir. Böylece yoğunluk/döndürme değişince
// kayıtlı düzen bozulmaz; sığmayan pencereler çizim sırasında alana sığdırılır (bkz. [Desktop.fit]).
//
// Doğrudan UI (DeX benzeri harici ekran) desteği için tasarım gereği ekrandan bağımsızdır: yeni bir ekran/alan,
// yalnızca farklı bir [DSize] vermektir; pencere kuralları aynı kalır.

/** Dikdörtgen (dp). */
data class DBox(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h
    val cx: Float get() = x + w / 2f
    val cy: Float get() = y + h / 2f

    fun contains(px: Float, py: Float): Boolean = px >= x && px < right && py >= y && py < bottom
}

/** Boyut (dp). */
data class DSize(val w: Float, val h: Float)

/** Pencerenin durumu. [NORMAL] dışındakiler yerleşimini alandan türetir (kayan pencerenin kutusu geri dönüş içindir). */
enum class WinMode(val key: String) {
    NORMAL("n"),
    MAXIMIZED("max"),
    SNAP_LEFT("l"),
    SNAP_RIGHT("r"),
    SNAP_TOP_LEFT("tl"),
    SNAP_TOP_RIGHT("tr"),
    SNAP_BOTTOM_LEFT("bl"),
    SNAP_BOTTOM_RIGHT("br"),
    ;

    companion object {
        fun fromKey(key: String?): WinMode = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

/** Yeniden boyutlandırmada hangi kenarlar tutuluyor (bit alanı). */
object Edge {
    const val LEFT = 1
    const val RIGHT = 2
    const val TOP = 4
    const val BOTTOM = 8
}

/**
 * Bir masaüstü penceresi. [app] uygulama anahtarıdır (bkz. [DesktopApps]); her uygulamanın en çok bir penceresi vardır.
 * [box]: kayan (NORMAL) pencerenin kutusu; kenara yaslı/büyütülmüş pencerede, geri alınınca dönülecek kutudur.
 */
data class DesktopWindow(
    val id: Int,
    val app: String,
    val box: DBox,
    val mode: WinMode = WinMode.NORMAL,
    val minimized: Boolean = false,
)

/** Pencere listesi; liste sırası üst üste sırasıdır (sonuncu en üstte). Odaktaki pencere, en üstteki küçültülmemiş olandır. */
data class DesktopState(
    val windows: List<DesktopWindow> = emptyList(),
    val nextId: Int = 1,
) {
    val focused: DesktopWindow? get() = windows.lastOrNull { !it.minimized }

    fun byApp(app: String): DesktopWindow? = windows.firstOrNull { it.app == app }

    fun byId(id: Int): DesktopWindow? = windows.firstOrNull { it.id == id }
}

/** Uygulama anahtarları. Anahtar kalıcı kaydedildiği için biçimi sabittir. */
object DesktopApps {
    const val FILES = "files"
    const val KRIPTON = "kripton"
    private const val AI_PREFIX = "ai:"

    fun ai(accountId: Long): String = "$AI_PREFIX$accountId"

    /** `ai:<kimlik>` ise hesap kimliği; değilse null. */
    fun aiAccountId(app: String): Long? = if (app.startsWith(AI_PREFIX)) app.removePrefix(AI_PREFIX).toLongOrNull() else null
}

object Desktop {
    /** Bir pencerenin en küçük boyutu (dp). */
    val MIN_SIZE = DSize(320f, 240f)

    /** Pencereyi sürüklerken en az bu kadarı (başlık çubuğu) alanda görünür kalır; pencere kaybolmaz. */
    const val GRAB_W = 96f
    const val GRAB_H = 40f

    /** Kenar/köşe yaslama bölgesinin kalınlığı ve köşe bölgesinin kenarı (dp). */
    const val SNAP_MARGIN = 20f
    const val SNAP_CORNER = 72f

    /** Boşluk: döşeme/yaslama düzenlerinde pencereler arası (dp). */
    const val GAP = 6f

    // ------------------------------------------------------------------------------------------------------------
    // Yerleşim
    // ------------------------------------------------------------------------------------------------------------

    /** [mode] için [area] içindeki kutu; NORMAL için null (kendi kutusu geçerli). */
    fun modeBox(mode: WinMode, area: DSize): DBox? {
        val hw = area.w / 2f
        val hh = area.h / 2f
        return when (mode) {
            WinMode.NORMAL -> null
            WinMode.MAXIMIZED -> DBox(0f, 0f, area.w, area.h)
            WinMode.SNAP_LEFT -> DBox(0f, 0f, hw, area.h)
            WinMode.SNAP_RIGHT -> DBox(hw, 0f, area.w - hw, area.h)
            WinMode.SNAP_TOP_LEFT -> DBox(0f, 0f, hw, hh)
            WinMode.SNAP_TOP_RIGHT -> DBox(hw, 0f, area.w - hw, hh)
            WinMode.SNAP_BOTTOM_LEFT -> DBox(0f, hh, hw, area.h - hh)
            WinMode.SNAP_BOTTOM_RIGHT -> DBox(hw, hh, area.w - hw, area.h - hh)
        }
    }

    /** Pencerenin şu an ekranda kapladığı kutu (kayan pencere alana sığdırılır; ör. klavye açıkken alan küçülür). */
    fun boxOf(win: DesktopWindow, area: DSize): DBox = modeBox(win.mode, area) ?: fit(win.box, area)

    /** [b]'yi alana sığdırır: alandan büyükse küçültür, dışarı taşıyorsa içeri çeker. */
    fun fit(b: DBox, area: DSize): DBox {
        val w = min(b.w, area.w)
        val h = min(b.h, area.h)
        val x = b.x.coerceIn(0f, max(0f, area.w - w))
        val y = b.y.coerceIn(0f, max(0f, area.h - h))
        return DBox(x, y, w, h)
    }

    // ------------------------------------------------------------------------------------------------------------
    // Açma / kapatma / odak
    // ------------------------------------------------------------------------------------------------------------

    /**
     * [app]'i açar. Zaten açıksa yeni pencere yapılmaz: küçültülmüşse geri getirilir ve öne alınır.
     * [initial]: yeni pencerenin istenen boyutu; verilmezse alana göre seçilir.
     */
    fun open(s: DesktopState, app: String, area: DSize, initial: DSize? = null, mode: WinMode = WinMode.NORMAL): DesktopState {
        s.byApp(app)?.let { return raise(s, it.id) }
        val size = initial ?: DSize(area.w * 0.62f, area.h * 0.74f)
        val w = size.w.coerceIn(min(MIN_SIZE.w, area.w), max(min(MIN_SIZE.w, area.w), area.w))
        val h = size.h.coerceIn(min(MIN_SIZE.h, area.h), max(min(MIN_SIZE.h, area.h), area.h))
        // Kademeli yerleşim: her yeni pencere bir öncekinin biraz sağ-altına düşer.
        val step = (s.windows.size % 8) * 30f
        val box = fit(DBox(24f + step, 24f + step, w, h), area)
        val win = DesktopWindow(s.nextId, app, box, mode)
        return s.copy(windows = s.windows + win, nextId = s.nextId + 1)
    }

    fun close(s: DesktopState, id: Int): DesktopState = s.copy(windows = s.windows.filter { it.id != id })

    /** Pencereyi öne alır ve küçültülmüşse geri getirir. */
    fun raise(s: DesktopState, id: Int): DesktopState {
        val win = s.byId(id) ?: return s
        val rest = s.windows.filter { it.id != id }
        return s.copy(windows = rest + win.copy(minimized = false))
    }

    /** Odağı (en üst sırayı) yalnız gerekiyorsa değiştirir; sürüklerken gereksiz liste yazımı olmasın. */
    fun focus(s: DesktopState, id: Int): DesktopState {
        val win = s.byId(id) ?: return s
        return if (s.windows.lastOrNull() == win && !win.minimized) s else raise(s, id)
    }

    fun minimize(s: DesktopState, id: Int): DesktopState = update(s, id) { it.copy(minimized = true) }

    /** Görev çubuğu düğmesi: odaktaysa küçült, değilse geri getirip öne al. */
    fun toggleFromTaskbar(s: DesktopState, id: Int): DesktopState {
        val win = s.byId(id) ?: return s
        return if (s.focused?.id == id && !win.minimized) minimize(s, id) else raise(s, id)
    }

    fun toggleMaximize(s: DesktopState, id: Int): DesktopState {
        val win = s.byId(id) ?: return s
        val next = if (win.mode == WinMode.MAXIMIZED) WinMode.NORMAL else WinMode.MAXIMIZED
        return raise(update(s, id) { it.copy(mode = next) }, id)
    }

    fun setMode(s: DesktopState, id: Int, mode: WinMode): DesktopState = raise(update(s, id) { it.copy(mode = mode) }, id)

    // ------------------------------------------------------------------------------------------------------------
    // Taşıma ve boyutlandırma
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Başlık çubuğundan sürüklemeye başlanırken: pencere kayan değilse (büyük/yaslı) önceki boyutuna döner ve
     * imleç aynı göreli noktada kalacak biçimde yerleşir ([grabFracX]: imlecin pencere genişliğindeki payı, 0..1).
     */
    fun beginMove(s: DesktopState, id: Int, area: DSize, grabFracX: Float): DesktopState {
        val win = s.byId(id) ?: return s
        if (win.mode == WinMode.NORMAL) return focus(s, id)
        val eff = boxOf(win, area)
        val w = min(win.box.w, area.w)
        val h = min(win.box.h, area.h)
        val f = grabFracX.coerceIn(0f, 1f)
        val x = eff.x + f * eff.w - f * w
        val y = eff.y
        return raise(update(s, id) { it.copy(mode = WinMode.NORMAL, box = fit(DBox(x, y, w, h), area)) }, id)
    }

    /** Kayan pencereyi ([dx], [dy]) kadar taşır; başlık çubuğu her zaman alanda tutunulabilir kalır. */
    fun moveBy(s: DesktopState, id: Int, dx: Float, dy: Float, area: DSize): DesktopState = update(s, id) { win ->
        if (win.mode != WinMode.NORMAL) return@update win
        val b = win.box
        val minX = -(b.w - GRAB_W)
        val maxX = area.w - GRAB_W
        val maxY = max(0f, area.h - GRAB_H)
        win.copy(box = b.copy(x = (b.x + dx).coerceIn(min(minX, maxX), maxX), y = (b.y + dy).coerceIn(0f, maxY)))
    }

    /**
     * [edges] ([Edge] bit alanı) tutulurken ([dx], [dy]) kadar sürükleyerek boyutlandırır. Kayan olmayan pencere önce
     * olduğu kutuyla kayana çevrilir. En küçük boyut ve alan sınırları korunur.
     */
    fun resizeBy(s: DesktopState, id: Int, edges: Int, dx: Float, dy: Float, area: DSize, minSize: DSize = MIN_SIZE): DesktopState {
        val win = s.byId(id) ?: return s
        val start = boxOf(win, area)
        val minW = min(minSize.w, area.w)
        val minH = min(minSize.h, area.h)
        var left = start.x
        var top = start.y
        var right = start.right
        var bottom = start.bottom
        if (edges and Edge.LEFT != 0) left = (left + dx).coerceIn(0f, right - minW)
        if (edges and Edge.RIGHT != 0) right = (right + dx).coerceIn(left + minW, area.w)
        if (edges and Edge.TOP != 0) top = (top + dy).coerceIn(0f, bottom - minH)
        if (edges and Edge.BOTTOM != 0) bottom = (bottom + dy).coerceIn(top + minH, area.h)
        val next = DBox(left, top, right - left, bottom - top)
        return update(s, id) { it.copy(mode = WinMode.NORMAL, box = next) }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Kenara yaslama
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Sürükleme sırasında imleç ([px], [py]: alan koordinatı) hangi yaslama bölgesinde? Hiçbirinde değilse null.
     *  üst kenar → tam ekran · sol/sağ kenar → yarım · köşe → çeyrek.
     */
    fun snapZone(px: Float, py: Float, area: DSize, margin: Float = SNAP_MARGIN, corner: Float = SNAP_CORNER): WinMode? {
        val nearLeft = px <= margin
        val nearRight = px >= area.w - margin
        val nearTop = py <= margin
        val nearBottom = py >= area.h - margin
        val inTopBand = py <= corner
        val inBottomBand = py >= area.h - corner
        val inLeftBand = px <= corner
        val inRightBand = px >= area.w - corner
        return when {
            (nearLeft && inTopBand) || (nearTop && inLeftBand) -> WinMode.SNAP_TOP_LEFT
            (nearRight && inTopBand) || (nearTop && inRightBand) -> WinMode.SNAP_TOP_RIGHT
            (nearLeft && inBottomBand) || (nearBottom && inLeftBand) -> WinMode.SNAP_BOTTOM_LEFT
            (nearRight && inBottomBand) || (nearBottom && inRightBand) -> WinMode.SNAP_BOTTOM_RIGHT
            nearTop -> WinMode.MAXIMIZED
            nearLeft -> WinMode.SNAP_LEFT
            nearRight -> WinMode.SNAP_RIGHT
            else -> null
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Düzenler
    // ------------------------------------------------------------------------------------------------------------

    /** Küçültülmemiş tüm pencereleri alana döşer (1: tam, 2: yan yana, 3: solda büyük + sağda iki, 4+: ızgara). */
    fun tile(s: DesktopState, area: DSize): DesktopState {
        val visible = s.windows.filter { !it.minimized }
        val n = visible.size
        if (n == 0) return s
        val g = GAP
        val boxes: List<DBox> = when (n) {
            1 -> listOf(DBox(0f, 0f, area.w, area.h))

            2 -> {
                val w = (area.w - g) / 2f
                listOf(DBox(0f, 0f, w, area.h), DBox(w + g, 0f, w, area.h))
            }

            3 -> {
                val w = (area.w - g) / 2f
                val h = (area.h - g) / 2f
                listOf(DBox(0f, 0f, w, area.h), DBox(w + g, 0f, w, h), DBox(w + g, h + g, w, h))
            }

            else -> {
                val cols = ceil(sqrt(n.toDouble())).toInt()
                val rows = ceil(n / cols.toDouble()).toInt()
                val w = (area.w - g * (cols - 1)) / cols
                val h = (area.h - g * (rows - 1)) / rows
                List(n) { i -> DBox((i % cols) * (w + g), (i / cols) * (h + g), w, h) }
            }
        }
        val byId = visible.mapIndexed { i, win -> win.id to boxes[i] }.toMap()
        return s.copy(
            windows = s.windows.map { win -> byId[win.id]?.let { win.copy(box = it, mode = WinMode.NORMAL) } ?: win },
        )
    }

    /** Küçültülmemiş pencereleri kademeli dizer (düzensiz kalan masaüstünü toparlar). */
    fun cascade(s: DesktopState, area: DSize): DesktopState {
        var i = 0
        return s.copy(
            windows = s.windows.map { win ->
                if (win.minimized) {
                    win
                } else {
                    val step = (i++ % 8) * 30f
                    val w = min(max(area.w * 0.62f, MIN_SIZE.w), area.w)
                    val h = min(max(area.h * 0.74f, MIN_SIZE.h), area.h)
                    win.copy(box = fit(DBox(24f + step, 24f + step, w, h), area), mode = WinMode.NORMAL)
                }
            },
        )
    }

    fun minimizeAll(s: DesktopState): DesktopState = s.copy(windows = s.windows.map { it.copy(minimized = true) })

    private inline fun update(s: DesktopState, id: Int, change: (DesktopWindow) -> DesktopWindow): DesktopState {
        if (s.windows.none { it.id == id }) return s
        return s.copy(windows = s.windows.map { if (it.id == id) change(it) else it })
    }
}

/**
 * Kalıcı metin biçimi (sürüm 1): `v1;next=5;w=<id>,<app>,<x>,<y>,<w>,<h>,<mod>,<küçük 0/1>|...`
 * Uygulama anahtarı virgül içermez (`ai:7`, `files`, `kripton`). Bozuk/bilinmeyen parçalar atlanır; okunamayan metin boş düzen verir.
 */
object DesktopCodec {
    private const val VERSION = "v1"

    fun encode(s: DesktopState): String = buildString {
        append(VERSION)
        append(";next=").append(s.nextId)
        append(";w=")
        append(
            s.windows.joinToString("|") { w ->
                listOf(w.id, w.app, w.box.x, w.box.y, w.box.w, w.box.h, w.mode.key, if (w.minimized) 1 else 0).joinToString(",")
            },
        )
    }

    fun decode(text: String?): DesktopState {
        if (text.isNullOrBlank()) return DesktopState()
        val parts = text.split(';')
        if (parts.firstOrNull() != VERSION) return DesktopState()
        val fields = parts.drop(1).mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        val seen = HashSet<Int>()
        val seenApps = HashSet<String>()
        val windows = fields["w"].orEmpty().split('|').mapNotNull { item ->
            val a = item.split(',')
            if (a.size != 8) return@mapNotNull null
            val id = a[0].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val app = a[1].takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val nums = a.subList(2, 6).map { it.toFloatOrNull()?.takeIf { f -> f.isFinite() } ?: return@mapNotNull null }
            if (nums[2] < 1f || nums[3] < 1f) return@mapNotNull null
            if (!seen.add(id) || !seenApps.add(app)) return@mapNotNull null
            DesktopWindow(id, app, DBox(nums[0], nums[1], nums[2], nums[3]), WinMode.fromKey(a[6]), a[7] == "1")
        }
        val next = max(fields["next"]?.toIntOrNull() ?: 1, (windows.maxOfOrNull { it.id } ?: 0) + 1)
        return DesktopState(windows, next)
    }
}

/** Başlıkta küçük bir saat için: 0-1439 dakikayı "SS:dd" yapar. */
fun formatClock(minuteOfDay: Int): String {
    val m = ((minuteOfDay % 1440) + 1440) % 1440
    return (m / 60).toString().padStart(2, '0') + ":" + (m % 60).toString().padStart(2, '0')
}
