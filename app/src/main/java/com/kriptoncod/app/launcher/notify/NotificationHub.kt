package com.kriptoncod.app.launcher.notify

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sistem bildirimlerinin launcher içindeki tek kaynağı. [KriptonNotificationListener] bağlanınca doldurulur.
 * Kullanıcı "Bildirim erişimi"ni vermediyse [connected] false kalır ve panel, izin vermek için bir kart gösterir;
 * hiçbir bildirim izinsiz okunmaz. Veriler yalnız bellektedir, hiçbir yere gönderilmez ve diske yazılmaz.
 */
object NotificationHub {
    private val _items = MutableStateFlow<List<LNotification>>(emptyList())
    val items: StateFlow<List<LNotification>> = _items.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val live = ConcurrentHashMap<String, StatusBarNotification>()
    private val labels = ConcurrentHashMap<String, String>()

    @Volatile private var service: NotificationListenerService? = null

    // ---- Erişim izni ----

    fun hasAccess(context: Context): Boolean = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Android'in "Bildirim erişimi" ayar ekranı (bu uygulamaya özel ekran varsa onu açar). */
    fun accessIntent(context: Context): Intent {
        val specific = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, KriptonNotificationListener::class.java).flattenToString(),
            )
        val resolvable = specific.resolveActivity(context.packageManager) != null
        return if (resolvable) specific else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    // ---- Servisten gelenler ----

    internal fun attach(s: NotificationListenerService) {
        service = s
        _connected.value = true
        refresh(s)
    }

    internal fun detach() {
        service = null
        _connected.value = false
        live.clear()
        _items.value = emptyList()
    }

    internal fun refresh(s: NotificationListenerService) {
        val active = runCatching { s.activeNotifications }.getOrNull().orEmpty()
        live.clear()
        active.forEach { live[it.key] = it }
        publish(s)
    }

    internal fun onPosted(s: NotificationListenerService, sbn: StatusBarNotification) {
        live[sbn.key] = sbn
        publish(s)
    }

    internal fun onRemoved(s: NotificationListenerService, key: String) {
        live.remove(key)
        publish(s)
    }

    private fun publish(ctx: Context) {
        _items.value = live.values.mapNotNull { map(ctx, it) }.sortedByDescending { it.postTime }
    }

    private fun map(ctx: Context, sbn: StatusBarNotification): LNotification? {
        val n = sbn.notification ?: return null
        // Sessiz/gizli sistem bildirimleri (ör. "USB hata ayıklama") yine gösterilir; yalnız tamamen boş olanlar süzülür.
        val ex = n.extras ?: Bundle()
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val max = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val prog = ex.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val percent = if (max > 0) ((prog * 100L) / max).toInt().coerceIn(0, 100) else -1
        val actions = n.actions.orEmpty().mapIndexed { i, a ->
            LAction(i, a.title?.toString().orEmpty(), a.remoteInputs?.isNotEmpty() == true)
        }.filter { it.title.isNotBlank() }
        return LNotification(
            key = sbn.key,
            pkg = sbn.packageName,
            appLabel = labelOf(ctx, sbn.packageName),
            title = title,
            text = text,
            bigText = if (big.isNotBlank() && big != text) big else "",
            postTime = sbn.postTime,
            ongoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0,
            clearable = sbn.isClearable,
            isSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            progress = percent,
            indeterminate = indeterminate,
            groupKey = n.group,
            actions = actions,
            hasContentIntent = n.contentIntent != null,
            autoCancel = (n.flags and Notification.FLAG_AUTO_CANCEL) != 0,
        )
    }

    private fun labelOf(ctx: Context, pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    // ---- Kullanıcı eylemleri ----

    /** Bildirime dokunma: uygulamasını açar; "dokununca kaybolsun" bayrağı varsa bildirimi de kaldırır. */
    fun open(context: Context, key: String): Boolean {
        val sbn = live[key] ?: return false
        val pi = sbn.notification?.contentIntent ?: return false
        val ok = runCatching { pi.send() }.isSuccess
        if (ok && (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL) != 0) dismiss(key)
        return ok
    }

    fun dismiss(key: String) {
        runCatching { service?.cancelNotification(key) }
    }

    /** Temizlenebilir bütün bildirimleri kaldırır (sürekli olanlar kalır). */
    fun clearAll() {
        runCatching { service?.cancelAllNotifications() }
    }

    fun clearPackage(pkg: String) {
        live.values.filter { it.packageName == pkg && it.isClearable }.forEach { dismiss(it.key) }
    }

    /** Bildirim düğmesini çalıştırır. [reply] doluysa satır içi yanıt olarak gönderilir. */
    fun runAction(context: Context, key: String, index: Int, reply: String?): Boolean {
        val sbn = live[key] ?: return false
        val action = sbn.notification?.actions?.getOrNull(index) ?: return false
        val pi = action.actionIntent ?: return false
        return runCatching {
            if (reply != null && !action.remoteInputs.isNullOrEmpty()) {
                val intent = Intent()
                val results = Bundle()
                action.remoteInputs.forEach { results.putCharSequence(it.resultKey, reply) }
                RemoteInput.addResultsToIntent(action.remoteInputs, intent, results)
                pi.send(context, 0, intent)
            } else {
                pi.send()
            }
        }.isSuccess
    }
}
