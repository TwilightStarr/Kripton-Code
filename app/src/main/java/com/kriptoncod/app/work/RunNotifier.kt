package com.kriptoncod.app.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.kriptoncod.app.MainActivity

/**
 * Arka plan çalışması bildirimleri.
 *  - Süren çalışma: kalıcı ilerleme bildirimi (Android, arka planda uzun süren iş için bunu zorunlu kılar).
 *  - Olay bildirimleri (tamamlandı, hata, kota/hesap bekleniyor): kullanıcı Ayarlar'dan açıksa gönderilir.
 */
object RunNotifier {
    private const val CHANNEL_RUN = "krypton_run"
    private const val CHANNEL_EVENTS = "krypton_events"
    private const val ONGOING_ID = 4711

    private fun manager(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = manager(ctx)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_RUN, "Çalışma ilerlemesi", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_EVENTS, "Bildirimler", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun openApp(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Süren çalışma için ön plan bilgisi; [total] 0 ise belirsiz ilerleme çubuğu gösterilir. */
    fun foregroundInfo(ctx: Context, text: String, done: Int = 0, total: Int = 0): ForegroundInfo {
        ensureChannels(ctx)
        val n: Notification = NotificationCompat.Builder(ctx, CHANNEL_RUN)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Krypton Code")
            .setContentText(text)
            .setContentIntent(openApp(ctx))
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(ONGOING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(ONGOING_ID, n)
        }
    }

    /** Olay bildirimi; izin yoksa veya bildirimler kapalıysa sessizce atlanır. Aynı [runId]+tür tek bildirimi günceller. */
    fun event(ctx: Context, runId: Long, kind: Int, title: String, text: String) {
        val nm = manager(ctx)
        if (!nm.areNotificationsEnabled()) return
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL_EVENTS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { nm.notify(((runId % 100_000L).toInt() * 10) + 5_000_000 + kind, n) }
    }

    const val KIND_DONE = 1
    const val KIND_FAILED = 2
    const val KIND_WAITING = 3
}
