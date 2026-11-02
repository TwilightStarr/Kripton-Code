package com.kriptoncod.app.launcher.notify

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Launcher ile arka plan kodu (bildirim üreten WorkManager işleri) arasındaki küçük, bellek içi köprü.
 */
object LauncherRuntime {
    /** Launcher ayarındaki "Kripton bildirimlerini yalnız panelde göster" anahtarının son değeri (kabuk günceller). */
    @Volatile var kriptonEventsInPanel: Boolean = true

    /** Kripton şu an cihazın ana ekran uygulaması mı? */
    fun isDefaultHome(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == context.packageName
    }

    /**
     * Kripton olay bildirimi Android bildirim gölgesinde GÖSTERİLMESİN mi? Yalnızca üç koşul birlikteyse: kullanıcı ayarı açık,
     * Kripton ana ekran uygulaması ve bildirim erişimi verilmiş (panel gerçekten çalışıyor). Aksi hâlde olay her zamanki gibi
     * sistem bildirimi olur; böylece hiçbir olay sessizce kaybolmaz.
     */
    fun suppressSystemEvents(context: Context): Boolean = kriptonEventsInPanel && NotificationHub.connected.value && isDefaultHome(context)
}
