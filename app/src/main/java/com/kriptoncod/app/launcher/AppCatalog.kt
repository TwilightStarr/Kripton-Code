package com.kriptoncod.app.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.runtime.Immutable
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cihazda kurulu, başlatılabilir bir uygulama. */
@Immutable
data class LauncherApp(
    val label: String,
    val packageName: String,
    val installedAt: Long,
) {
    val letter: Char
        get() = label.trim().firstOrNull()?.let { c ->
            val u = c.toString().uppercase(Locale("tr")).first()
            if (u.isLetter()) u else '#'
        } ?: '#'
}

/** Uygulamanın uzun basış kısayolu (ör. "Yeni sohbet"). */
@Immutable
data class AppShortcut(val id: String, val packageName: String, val label: String)

/**
 * Kurulu uygulamaların canlı listesi. [LauncherApps] kullanılır: kurulum/kaldırma/güncelleme anında liste kendiliğinden
 * yenilenir (eski sürümde yalnız çekmece açılınca yenileniyordu). Hiçbir veri cihazdan çıkmaz.
 */
class AppCatalog(context: Context) {
    private val app = context.applicationContext
    private val launcherApps = app.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val me = Process.myUserHandle()

    private val _apps = MutableStateFlow<List<LauncherApp>?>(null)

    /** null: ilk yükleme sürüyor. Kendimiz hariç, ada göre sıralı. */
    val apps: StateFlow<List<LauncherApp>?> = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    @Volatile private var started = false

    /** Dinlemeyi başlatır ve ilk listeyi yükler (ağır iştir; arka planda çağır). */
    fun start() {
        if (started) return
        started = true
        runCatching { launcherApps.registerCallback(callback) }
        reload()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { launcherApps.unregisterCallback(callback) }
    }

    fun reload() {
        val list = runCatching {
            val pm = app.packageManager
            launcherApps.getActivityList(null, me)
                .filter { it.applicationInfo.packageName != app.packageName }
                .distinctBy { it.applicationInfo.packageName }
                .map {
                    val pkg = it.applicationInfo.packageName
                    val installed = runCatching { pm.getPackageInfo(pkg, 0).firstInstallTime }.getOrDefault(0L)
                    LauncherApp(it.label.toString(), pkg, installed)
                }
                .sortedBy { it.label.lowercase(Locale.ROOT) }
        }.getOrDefault(emptyList())
        _apps.value = list
    }

    /** Uygulamanın kısayolları (en çok [limit]). Yalnız Kripton ana ekran uygulamasıysa Android izin verir; yoksa boş. */
    fun shortcuts(packageName: String, limit: Int = 4): List<AppShortcut> {
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return emptyList()
        return runCatching {
            val q = LauncherApps.ShortcutQuery()
                .setPackage(packageName)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
            launcherApps.getShortcuts(q, me).orEmpty()
                .filter { it.isEnabled }
                .take(limit)
                .map { AppShortcut(it.id, it.`package`, (it.shortLabel ?: it.longLabel ?: it.id).toString()) }
        }.getOrDefault(emptyList())
    }

    fun startShortcut(s: AppShortcut): Boolean = runCatching {
        launcherApps.startShortcut(s.packageName, s.id, null, null, me)
        true
    }.getOrDefault(false)

    companion object {
        /** Uygulama ayrıntıları ekranı. */
        fun appInfoIntent(packageName: String): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Kaldırma onayı (sistemin kendi penceresi). */
        fun uninstallIntent(packageName: String): Intent =
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun isSystemApp(context: Context, packageName: String): Boolean = runCatching {
            val flags = context.packageManager.getApplicationInfo(packageName, 0).flags
            (flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        }.getOrDefault(false)
    }
}
