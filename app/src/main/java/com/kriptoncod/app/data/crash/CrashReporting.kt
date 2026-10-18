package com.kriptoncod.app.data.crash

import android.app.Application
import com.kriptoncod.app.BuildConfig
import org.acra.ReportField
import org.acra.data.StringFormat
import org.acra.ktx.initAcra

/**
 * ACRA ile yerel çökme günlüğü. Gizlilik tasarımı:
 *  - Gönderici yalnız [LocalCrashSender] (diske yazar); ağ göndericisi yoktur.
 *  - Yalnız aşağıdaki alanlar toplanır: yığın izi, sürüm, cihaz modeli, Android sürümü, bellek.
 *    Logcat, ayarlar, paylaşılan tercihler, ortam değişkenleri, cihaz/yükleme kimliği, e-posta TOPLANMAZ.
 *    Böylece prompt, anahtar, token ve çıktılar kayda girmez.
 *
 * [Application.attachBaseContext] içinde, `super.attachBaseContext`'ten sonra çağrılmalıdır.
 */
object CrashReporting {
    internal val FIELDS = listOf(
        ReportField.REPORT_ID,
        ReportField.APP_VERSION_CODE,
        ReportField.APP_VERSION_NAME,
        ReportField.PACKAGE_NAME,
        ReportField.ANDROID_VERSION,
        ReportField.BRAND,
        ReportField.PHONE_MODEL,
        ReportField.PRODUCT,
        ReportField.TOTAL_MEM_SIZE,
        ReportField.AVAILABLE_MEM_SIZE,
        ReportField.USER_CRASH_DATE,
        ReportField.IS_SILENT,
        ReportField.STACK_TRACE,
        ReportField.STACK_TRACE_HASH,
    )

    fun install(app: Application) {
        app.initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON
            reportContent = FIELDS
        }
    }
}
