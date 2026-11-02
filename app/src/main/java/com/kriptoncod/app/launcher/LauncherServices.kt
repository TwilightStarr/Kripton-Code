package com.kriptoncod.app.launcher

import android.content.Context
import com.kriptoncod.app.launcher.search.FileIndex
import com.kriptoncod.app.launcher.widgets.WidgetHostController
import com.kriptoncod.app.ui.desktop.DesktopPlatform

/** Launcher'ın uygulama genelindeki paylaşılan servisleri ([com.kriptoncod.app.AppContainer] tembel olarak oluşturur). */
class LauncherServices(context: Context) {
    val apps = AppCatalog(context)
    val widgets = WidgetHostController(context)
    val files = FileIndex { DesktopPlatform.storageRoot() }
}
