package com.kriptoncod.app.launcher.notify

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Bildirim erişimi servisi. Yalnız kullanıcı Android ayarlarından "Bildirim erişimi"ni açarsa çalışır.
 * Bildirimler [NotificationHub]'a bellekte aktarılır; uygulama bildirimleri ağa göndermez ve diske yazmaz.
 */
class KriptonNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationHub.attach(this)
    }

    override fun onListenerDisconnected() {
        NotificationHub.detach()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationHub.onPosted(this, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationHub.onRemoved(this, sbn.key)
    }
}
