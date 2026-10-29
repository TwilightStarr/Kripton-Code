package com.kriptoncod.app.ui.workspace

import com.kriptoncod.app.data.web.WebPhase

/**
 * Kaydedilen çalışma alanı düzeni: hangi AI'lar açık, pencere boyutları, sabit pencere, hazır düzen.
 * Uygulama açılınca aynı çalışma alanı gelsin diye DataStore'a tek bir metin olarak yazılır.
 * Hiçbir Android/Compose türüne bağlı değildir; birim testle doğrulanır.
 */
data class WorkspaceSnapshot(
    val open: List<Long> = emptyList(),
    val weights: Map<Long, PaneWeight> = emptyMap(),
    val pinned: Long? = null,
    val preset: LayoutPreset = LayoutPreset.EQUAL,
    val growOnFocus: Boolean = true,
) {
    /** Yalnız [valid] hesap kimliklerini tutar (silinmiş hesapların penceresi geri gelmesin). Sabit pencere açık kalmadıysa düşer. */
    fun retaining(valid: Set<Long>): WorkspaceSnapshot {
        val kept = open.filter { it in valid }.distinct()
        return copy(
            open = kept,
            weights = weights.filterKeys { it in kept },
            pinned = pinned?.takeIf { it in kept },
        )
    }
}

/**
 * Metin biçimi (sürüm 1), alanlar `;` ile ayrılır:
 *   `v1;preset=sidebar;grow=1;pin=7;open=3,7,9;w=3:1.0:1.0,7:0.5:2.0`
 * Bozuk/bilinmeyen alanlar yok sayılır; tamamen okunamayan metin boş düzen verir (çökme yok).
 */
object WorkspaceCodec {
    private const val VERSION = "v1"

    fun encode(s: WorkspaceSnapshot): String = buildString {
        append(VERSION)
        append(";preset=").append(s.preset.key)
        append(";grow=").append(if (s.growOnFocus) 1 else 0)
        s.pinned?.let { append(";pin=").append(it) }
        append(";open=").append(s.open.joinToString(","))
        append(";w=").append(
            s.weights.entries.joinToString(",") { (id, w) -> "$id:${w.main}:${w.cross}" },
        )
    }

    fun decode(text: String?): WorkspaceSnapshot {
        if (text.isNullOrBlank()) return WorkspaceSnapshot()
        val parts = text.split(';')
        if (parts.firstOrNull() != VERSION) return WorkspaceSnapshot()
        val fields = parts.drop(1).mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()

        val open = fields["open"].orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.distinct()
        val weights = fields["w"].orEmpty().split(',').mapNotNull { item ->
            val a = item.split(':')
            if (a.size != 3) return@mapNotNull null
            val id = a[0].toLongOrNull() ?: return@mapNotNull null
            val main = a[1].toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: return@mapNotNull null
            val cross = a[2].toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: return@mapNotNull null
            id to PaneWeight(main, cross)
        }.toMap()
        return WorkspaceSnapshot(
            open = open,
            weights = weights.filterKeys { it in open },
            pinned = fields["pin"]?.toLongOrNull()?.takeIf { it in open },
            preset = LayoutPreset.fromKey(fields["preset"]),
            growOnFocus = fields["grow"] != "0",
        )
    }
}

/**
 * Bildirim rozeti: bir AI'ın yanıtı bitince (SENDING/GENERATING → REPLIED/PROBLEM) o AI'ın kimliği döner.
 * [prev] ve [now] hesap kimliği → o hesabın canlı oturumunun aşamasıdır.
 */
fun finishedAccounts(prev: Map<Long, WebPhase>, now: Map<Long, WebPhase>): Set<Long> =
    now.filter { (id, phase) ->
        val before = prev[id]
        (before == WebPhase.SENDING || before == WebPhase.GENERATING) &&
            (phase == WebPhase.REPLIED || phase == WebPhase.PROBLEM)
    }.keys

/**
 * Rozetli sayfalar: görünmeyen sayfalardaki, yanıtı bitmiş ve henüz görülmemiş ([unseen]) AI'ların sayfaları.
 * Sabit pencere her sayfada göründüğü için hiçbir sayfayı rozetlemez.
 */
fun badgedPages(open: List<Long>, pinned: Long?, unseen: Set<Long>, perPage: Int): Set<Int> =
    unseen.mapNotNull { pageOfItem(open, pinned, it, perPage) }.toSet()
