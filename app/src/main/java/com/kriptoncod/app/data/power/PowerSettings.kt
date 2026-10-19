package com.kriptoncod.app.data.power

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** Üreticiye özel güç/otomatik başlatma ekranları olan cihaz aileleri. */
enum class Oem { XIAOMI, HUAWEI, OPPO, VIVO, SAMSUNG, ONEPLUS, ASUS, OTHER }

/**
 * Pil optimizasyonu: arka planda uzun süren üretimlerin sistem tarafından durdurulmaması için.
 * Android'in standart "pil kısıtlaması yok" izni + (Xiaomi/Huawei/Oppo/Vivo/Samsung gibi cihazlarda)
 * üreticinin kendi "otomatik başlat / arka planda çalış" ekranları. Hiçbir ayar kullanıcı adına değiştirilmez;
 * yalnız ilgili sistem ekranı açılır.
 */
object PowerSettings {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** Doğrudan "bu uygulamayı kısıtlama" iletişim kutusu (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS izni gerekir). */
    fun requestIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    /** Tüm uygulamaların pil optimizasyonu listesi. */
    fun listIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** Uygulama ayrıntıları (pil/otomatik başlatma için son çare). */
    fun detailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun detectOem(manufacturer: String?, brand: String? = null): Oem {
        val id = "${manufacturer.orEmpty()} ${brand.orEmpty()}".lowercase()
        return when {
            "xiaomi" in id || "redmi" in id || "poco" in id -> Oem.XIAOMI
            "huawei" in id || "honor" in id -> Oem.HUAWEI
            "oppo" in id || "realme" in id -> Oem.OPPO
            "vivo" in id || "iqoo" in id -> Oem.VIVO
            "samsung" in id -> Oem.SAMSUNG
            "oneplus" in id -> Oem.ONEPLUS
            "asus" in id -> Oem.ASUS
            else -> Oem.OTHER
        }
    }

    /** Üreticiye özel ekranlar, en olası olandan başlayarak. Sürümden sürüme değişir; hepsi denenir. */
    fun oemComponents(oem: Oem): List<ComponentName> = when (oem) {
        Oem.XIAOMI -> listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
        )
        Oem.HUAWEI -> listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )
        Oem.OPPO -> listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        )
        Oem.VIVO -> listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        )
        Oem.SAMSUNG -> listOf(
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
        )
        Oem.ONEPLUS -> listOf(
            ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        )
        Oem.ASUS -> listOf(
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
        )
        Oem.OTHER -> emptyList()
    }

    /** Üreticiye özel ekranları açar; hiçbiri yoksa uygulama ayrıntılarına düşer. @return açılan ekran var mı. */
    fun openOemSettings(context: Context, oem: Oem): Boolean {
        val candidates = oemComponents(oem).map { Intent().setComponent(it) } + detailsIntent(context)
        return openFirst(context, candidates)
    }

    /** Sırayla dener; ilk açılan ekranda durur. Etkinlik bulunamazsa/izin reddedilirse sıradakine geçer. */
    fun openFirst(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            val launched = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (launched) return true
        }
        return false
    }
}
