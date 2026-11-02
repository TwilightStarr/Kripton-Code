package com.kriptoncod.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kriptoncod.app.domain.ThemePreference
import com.kriptoncod.app.launcher.DrawerSort
import com.kriptoncod.app.launcher.LauncherPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Sistem/Açık/Koyu seçimi + CI deneme sayısı. DataStore yazmaları atomiktir. */
class ThemeStore(private val context: Context) {

    val preference: Flow<ThemePreference> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { ThemePreference.fromKey(it[THEME_KEY]) }

    suspend fun set(value: ThemePreference) {
        context.settingsDataStore.edit {
            it[SCHEMA_KEY] = SCHEMA_VERSION
            it[THEME_KEY] = value.key
        }
    }

    // ---- CI deneme sayısı (1-10, varsayılan 3) ----
    val ciMaxAttemptsFlow: Flow<Int> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[CI_MAX_ATTEMPTS] ?: DEFAULT_CI_ATTEMPTS }

    suspend fun setCiMaxAttempts(value: Int) {
        context.settingsDataStore.edit { it[CI_MAX_ATTEMPTS] = value.coerceIn(1, 10) }
    }

    // ---- Bildirimler (varsayılan açık) ----
    val notifyFlow: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[NOTIFY] ?: true }

    suspend fun setNotify(value: Boolean) {
        context.settingsDataStore.edit { it[NOTIFY] = value }
    }

    // ---- Animasyonlar (varsayılan açık) ----
    val animationsFlow: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[ANIMATIONS] ?: true }

    suspend fun setAnimations(value: Boolean) {
        context.settingsDataStore.edit { it[ANIMATIONS] = value }
    }

    // ---- Web hesabı "hesap kapatılabilir" uyarısı (bir kez onaylanır) ----
    val webWarningAccepted: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[WEB_WARNING_ACCEPTED] ?: false }

    suspend fun setWebWarningAccepted(value: Boolean) {
        context.settingsDataStore.edit { it[WEB_WARNING_ACCEPTED] = value }
    }

    // ---- Çalışma alanı düzeni (açık AI'lar, boyutlar, sabit pencere); metin biçimi WorkspaceCodec'te ----
    val workspaceLayoutFlow: Flow<String?> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[WORKSPACE_LAYOUT] }

    suspend fun setWorkspaceLayout(value: String) {
        context.settingsDataStore.edit { it[WORKSPACE_LAYOUT] = value }
    }

    // ---- Masaüstü modu (tablet): bir kez açılırsa uygulama hep bu modla açılır; ayarlardan/görev çubuğundan kapatılır ----
    val desktopModeFlow: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[DESKTOP_MODE] ?: false }

    suspend fun setDesktopMode(value: Boolean) {
        context.settingsDataStore.edit { it[DESKTOP_MODE] = value }
    }

    /** Masaüstü modunda sistem çubukları gizlenir (tam ekran); varsayılan açık. */
    val desktopFullscreenFlow: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[DESKTOP_FULLSCREEN] ?: true }

    suspend fun setDesktopFullscreen(value: Boolean) {
        context.settingsDataStore.edit { it[DESKTOP_FULLSCREEN] = value }
    }

    /** Masaüstü pencere düzeni (açık pencereler, konum/boyut); metin biçimi DesktopCodec'te. */
    val desktopLayoutFlow: Flow<String?> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[DESKTOP_LAYOUT] }

    suspend fun setDesktopLayout(value: String) {
        context.settingsDataStore.edit { it[DESKTOP_LAYOUT] = value }
    }

    /** Masaüstü modunun izin açıklaması bir kez gösterilir. */
    val desktopPermissionAsked: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[DESKTOP_PERM_ASKED] ?: false }

    suspend fun setDesktopPermissionAsked(value: Boolean) {
        context.settingsDataStore.edit { it[DESKTOP_PERM_ASKED] = value }
    }

    /** Dosyalar penceresinde Shizuku ile sistem dosyalarına erişim (varsayılan KAPALI; açılmadıkça Shizuku'ya bağlanılmaz). */
    val shizukuFilesFlow: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[SHIZUKU_FILES] ?: false }

    suspend fun setShizukuFiles(value: Boolean) {
        context.settingsDataStore.edit { it[SHIZUKU_FILES] = value }
    }

    /** Tüm masaüstü ayarları tek akışta. [DESKTOP_FULLSCREEN] eski anahtarla aynıdır. */
    val desktopPrefsFlow: Flow<DesktopPrefs> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { readDesktop(it) }

    suspend fun editDesktopPrefs(change: (DesktopPrefs) -> DesktopPrefs) {
        context.settingsDataStore.edit {
            val n = change(readDesktop(it))
            it[DESKTOP_FULLSCREEN] = n.fullscreen
            it[DESKTOP_FS_DOCK] = n.fullscreenHidesDock
            it[DESKTOP_DOCK_AUTOHIDE] = n.dockAutoHide
            it[DESKTOP_DOCK_LABELS] = n.dockLabels
            it[DESKTOP_CLOCK] = n.showClock
            it[DESKTOP_HINTS] = n.showHints
            it[DESKTOP_FILES_HIDDEN] = n.filesHidden
            it[DESKTOP_FILES_EXT] = n.filesExt
            it[DESKTOP_FILES_GRID] = n.filesGrid
            it[DESKTOP_WINDOW_ANIM] = n.windowAnimations
            it[DESKTOP_MOTION_SPEED] = DesktopPrefs.cleanSpeed(n.motionSpeed)
            it[DESKTOP_SYSTEM_SCALE] = n.followSystemAnimatorScale
        }
    }

    private fun readDesktop(p: Preferences): DesktopPrefs {
        val d = DesktopPrefs()
        return DesktopPrefs(
            fullscreen = p[DESKTOP_FULLSCREEN] ?: d.fullscreen,
            fullscreenHidesDock = p[DESKTOP_FS_DOCK] ?: d.fullscreenHidesDock,
            dockAutoHide = p[DESKTOP_DOCK_AUTOHIDE] ?: d.dockAutoHide,
            dockLabels = p[DESKTOP_DOCK_LABELS] ?: d.dockLabels,
            showClock = p[DESKTOP_CLOCK] ?: d.showClock,
            showHints = p[DESKTOP_HINTS] ?: d.showHints,
            filesHidden = p[DESKTOP_FILES_HIDDEN] ?: d.filesHidden,
            filesExt = p[DESKTOP_FILES_EXT] ?: d.filesExt,
            filesGrid = p[DESKTOP_FILES_GRID] ?: d.filesGrid,
            windowAnimations = p[DESKTOP_WINDOW_ANIM] ?: d.windowAnimations,
            motionSpeed = DesktopPrefs.cleanSpeed(p[DESKTOP_MOTION_SPEED] ?: d.motionSpeed),
            followSystemAnimatorScale = p[DESKTOP_SYSTEM_SCALE] ?: d.followSystemAnimatorScale,
        )
    }

    // ---- Launcher (ana ekran, çekmece, arama, bildirim paneli) ----

    val launcherPrefsFlow: Flow<LauncherPrefs> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { readLauncher(it) }

    suspend fun editLauncherPrefs(change: (LauncherPrefs) -> LauncherPrefs) {
        context.settingsDataStore.edit {
            val n = change(readLauncher(it))
            it[LAUNCHER_COLUMNS] = LauncherPrefs.cleanColumns(n.homeColumns)
            it[LAUNCHER_ICON] = LauncherPrefs.cleanIcon(n.iconScale)
            it[LAUNCHER_LABELS] = n.showLabels
            it[LAUNCHER_SORT] = n.drawerSort.id
            it[LAUNCHER_BADGES] = n.badges
            it[LAUNCHER_SWIPE_UP] = n.gestureSwipeUp
            it[LAUNCHER_SWIPE_DOWN] = n.gestureSwipeDown
            it[LAUNCHER_KRIPTON_PANEL] = n.kriptonNotificationsInPanel
            it[LAUNCHER_SEARCH_FILES] = n.searchFiles
            it[LAUNCHER_SEARCH_CONTACTS] = n.searchContacts
            it[LAUNCHER_SEARCH_CALC] = n.searchCalc
            it[LAUNCHER_SEARCH_WEB] = n.searchWeb
            it[LAUNCHER_WALLPAPER] = n.wallpaper
            it[LAUNCHER_WALL_DIM] = LauncherPrefs.cleanDim(n.wallpaperDim)
            it[LAUNCHER_HIDDEN] = LauncherPrefs.encodeSet(n.hiddenApps)
            it[LAUNCHER_COUNTS] = LauncherPrefs.encodeCounts(n.launchCounts)
        }
    }

    /** Uygulama başlatıldı: "en çok kullanılan" sırası ve öneriler için sayaç artar (yalnız cihazda). */
    suspend fun recordLaunch(packageName: String) {
        editLauncherPrefs { it.copy(launchCounts = it.launchCounts + (packageName to ((it.launchCounts[packageName] ?: 0) + 1))) }
    }

    /** Ana ekran düzeni (metin biçimi: launcher/home/HomeCodec). */
    val homeLayoutFlow: Flow<String?> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[LAUNCHER_HOME] }

    suspend fun setHomeLayout(value: String) {
        context.settingsDataStore.edit { it[LAUNCHER_HOME] = value }
    }

    private fun readLauncher(p: Preferences): LauncherPrefs {
        val d = LauncherPrefs()
        return LauncherPrefs(
            homeColumns = LauncherPrefs.cleanColumns(p[LAUNCHER_COLUMNS] ?: d.homeColumns),
            iconScale = LauncherPrefs.cleanIcon(p[LAUNCHER_ICON] ?: d.iconScale),
            showLabels = p[LAUNCHER_LABELS] ?: d.showLabels,
            drawerSort = DrawerSort.fromId(p[LAUNCHER_SORT]),
            badges = p[LAUNCHER_BADGES] ?: d.badges,
            gestureSwipeUp = p[LAUNCHER_SWIPE_UP] ?: d.gestureSwipeUp,
            gestureSwipeDown = p[LAUNCHER_SWIPE_DOWN] ?: d.gestureSwipeDown,
            kriptonNotificationsInPanel = p[LAUNCHER_KRIPTON_PANEL] ?: d.kriptonNotificationsInPanel,
            searchFiles = p[LAUNCHER_SEARCH_FILES] ?: d.searchFiles,
            searchContacts = p[LAUNCHER_SEARCH_CONTACTS] ?: d.searchContacts,
            searchCalc = p[LAUNCHER_SEARCH_CALC] ?: d.searchCalc,
            searchWeb = p[LAUNCHER_SEARCH_WEB] ?: d.searchWeb,
            wallpaper = p[LAUNCHER_WALLPAPER] ?: d.wallpaper,
            wallpaperDim = LauncherPrefs.cleanDim(p[LAUNCHER_WALL_DIM] ?: d.wallpaperDim),
            hiddenApps = LauncherPrefs.decodeSet(p[LAUNCHER_HIDDEN]),
            launchCounts = LauncherPrefs.decodeCounts(p[LAUNCHER_COUNTS]),
        )
    }

    private companion object {
        val LAUNCHER_COLUMNS = intPreferencesKey("launcher_columns")
        val LAUNCHER_ICON = floatPreferencesKey("launcher_icon_scale")
        val LAUNCHER_LABELS = booleanPreferencesKey("launcher_labels")
        val LAUNCHER_SORT = stringPreferencesKey("launcher_sort")
        val LAUNCHER_BADGES = booleanPreferencesKey("launcher_badges")
        val LAUNCHER_SWIPE_UP = booleanPreferencesKey("launcher_swipe_up")
        val LAUNCHER_SWIPE_DOWN = booleanPreferencesKey("launcher_swipe_down")
        val LAUNCHER_KRIPTON_PANEL = booleanPreferencesKey("launcher_kripton_panel")
        val LAUNCHER_SEARCH_FILES = booleanPreferencesKey("launcher_search_files")
        val LAUNCHER_SEARCH_CONTACTS = booleanPreferencesKey("launcher_search_contacts")
        val LAUNCHER_SEARCH_CALC = booleanPreferencesKey("launcher_search_calc")
        val LAUNCHER_SEARCH_WEB = booleanPreferencesKey("launcher_search_web")
        val LAUNCHER_WALLPAPER = stringPreferencesKey("launcher_wallpaper")
        val LAUNCHER_WALL_DIM = floatPreferencesKey("launcher_wallpaper_dim")
        val LAUNCHER_HIDDEN = stringPreferencesKey("launcher_hidden_apps")
        val LAUNCHER_COUNTS = stringPreferencesKey("launcher_launch_counts")
        val LAUNCHER_HOME = stringPreferencesKey("launcher_home")
        val DESKTOP_FS_DOCK = booleanPreferencesKey("desktop_fs_hides_dock")
        val DESKTOP_DOCK_AUTOHIDE = booleanPreferencesKey("desktop_dock_autohide")
        val DESKTOP_DOCK_LABELS = booleanPreferencesKey("desktop_dock_labels")
        val DESKTOP_CLOCK = booleanPreferencesKey("desktop_clock")
        val DESKTOP_HINTS = booleanPreferencesKey("desktop_hints")
        val DESKTOP_FILES_HIDDEN = booleanPreferencesKey("desktop_files_hidden")
        val DESKTOP_FILES_EXT = booleanPreferencesKey("desktop_files_ext")
        val DESKTOP_FILES_GRID = booleanPreferencesKey("desktop_files_grid")
        val DESKTOP_WINDOW_ANIM = booleanPreferencesKey("desktop_window_anim")
        val DESKTOP_MOTION_SPEED = floatPreferencesKey("desktop_motion_speed")
        val DESKTOP_SYSTEM_SCALE = booleanPreferencesKey("desktop_system_anim_scale")
        val SHIZUKU_FILES = booleanPreferencesKey("shizuku_files")
        val DESKTOP_MODE = booleanPreferencesKey("desktop_mode")
        val DESKTOP_FULLSCREEN = booleanPreferencesKey("desktop_fullscreen")
        val DESKTOP_LAYOUT = stringPreferencesKey("desktop_layout")
        val DESKTOP_PERM_ASKED = booleanPreferencesKey("desktop_perm_asked")
        val WORKSPACE_LAYOUT = stringPreferencesKey("workspace_layout")
        val WEB_WARNING_ACCEPTED = booleanPreferencesKey("web_warning_accepted")
        val ANIMATIONS = booleanPreferencesKey("animations")
        val NOTIFY = booleanPreferencesKey("notify_events")
        const val SCHEMA_VERSION = 1
        const val DEFAULT_CI_ATTEMPTS = 3
        val SCHEMA_KEY = intPreferencesKey("schema")
        val THEME_KEY = stringPreferencesKey("theme")
        val CI_MAX_ATTEMPTS = intPreferencesKey("ci_max_attempts")
    }
}
