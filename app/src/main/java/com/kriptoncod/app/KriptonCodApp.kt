package com.kriptoncod.app

import android.app.Application
import android.content.Context
import com.kriptoncod.app.data.crash.CrashReporting
import com.kriptoncod.app.debug.DebugTools
import com.kriptoncod.app.widget.RunWidgetUpdater
import org.acra.ACRA

class KriptonCodApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Yerel çökme günlüğü (ACRA): olabildiğince erken kurulur. Hiçbir şey ağa gönderilmez.
        CrashReporting.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        // ACRA'nın gönderim servisi ayrı süreçte (:acra) çalışır; orada uygulama kurulumu yapılmaz.
        if (ACRA.isACRASenderServiceProcess()) return
        // Debug: LeakCanary bildirimleri yerine çökme günlüğü + tek bildirim (release'te boş).
        DebugTools.install(this)
        container = AppContainer(this)
        container.startRecovery()
        RunWidgetUpdater.syncWithRuns(this, container.appScope, container.runRepository)
    }
}
