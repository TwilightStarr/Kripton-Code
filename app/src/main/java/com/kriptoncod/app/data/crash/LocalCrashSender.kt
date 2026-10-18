package com.kriptoncod.app.data.crash

import android.content.Context
import org.acra.ReportField
import org.acra.config.CoreConfiguration
import org.acra.data.CrashReportData
import org.acra.sender.ReportSender
import org.acra.sender.ReportSenderFactory

/**
 * ACRA raporunu AĞA GÖNDERMEZ; yalnız cihazdaki [CrashLogStore]'a yazar. Başka bir gönderici (http, mail) eklenmediği
 * için kullanıcı verisi cihazdan çıkmaz. Kullanıcı isterse son kaydı Ayarlar'dan kendisi paylaşır.
 */
class LocalCrashSender(private val store: CrashLogStore) : ReportSender {
    override fun send(context: Context, errorContent: CrashReportData) {
        store.write(errorContent.getString(ReportField.REPORT_ID).orEmpty(), errorContent.toJSON())
    }
}

/**
 * ACRA bu sınıfı ServiceLoader ile bulur: `META-INF/services/org.acra.sender.ReportSenderFactory`.
 * Genel (public) ve parametresiz kurucu zorunludur.
 */
class LocalCrashSenderFactory : ReportSenderFactory {
    override fun create(context: Context, config: CoreConfiguration): ReportSender =
        LocalCrashSender(CrashLogStore.default(context))
}
