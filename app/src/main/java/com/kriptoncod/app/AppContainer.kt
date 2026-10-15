package com.kriptoncod.app

import android.content.Context
import com.kriptoncod.app.data.settings.ThemeStore

/** Elle basit DI: tekil bağımlılıklar burada tembel (lazy) oluşturulur. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val themeStore: ThemeStore by lazy { ThemeStore(appContext) }
}
