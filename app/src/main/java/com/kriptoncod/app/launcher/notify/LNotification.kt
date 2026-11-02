package com.kriptoncod.app.launcher.notify

import androidx.compose.runtime.Immutable

/** Bir bildirim eylemi düğmesi (ör. "Yanıtla", "Arşivle"). [acceptsReply]: satır içi yanıt kutusu açar. */
@Immutable
data class LAction(val index: Int, val title: String, val acceptsReply: Boolean)

/**
 * Launcher'ın kendi bildirim modeli: sistem bildiriminin görünümü değil, yalnız gerekli alanlar. PendingIntent'ler burada
 * tutulmaz ([NotificationHub] anahtarla saklar); böylece model saftır ve birim testle doğrulanır.
 */
@Immutable
data class LNotification(
    val key: String,
    val pkg: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val bigText: String,
    val postTime: Long,
    val ongoing: Boolean,
    val clearable: Boolean,
    val isSummary: Boolean,
    /** -1: ilerleme yok; 0..100: yüzde. */
    val progress: Int = -1,
    val indeterminate: Boolean = false,
    val groupKey: String? = null,
    val actions: List<LAction> = emptyList(),
    val hasContentIntent: Boolean = false,
    val autoCancel: Boolean = false,
)

/** Panelde uygulama başına gruplanmış bildirimler. */
@Immutable
data class NotifGroup(val pkg: String, val label: String, val items: List<LNotification>) {
    val latest: Long get() = items.maxOfOrNull { it.postTime } ?: 0L
    val count: Int get() = items.size
}

/** Bildirim listesi üzerinde saf işlemler. */
object Notifs {
    /**
     * Panelde gösterilecek bildirimler: boş olanlar ve (aynı grupta çocuğu olan) özet bildirimler çıkar; sürekli (ongoing)
     * olanlar ayrı döner ([split]).
     */
    fun visible(all: List<LNotification>): List<LNotification> {
        val withChildren = all.filter { !it.isSummary && it.groupKey != null }.map { it.pkg + "|" + it.groupKey }.toSet()
        return all.filter { n ->
            val empty = n.title.isBlank() && n.text.isBlank() && n.bigText.isBlank()
            !empty && !(n.isSummary && (n.pkg + "|" + n.groupKey) in withChildren)
        }
    }

    /** (olaylar, sürenler): sürenler (müzik, indirme, çalışan Kripton işi…) ayrı bölümde gösterilir. */
    fun split(visible: List<LNotification>): Pair<List<LNotification>, List<LNotification>> {
        val (ongoing, events) = visible.partition { it.ongoing && !it.clearable }
        return events to ongoing
    }

    /** Uygulama başına grupla; en yeni bildirimi olan grup üstte, grup içinde en yeni üstte. */
    fun group(events: List<LNotification>): List<NotifGroup> =
        events.groupBy { it.pkg }
            .map { (pkg, list) -> NotifGroup(pkg, list.first().appLabel, list.sortedByDescending { it.postTime }) }
            .sortedByDescending { it.latest }

    /** Simge rozetleri: paket başına temizlenebilir (okunmamış sayılan) bildirim sayısı; sürekli ve özet bildirimler sayılmaz. */
    fun badgeCounts(all: List<LNotification>): Map<String, Int> =
        visible(all).filter { !it.ongoing && !it.isSummary }.groupingBy { it.pkg }.eachCount()

    /** "az önce", "5 dk", "2 sa", "3 gün" (dil bağımsız kısa biçim). */
    fun ago(now: Long, then: Long, justNow: String): String {
        val s = ((now - then) / 1000L).coerceAtLeast(0L)
        return when {
            s < 60 -> justNow
            s < 3600 -> "${s / 60} dk"
            s < 86_400 -> "${s / 3600} sa"
            else -> "${s / 86_400} g"
        }
    }
}
