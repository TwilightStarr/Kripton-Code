package com.kriptoncod.app.ui.workspace

// Çalışma alanının (aynı anda birden çok AI web arayüzü) yerleşim mantığı. Hiçbir Android/Compose türüne bağlı değildir;
// bu yüzden birim testle doğrulanır. Ekran bu dosyadan çıkan dikdörtgenleri animasyonla uygular.

/** Tablette bir sayfada en fazla bu kadar AI görünür; fazlası sonraki sayfaya geçer. */
const val PANES_PER_PAGE = 4

/** Her bölmenin (yaklaşık) en küçük payı: bir pencere sürüklenerek yok edilemez. */
const val MIN_FRACTION = 0.22f

/** Odaklanan (dokunulan / yazılan) pencerenin ağırlık çarpanı. */
const val FOCUS_BOOST = 1.35f

/** Klavye açıkken yazılan pencerenin çarpanı: yazılan yer ekranın büyük kısmını alır. */
const val TYPING_BOOST = 2.4f

/** Pencereler arası boşluk (dp). */
const val GAP_DP = 8

/** "Biri büyük, diğerleri yan şeritte" düzeninde büyük pencerenin şeride göre ana eksen oranı. */
const val SIDEBAR_RATIO = 2.2f

/** Tek dokunuşluk hazır düzenler. */
enum class LayoutPreset(val key: String) {
    /** Sayfadaki pencereler eşit bölünür (3 → solda büyük/sağda iki, 4 → 2x2 varsayılan yapısı). */
    EQUAL("equal"),

    /** Sayfadaki ilk pencere büyük; kalanlar yanında tek şeritte alt alta dizilir. */
    SIDEBAR("sidebar"),
    ;

    companion object {
        fun fromKey(key: String?): LayoutPreset = entries.firstOrNull { it.key == key } ?: EQUAL
    }
}

fun pageCount(total: Int, perPage: Int = PANES_PER_PAGE): Int = if (total <= 0) 1 else (total + perPage - 1) / perPage

/** [page] (0 tabanlı) sayfadaki öğeler; sayfa sınırın dışındaysa en yakın geçerli sayfa kullanılır. */
fun <T> pageItems(items: List<T>, page: Int, perPage: Int = PANES_PER_PAGE): List<T> {
    val p = page.coerceIn(0, pageCount(items.size, perPage) - 1)
    return items.drop(p * perPage).take(perPage)
}

/**
 * Bir pencerenin kullanıcı tarafından ayarlanan göreli boyutu.
 * [main]: ana eksendeki (yatay ekranda genişlik) pay; [cross]: bölmesi içinde diğer eksendeki (yükseklik) pay.
 */
data class PaneWeight(val main: Float = 1f, val cross: Float = 1f)

