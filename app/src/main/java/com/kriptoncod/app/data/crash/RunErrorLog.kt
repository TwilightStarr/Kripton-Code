package com.kriptoncod.app.data.crash

import android.content.Context
import org.json.JSONObject

/**
 * Çalıştırma sırasında yakalanan (uygulamayı düşürmeyen) hataları yerel çökme günlüğüne yazar.
 * Böylece sessizce yeniden denenen ya da düşen düğümlerin sebebi Ayarlar > çökme kayıtlarında görünür.
 * Kayıt yalnız cihazda kalır; prompt metni ve hesap bilgisi yazılmaz.
 */
object RunErrorLog {
    fun record(context: Context, kind: String, runId: Long?, jobId: Long?, title: String?, error: Throwable) {
        runCatching {
            val json = JSONObject()
            json.put("type", "run-error")
            json.put("kind", kind)
            if (runId != null) json.put("runId", runId)
            if (jobId != null) json.put("jobId", jobId)
            if (title != null) json.put("title", title)
            json.put("error", error::class.java.name)
            json.put("message", error.message.orEmpty().take(500))
            json.put("stack", error.stackTraceToString().take(6000))
            CrashLogStore.default(context).write("run-$kind", json.toString())
        }
    }
}
