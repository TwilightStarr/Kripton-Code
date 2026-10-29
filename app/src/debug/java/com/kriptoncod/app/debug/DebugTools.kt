package com.kriptoncod.app.debug

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kriptoncod.app.R
import com.kriptoncod.app.data.crash.CrashLogStore
import leakcanary.EventListener
import leakcanary.LeakCanary
import org.json.JSONObject
import shark.HeapAnalysisFailure
import shark.HeapAnalysisSuccess

/**
 * Yalnız debug: LeakCanary'nin kendi bildirimlerini (bulundu / heap dökülüyor / analiz bitti) ve toast'larını kapatır.
 * Yerine sızıntı ya da analiz hatası bulununca:
 *  1. ayrıntı yerel çökme günlüğüne yazılır (Ayarlar > Çökme günlüğü),
 *  2. tek bir kısa bildirim gösterilir (sabit kimlik: yığılmaz).
 * Aynı sızıntı (imza) bir daha bildirilmez/kaydedilmez; böylece bildirim yağmuru ve günlüğün dolması biter.
 * Yalnız UYGULAMA sızıntıları sayılır; sistem/kütüphane sızıntıları kullanıcının düzeltebileceği şey değildir.
 */
object DebugTools {
    fun install(app: Application) {
        val cfg = LeakCanary.config
        val kept = cfg.eventListeners.filterNot { l ->
            val n = l.javaClass.name
            n.contains("Toast") || n.contains("Notification") || n.contains("LazyForwarding") || n.contains("TvEventListener")
        }
        LeakCanary.config = cfg.copy(eventListeners = kept + LeakCrashLogListener(app))
    }
}

private class LeakCrashLogListener(private val app: Application) : EventListener {
    override fun onEvent(event: EventListener.Event) {
        if (event !is EventListener.Event.HeapAnalysisDone<*>) return
        runCatching {
            when (val a = event.heapAnalysis) {
                is HeapAnalysisSuccess -> {
                    val fresh = a.applicationLeaks.filter { markSeen("leak:" + it.signature) }
                    if (fresh.isNotEmpty()) {
                        val body = fresh.joinToString("\n\n") { it.toString() }
                        report("memory-leak", "${fresh.size} yeni bellek sızıntısı: " + fresh.first().shortDescription, body)
                    }
                }

                is HeapAnalysisFailure -> {
                    val msg = a.exception.message.orEmpty().take(200)
                    if (markSeen("fail:$msg")) report("leak-analysis-failed", "Sızıntı analizi başarısız: $msg", a.exception.stackTraceToString())
                }

                else -> Unit
            }
        }
    }

    /** İmza ilk kez görülüyorsa true (ve kaydeder); kalıcıdır, uygulama yeniden açılınca da bir daha bildirilmez. */
    @Synchronized
    private fun markSeen(key: String): Boolean {
        val prefs = app.getSharedPreferences("debug_leak_seen", Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("sigs", emptySet()).orEmpty().toMutableSet()
        if (!seen.add(key)) return false
        // Sınırsız büyümesin.
        val limited = if (seen.size > 300) seen.drop(seen.size - 300).toSet() else seen
        prefs.edit().putStringSet("sigs", limited).apply()
        return true
    }

    private fun report(kind: String, summary: String, details: String) {
        val json = JSONObject()
        json.put("type", "debug-leak")
        json.put("kind", kind)
        json.put("message", summary.take(500))
        json.put("details", details.take(8000))
        json.put("appVersion", runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull().orEmpty())
        CrashLogStore.default(app).write(kind, json.toString())
        notifyOnce()
    }

    @SuppressLint("MissingPermission")
    private fun notifyOnce() {
        val nm = NotificationManagerCompat.from(app)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!nm.areNotificationsEnabled()) return
        val channel = NotificationChannel(CHANNEL, app.getString(R.string.debug_error_channel), NotificationManager.IMPORTANCE_LOW)
        app.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(app, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val text = app.getString(R.string.debug_error_text)
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(app.getString(R.string.debug_error_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    private companion object {
        const val CHANNEL = "kripton-debug-errors"
        const val NOTIFICATION_ID = 7701
    }
}
