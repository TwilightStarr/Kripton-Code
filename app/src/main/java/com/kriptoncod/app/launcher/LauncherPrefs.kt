package com.kriptoncod.app.launcher

/**
 * Launcher ayarları (Ana ekran uzun basış > Launcher ayarları). Hepsi cihazda kalır.
 *
 * Alt çubuk (dock) ve masaüstü animasyonları gibi eski kabuk ayarları [com.kriptoncod.app.data.settings.DesktopPrefs]'te
 * durmaya devam eder; burada yalnız launcher'a özgü olanlar vardır.
 */
data class LauncherPrefs(
    /** Ana ekranda bir satırdaki simge sayısı (telefonda; tablette bir fazlası kullanılır). */
    val homeColumns: Int = 4,
    /** Simge boyutu çarpanı. */
    val iconScale: Float = 1f,
    val showLabels: Boolean = true,
    /** Uygulama çekmecesi sırası: alfabetik, en çok kullanılan, en son kurulan. */
    val drawerSort: DrawerSort = DrawerSort.ALPHA,
    /** Simgelerde okunmamış bildirim sayısı rozeti. */
    val badges: Boolean = true,
    /** Ana ekranda yukarı kaydır: çekmece; aşağı kaydır: bildirim paneli. */
    val gestureSwipeUp: Boolean = true,
    val gestureSwipeDown: Boolean = true,
    /** Kripton'un kendi olay bildirimlerini (çalışma bitti/hata/bekleniyor) sistem bildirim gölgesi yerine yalnız panelde göster. */
    val kriptonNotificationsInPanel: Boolean = true,
    /** Arama kaynakları. */
    val searchFiles: Boolean = true,
    val searchContacts: Boolean = false,
    val searchCalc: Boolean = true,
    val searchWeb: Boolean = true,
    /** Duvar kağıdı: boş = Kripton'un kendi koyu zemini; "system" = sistem duvar kağıdı (izin gerektirebilir); yoksa yerel dosya adı. */
    val wallpaper: String = "",
    /** Duvar kağıdının üstündeki karartma (0-0.8). */
    val wallpaperDim: Float = 0.35f,
    /** Çekmecede gizlenen uygulamalar (paket adları). Aramada da çıkmaz. */
    val hiddenApps: Set<String> = emptySet(),
    /** Uygulama başlatma sayıları (paket -> sayı); "en çok kullanılan" sırası ve öneriler için. */
    val launchCounts: Map<String, Int> = emptyMap(),
) {
    companion object {
        const val COLUMNS_MIN = 3
        const val COLUMNS_MAX = 7
        const val ICON_MIN = 0.7f
        const val ICON_MAX = 1.4f
        const val DIM_MAX = 0.8f

        fun cleanColumns(v: Int): Int = v.coerceIn(COLUMNS_MIN, COLUMNS_MAX)
        fun cleanIcon(v: Float): Float = if (v.isNaN() || v.isInfinite()) 1f else v.coerceIn(ICON_MIN, ICON_MAX)
        fun cleanDim(v: Float): Float = if (v.isNaN() || v.isInfinite()) 0.35f else v.coerceIn(0f, DIM_MAX)

        /** Kayıt biçimi: `paket=sayı,paket=sayı`. Paket adları `=` ve `,` içermez. Bozuk parçalar atlanır. */
        fun decodeCounts(text: String?): Map<String, Int> {
            if (text.isNullOrBlank()) return emptyMap()
            return text.split(',').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) return@mapNotNull null
                val n = it.substring(i + 1).toIntOrNull()?.takeIf { c -> c > 0 } ?: return@mapNotNull null
                it.substring(0, i) to n
            }.toMap()
        }

        /** En çok 200 kayıt tutulur (en çok kullanılanlar kalır). */
        fun encodeCounts(m: Map<String, Int>): String = m.entries.sortedByDescending { it.value }.take(200).joinToString(",") { "${it.key}=${it.value}" }

        fun decodeSet(text: String?): Set<String> = text.orEmpty().split(',').filter { it.isNotBlank() }.toSet()

        fun encodeSet(s: Set<String>): String = s.sorted().joinToString(",")
    }
}

enum class DrawerSort(val id: String) {
    ALPHA("alpha"),
    MOST_USED("used"),
    NEWEST("new"),
    ;

    companion object {
        fun fromId(id: String?): DrawerSort = entries.firstOrNull { it.id == id } ?: ALPHA
    }
}
