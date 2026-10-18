package com.kriptoncod.app.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.kriptoncod.app.MainActivity
import com.kriptoncod.app.R

/**
 * Arka plan çalışması bildirimleri.
 *  - Süren çalışma: kalıcı ilerleme bildirimi (Android, arka planda uzun süren iş için bunu zorunlu kılar);
 *    **Duraklat** ve **İptal** düğmeleri taşır, uygulamayı açmadan çalışmayı denetler.
 *  - Duraklatılmış çalışma: **Devam et** ve **İptal** düğmeli bildirim.
 *  - Olay bildirimleri (tamamlandı, hata, kota/hesap bekleniyor): kullanıcı Ayarlar'dan açıksa gönderilir.
 */
object RunNotifier {
    private const val CHANNEL_RUN = "krypton_run"
    private const val CHANNEL_EVENTS = "krypton_events"
    private const val ONGOING_ID = 4711
    private const val PAUSED_ID_BASE = 6_000_000

    private fun manager(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = manager(ctx)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RUN, ctx.getString(R.string.notif_channel_run), NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_EVENTS, ctx.getString(R.string.notif_channel_events), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun openApp(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * Denetim düğmesinin PendingIntent'i. PendingIntent kimliği ekstralara bakmaz; bu yüzden run ve eylem
     * `data` URI'sine yazılır (farklı run'ların düğmeleri birbirinin yerine geçmesin).
     */
    internal fun actionIntent(ctx: Context, action: RunControl.Action, runId: Long): PendingIntent {
        val intent = Intent(ctx, RunActionReceiver::class.java)
            .setAction(RunActionReceiver.ACTION)
            .setData(Uri.parse("kriptoncod://run/$runId/${action.name.lowercase()}"))
            .putExtra(RunActionReceiver.EXTRA_RUN_ID, runId)
            .putExtra(RunActionReceiver.EXTRA_ACTION, action.name)
        return PendingIntent.getBroadcast(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun NotificationCompat.Builder.addControl(
        ctx: Context,
        action: RunControl.Action,
        runId: Long,
        labelRes: Int,
    ): NotificationCompat.Builder = addAction(0, ctx.getString(labelRes), actionIntent(ctx, action, runId))

    /**
     * Süren çalışma için ön plan bilgisi; [total] 0 ise belirsiz ilerleme çubuğu gösterilir.
     * [runId] verilirse bildirime Duraklat ve İptal düğmeleri eklenir.
     */
    fun foregroundInfo(ctx: Context, text: String, done: Int = 0, total: Int = 0, runId: Long? = null): ForegroundInfo {
        ensureChannels(ctx)
        val builder = NotificationCompat.Builder(ctx, CHANNEL_RUN)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openApp(ctx))
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (runId != null) {
            builder.addControl(ctx, RunControl.Action.PAUSE, runId, R.string.notif_action_pause)
                .addControl(ctx, RunControl.Action.CANCEL, runId, R.string.notif_action_cancel)
        }
        val n: Notification = builder.build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(ONGOING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(ONGOING_ID, n)
        }
    }

    /** Duraklatılmış run: Devam et / İptal düğmeli, kapatılabilir bildirim. İzin yoksa sessizce atlanır. */
    fun paused(ctx: Context, runId: Long) {
        val nm = manager(ctx)
        if (!nm.areNotificationsEnabled()) return
        ensureChannels(ctx)
        val text = ctx.getString(R.string.notif_paused_text, runId)
        val n = NotificationCompat.Builder(ctx, CHANNEL_EVENTS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(ctx.getString(R.string.notif_paused_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .setOnlyAlertOnce(true)
            .addControl(ctx, RunControl.Action.RESUME, runId, R.string.notif_action_resume)
            .addControl(ctx, RunControl.Action.CANCEL, runId, R.string.notif_action_cancel)
            .build()
        runCatching { nm.notify(pausedId(runId), n) }
    }

    /** Devam/iptal sonrası duraklatma bildirimini kaldırır. */
    fun clearPaused(ctx: Context, runId: Long) {
        runCatching { manager(ctx).cancel(pausedId(runId)) }
    }

    private fun pausedId(runId: Long) = PAUSED_ID_BASE + (runId % 100_000L).toInt()

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