/** Piksel cinsinden dikdörtgen. */
data class PaneBox(val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * [count] AI için bölme yapısı: iç listeler birer "sütun"dur (dikeyde üst üste dizilen pencereler).
 *  1 → tek büyük · 2 → yan yana · 3 → solda büyük, sağda iki küçük · 4 → 2x2.
 * Dikey (portre) ekranda eksenler yer değiştirir; yapı aynı kalır.
 */
fun slotGroups(count: Int, sidebar: Boolean = false): List<List<Int>> = when (count.coerceIn(0, PANES_PER_PAGE)) {
    0 -> emptyList()

    1 -> listOf(listOf(0))

    2 -> listOf(listOf(0), listOf(1))

    3 -> listOf(listOf(0), listOf(1, 2))

    // Yan şerit: ilk pencere tek başına, kalan hepsi ikinci sütunda alt alta.
    else -> if (sidebar) listOf(listOf(0), listOf(1, 2, 3)) else listOf(listOf(0, 1), listOf(2, 3))
}

/** Grupların ana eksendeki doğal çarpanı: yan şerit düzeninde ilk grup [SIDEBAR_RATIO] kat geniştir. */
private fun groupBias(groupCount: Int, sidebar: Boolean): List<Float> = List(groupCount) { if (sidebar && groupCount > 1 && it == 0) SIDEBAR_RATIO else 1f }

/**
 * Sayfadaki pencerelerin dikdörtgenlerini hesaplar.
 *
 * @param weights kullanıcının ayarladığı boyutlar (pencere sırasıyla); eksikse 1.
 * @param focus odaklanan pencerenin sırası; null ise hiçbiri büyütülmez.
 * @param focusBoost odaklanan pencerenin çarpanı (1 = büyütme yok).
 * @param gap pencereler arası boşluk (piksel).
 * @param sidebar true: ilk pencere büyük, kalanlar yan şeritte ([LayoutPreset.SIDEBAR]).
 */
fun layoutPane(
    count: Int,
    width: Float,
    height: Float,
    weights: List<PaneWeight> = emptyList(),
    focus: Int? = null,
    focusBoost: Float = 1f,
    gap: Float = 0f,
    sidebar: Boolean = false,
): List<PaneBox> {
    val groups = slotGroups(count, sidebar)
    if (groups.isEmpty() || width <= 0f || height <= 0f) return emptyList()
    val horizontal = width >= height
    val mainSize = if (horizontal) width else height
    val crossSize = if (horizontal) height else width

    fun w(i: Int) = weights.getOrNull(i) ?: PaneWeight()
    fun boost(i: Int) = if (focus == i) focusBoost.coerceAtLeast(1f) else 1f

    // Grupların ana eksendeki payı: grubun ilk penceresinin ağırlığı; odaklı pencere grubunu büyütür.
    val bias = groupBias(groups.size, sidebar)
    val groupWeights = groups.mapIndexed { gi, g -> w(g.first()).main.coerceAtLeast(0.01f) * bias[gi] * (g.maxOf { boost(it) }) }
    val mainAvail = (mainSize - gap * (groups.size - 1)).coerceAtLeast(1f)
    val mainTotal = groupWeights.sum()

    val out = arrayOfNulls<PaneBox>(count.coerceIn(0, PANES_PER_PAGE))
    var mainPos = 0f
    groups.forEachIndexed { gi, g ->
        val mainLen = mainAvail * groupWeights[gi] / mainTotal
        val crossAvail = (crossSize - gap * (g.size - 1)).coerceAtLeast(1f)
        val crossWeights = g.map { w(it).cross.coerceAtLeast(0.01f) * boost(it) }
        val crossTotal = crossWeights.sum()
        var crossPos = 0f
        g.forEachIndexed { pi, idx ->
            val crossLen = crossAvail * crossWeights[pi] / crossTotal
            out[idx] = if (horizontal) PaneBox(mainPos, crossPos, mainLen, crossLen) else PaneBox(crossPos, mainPos, crossLen, mainLen)
            crossPos += crossLen + gap
        }
        mainPos += mainLen + gap
    }
    return out.map { it ?: PaneBox(0f, 0f, 0f, 0f) }
}

/**
 * Sağ alt köşeden sürükleme: [index] numaralı pencere [dMain]/[dCross] (kendi eksenindeki toplamın kesri; pozitif = büyür)
 * kadar büyür, komşusu aynı miktarda küçülür. Hiçbir bölme [MIN_FRACTION]'ın altına inmez; toplam korunur.
 * Dönen liste, [weights] ile aynı sıradadır.
 */
fun resizePane(
    count: Int,
    weights: List<PaneWeight>,
    index: Int,
    dMain: Float,
    dCross: Float,
    sidebar: Boolean = false,
): List<PaneWeight> {
    val groups = slotGroups(count, sidebar)
    val bias = groupBias(groups.size, sidebar)
    val n = count.coerceIn(0, PANES_PER_PAGE)
    if (index !in 0 until n) return weights
    val base = List(n) { weights.getOrNull(it) ?: PaneWeight() }.toMutableList()

    // Ana eksen: gruplar arası pay.
    val gi = groups.indexOfFirst { index in it }
    if (groups.size > 1 && gi >= 0) {
        // Doğal çarpan (yan şerit) hesaba katılır; saklanan ağırlık çarpandan arındırılmış olur.
        val fr = normalized(groups.mapIndexed { i, g -> base[g.first()].main * bias[i] })
        val nb = if (gi < groups.lastIndex) gi + 1 else gi - 1
        val applied = dMain.coerceIn(MIN_FRACTION - fr[gi], fr[nb] - MIN_FRACTION)
        fr[gi] += applied
        fr[nb] -= applied
        groups.forEachIndexed { i, g -> g.forEach { base[it] = base[it].copy(main = fr[i] / bias[i]) } }
    }

    // Çapraz eksen: grup içindeki pencereler arası pay.
    val g = groups.getOrNull(gi)
    if (g != null && g.size > 1) {
        val pos = g.indexOf(index)
        val fr = normalized(g.map { base[it].cross })
        val nb = if (pos < g.lastIndex) pos + 1 else pos - 1
        val applied = dCross.coerceIn(MIN_FRACTION - fr[pos], fr[nb] - MIN_FRACTION)
        fr[pos] += applied
        fr[nb] -= applied
        g.forEachIndexed { i, idx -> base[idx] = base[idx].copy(cross = fr[i]) }
    }
    return base
}

private fun normalized(values: List<Float>): MutableList<Float> {
    val safe = values.map { it.coerceAtLeast(0.01f) }
    val total = safe.sum()
    return safe.map { it / total }.toMutableList()
}

/** Bir sayfa sayısı değişince (pencere kapatılınca) geçerli sayfayı sınırlar içinde tutar. */
fun clampPage(page: Int, total: Int, perPage: Int = PANES_PER_PAGE): Int = page.coerceIn(0, pageCount(total, perPage) - 1)

// ---------------------------------------------------------------------------------------------------------------
// Sabitleme (pin): bir pencere her sayfada görünür; diğerleri kalan yerlerde sayfalanır.
// ---------------------------------------------------------------------------------------------------------------

/** Geçerli sabit pencere: açık listedeyse ve sayfada birden çok pencere sığıyorsa; yoksa null (telefonda sabitleme yok). */
fun <T> effectivePin(open: List<T>, pinned: T?, perPage: Int): T? = pinned?.takeIf { perPage > 1 && it in open }

/** Sabit pencere dahil toplam sayfa sayısı. */
fun <T> pagesWithPin(open: List<T>, pinned: T?, perPage: Int = PANES_PER_PAGE): Int {
    if (effectivePin(open, pinned, perPage) == null) return pageCount(open.size, perPage)
    return pageCount(open.size - 1, perPage - 1)
}

/** [page] sayfasında görünenler: sabit pencere (varsa) her zaman ilk sırada, kalan yerler diğerleriyle sayfalanır. */
fun <T> pagedItems(open: List<T>, pinned: T?, page: Int, perPage: Int = PANES_PER_PAGE): List<T> {
    val p = effectivePin(open, pinned, perPage) ?: return pageItems(open, page, perPage)
    return listOf(p) + pageItems(open.filter { it != p }, page, perPage - 1)
}

/** [id]'nin görüneceği sayfa; sabit pencere her sayfada olduğundan null döner (listede yoksa da null). */
fun <T> pageOfItem(open: List<T>, pinned: T?, id: T, perPage: Int = PANES_PER_PAGE): Int? {
    if (id !in open) return null
    val p = effectivePin(open, pinned, perPage)
    if (p == id) return null
    val rest = if (p == null) open else open.filter { it != p }
    val per = if (p == null) perPage else perPage - 1
    return rest.indexOf(id) / per
}

// ---------------------------------------------------------------------------------------------------------------
// Sürükleyerek yer değiştirme.
// ---------------------------------------------------------------------------------------------------------------

/** (x, y) noktasını içeren pencerenin sırası; hiçbirinin içinde değilse null. */
fun paneAt(boxes: List<PaneBox>, x: Float, y: Float): Int? = boxes.indexOfFirst { x >= it.x && x < it.x + it.w && y >= it.y && y < it.y + it.h }.takeIf { it >= 0 }

/** [list] içinde [a] ve [b]'nin yerini değiştirir; ikisi de yoksa ya da aynıysa liste aynen döner. */
fun <T> swapItems(list: List<T>, a: T, b: T): List<T> {
    val ia = list.indexOf(a)
    val ib = list.indexOf(b)
    if (ia < 0 || ib < 0 || ia == ib) return list
    return list.toMutableList().also {
        it[ia] = b
        it[ib] = a
    }
}
