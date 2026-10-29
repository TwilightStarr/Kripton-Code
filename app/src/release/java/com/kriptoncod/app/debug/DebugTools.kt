package com.kriptoncod.app.debug

import android.app.Application

/** Release'te hata ayıklama araçları yoktur (LeakCanary yalnız debug). Gerçek sürüm: `src/debug`. */
object DebugTools {
    fun install(app: Application) = Unit
}
