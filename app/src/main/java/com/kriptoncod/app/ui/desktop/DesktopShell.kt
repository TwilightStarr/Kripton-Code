package com.kriptoncod.app.ui.desktop

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.view.DragEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.view.DragAndDropPermissionsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.R
import com.kriptoncod.app.data.settings.DesktopPrefs
import com.kriptoncod.app.data.shizuku.ShizukuFiles
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.web.WebChatHub
import com.kriptoncod.app.domain.ThemePreference
import com.kriptoncod.app.ui.adaptive.windowClassFor
import com.kriptoncod.app.ui.navigation.AppRoot
import com.kriptoncod.app.ui.screens.ChatPage
import com.kriptoncod.app.ui.screens.Notice
import com.kriptoncod.app.ui.screens.WebFileChooserEffect
import com.kriptoncod.app.ui.screens.tint
import com.kriptoncod.app.ui.screens.webProvider
import com.kriptoncod.app.launcher.AppCatalog
import com.kriptoncod.app.launcher.LauncherPrefs
import com.kriptoncod.app.launcher.LauncherBus
import com.kriptoncod.app.launcher.Wallpaper
import com.kriptoncod.app.launcher.home.Home
import com.kriptoncod.app.launcher.home.HomeCodec
import com.kriptoncod.app.launcher.home.HomeItem
import com.kriptoncod.app.launcher.home.HomeState
import com.kriptoncod.app.launcher.notify.KriptonInbox
import com.kriptoncod.app.launcher.notify.LauncherRuntime
import com.kriptoncod.app.launcher.notify.NotificationHub
import com.kriptoncod.app.launcher.notify.Notifs
import com.kriptoncod.app.launcher.notify.QuickToggles
import com.kriptoncod.app.launcher.search.SearchEngine
import com.kriptoncod.app.launcher.ui.AppDrawer
import com.kriptoncod.app.launcher.ui.AskSheet
import com.kriptoncod.app.launcher.ui.DrawerActions
import com.kriptoncod.app.launcher.ui.HomeActions
import com.kriptoncod.app.launcher.ui.LauncherHome
import com.kriptoncod.app.launcher.ui.LauncherSettings
import com.kriptoncod.app.launcher.ui.LocalInAppMessage
import com.kriptoncod.app.launcher.ui.NotificationPanel
import com.kriptoncod.app.launcher.ui.WidgetPicker
import com.kriptoncod.app.launcher.widgets.rememberWidgetAdder
import com.kriptoncod.app.ui.theme.KriptonTheme
import com.kriptoncod.app.ui.theme.LocalAnimationsEnabled
import com.kriptoncod.app.ui.theme.LocalMotionScale
import com.kriptoncod.app.ui.theme.MotionScale
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Masaüstü modu: Linux başlatıcısı (launcher) düzeni.
 *
 *  - Alt çubuk (dock): sol baştaki düğme uygulama başlatıcıyı açar; yanında sabitlenmiş uygulamalar ve açık sekmeler durur.
 *    İstediğin uygulamayı başlatıcıdaki raptiyeyle (ya da uzun basışla) çubuğa ekler/çıkarırsın.
 *  - Uygulamalar SEKME gibi açılır: aynı anda tek sekme tüm alanı kaplar; diğerleri arkada açık kalır (AI oturumları ve
 *    sohbetler kaybolmaz). Sekme yokken boş masaüstü (saat + sabit uygulamalar) görünür.
 *  - AI web arayüzleri, "Dosyalar" ve "Kripton Code" (uygulamanın bütün ekranları) birer sekmedir.
 *  - Cihazdaki diğer Android uygulamaları tam ekran açılır ve çubukta açık sekme olarak izlenir (Android bir uygulamayı
 *    başka uygulamanın içine gömmeye izin vermez); çubuktan dokununca yeniden öne gelir.
 *  - Dosyalar sekmesinde bir dosyayı basılı tutup alt çubuktaki açık AI sekmesine sürükle ya da satır menüsüyle gönder.
 *  - Sekmeler ve sabit uygulamalar kaydedilir; mod bir kez açılınca uygulama hep bununla başlar ([DesktopModeHost]).
 */
@Composable
fun DesktopShell(
    container: AppContainer,
    themePreference: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    /** Ana ekran (launcher) olarak açıldı: geri tuşu uygulamadan çıkarmaz. */
    isHome: Boolean = false,
) {
    // Pencere/sekme hareketlerinin ölçeği: genel "Animasyonlar" anahtarı (sistem "animasyonları kaldır" dahil) VE masaüstünün
    // "Pencere animasyonları" anahtarı açıksa oynar; süreler "geçiş hızı" ve (isteğe bağlı) sistem Animatör ölçeğiyle ayarlanır.
    val prefs by container.themeStore.desktopPrefsFlow.collectAsStateWithLifecycle(initialValue = DesktopPrefs())
    val animationsOn = LocalAnimationsEnabled.current && prefs.windowAnimations
    val systemScale = rememberSystemAnimatorScale(prefs.followSystemAnimatorScale)
    val motion = remember(animationsOn, prefs.motionSpeed, systemScale) { MotionScale(animationsOn, prefs.motionSpeed, systemScale) }
    CompositionLocalProvider(LocalMotionScale provides motion) {
        DesktopShellContent(container, themePreference, onThemeChange, isHome)
    }
}

@OptIn(ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
private fun DesktopShellContent(
    container: AppContainer,
    themePreference: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    isHome: Boolean,
) {
    val context = LocalContext.current
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val store = container.themeStore
    val hub = container.webChatHub
    val desktopMode = LocalDesktopMode.current

    val prefs by store.desktopPrefsFlow.collectAsStateWithLifecycle(initialValue = DesktopPrefs())
    val fullscreen = prefs.fullscreen
    val shizukuFiles by store.shizukuFilesFlow.collectAsStateWithLifecycle(initialValue = false)
    DesktopImmersiveEffect(fullscreen)
    // Dosya seçici bütün sekmeler için TEK kez kurulur; içerideki ekranlar kendi seçicisini kurmaz.
    WebFileChooserEffect()

    val accountsState: List<AiAccount>? by container.aiAccountRepository.observeAll().collectAsStateWithLifecycle(initialValue = null)
    val aiRows = remember(accountsState) { accountsState.orEmpty().filter { it.webProvider() != null }.sortedBy { it.displayName.lowercase() } }
    val rowsById = remember(aiRows) { aiRows.associateBy { it.id } }

    var state by remember { mutableStateOf(DesktopState()) }
    var areaRoot by remember { mutableStateOf(Offset.Zero) }
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    // Kapanış animasyonu oynayan sekmeler: durumdan hemen çıkar, içerik animasyon bitene dek bileşimde kalır.
    var closing by remember { mutableStateOf<List<DesktopTab>>(emptyList()) }
    // Açılışta geri yüklenen sekmeler "ikondan büyüme" animasyonu oynamaz; yalnız sonradan açılanlar oynar.
    val restoredIds = remember { HashSet<Int>() }
    var restored by remember { mutableStateOf(false) }
    var launcher by remember { mutableStateOf(false) }
    var showPerm by remember { mutableStateOf(false) }
    var toastText by remember { mutableStateOf<String?>(null) }
    var minuteOfDay by remember { mutableIntStateOf(0) }
    var dateText by remember { mutableStateOf("") }
    val launcherApps by container.launcher.apps.apps.collectAsStateWithLifecycle()
    val installed: List<InstalledApp>? = launcherApps?.map { InstalledApp(it.label, it.packageName) }
    val launcherPrefs by store.launcherPrefsFlow.collectAsStateWithLifecycle(initialValue = LauncherPrefs())
    var homeState by remember { mutableStateOf(HomeState()) }
    var homeLoaded by remember { mutableStateOf(false) }
    var drawerFocus by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var widgetPicker by remember { mutableStateOf(false) }
    var askOpen by remember { mutableStateOf(false) }
    var askTarget by remember { mutableStateOf<Long?>(null) }
    var askText by remember { mutableStateOf("") }
    val sysNotifs by NotificationHub.items.collectAsStateWithLifecycle()
    val inbox by KriptonInbox.items.collectAsStateWithLifecycle()
    val sharedText by LauncherBus.sharedText.collectAsStateWithLifecycle()
    val fileDrag = remember { FileDrag() }
    // Alt çubuk: tam ekranda (ayara bağlı) ya da "otomatik gizle" açıkken gizlenir; alt kenardaki tutamaçla geçici açılır.
    var dockPeek by remember { mutableStateOf(false) }
    var dockPeekTick by remember { mutableIntStateOf(0) }
    val opened = remember { mutableSetOf<Long>() }
    var dropPermissions by remember { mutableStateOf<DragAndDropPermissionsCompat?>(null) }
    val filesAccess = rememberFilesAccess()
    val imeVisible = WindowInsets.isImeVisible
    val dockHidden = (prefs.fullscreen && prefs.fullscreenHidesDock) || prefs.dockAutoHide
    val dockVisible = !dockHidden || dockPeek || launcher || fileDrag.file != null
    val dockPad = if (imeVisible || !dockVisible) 0.dp else DOCK_HEIGHT + 2.dp
    LaunchedEffect(dockPeek, dockPeekTick, launcher) {
        if (dockPeek && !launcher) {
            kotlinx.coroutines.delay(5_000)
            dockPeek = false
        }
    }
    val dockBounds = remember { HashMap<String, Rect>() }
    val filesLabel = stringResource(R.string.desktop_app_files)
    val kriptonLabel = stringResource(R.string.desktop_app_kripton)

    // ---- Kayıt / geri yükleme ----
    LaunchedEffect(Unit) {
        val saved = DesktopCodec.decode(store.desktopLayoutFlow.first())
        state = saved ?: Desktop.open(DesktopState(pinned = DesktopApps.DEFAULT_PINNED), DesktopApps.KRIPTON)
        restoredIds += state.tabs.map { it.id }
        restored = true
        if (!store.desktopPermissionAsked.first()) {
            if (FilesAccess.isGranted(context)) store.setDesktopPermissionAsked(true) else showPerm = true
        }
    }
    LaunchedEffect(restored) {
        if (!restored) return@LaunchedEffect
        snapshotFlow { DesktopCodec.encode(state) }.distinctUntilChanged().drop(1).debounce(500).collect { store.setDesktopLayout(it) }
    }
    // Cihazdaki uygulamalar: açılışta ve başlatıcı her açıldığında yenilenir (yeni kurulanlar görünsün).
    // Uygulama listesi canlıdır (kurulum/kaldırma anında kendiliğinden güncellenir); widget sunucusu kabukla birlikte çalışır.
    LaunchedEffect(Unit) { withContext(Dispatchers.Default) { container.launcher.apps.start() } }
    DisposableEffect(Unit) {
        container.launcher.widgets.start()
        QuickToggles.watchTorch(context)
        onDispose { container.launcher.widgets.stop() }
    }
    LaunchedEffect(launcherPrefs.kriptonNotificationsInPanel) { LauncherRuntime.kriptonEventsInPanel = launcherPrefs.kriptonNotificationsInPanel }
    // Ana ekran düzeni: yükle / kaydet.
    LaunchedEffect(Unit) {
        homeState = HomeCodec.decode(store.homeLayoutFlow.first()) ?: Home.initial(listOf(DesktopApps.KRIPTON, DesktopApps.FILES))
        homeLoaded = true
    }
    LaunchedEffect(homeLoaded) {
        if (!homeLoaded) return@LaunchedEffect
        snapshotFlow { HomeCodec.encode(homeState) }.distinctUntilChanged().drop(1).debounce(500).collect { store.setHomeLayout(it) }
    }
    // Kaldırılan uygulamalar ve silinen AI hesapları ana ekrandan temizlenir (listeler yüklenmeden hiçbir şey silinmez).
    LaunchedEffect(launcherApps, accountsState, homeLoaded) {
        val apps = launcherApps ?: return@LaunchedEffect
        if (accountsState == null || !homeLoaded) return@LaunchedEffect
        val pkgs = apps.map { it.packageName }.toSet()
        homeState = Home.retainApps(homeState) { app ->
            DesktopApps.packageOf(app)?.let { it in pkgs } ?: DesktopApps.aiAccountId(app)?.let { it in rowsById } ?: true
        }
    }
    // Başka uygulamadan "Paylaş" ile gelen metin: AI'a sor sayfası hazır metinle açılır.
    LaunchedEffect(sharedText) {
        val t = sharedText ?: return@LaunchedEffect
        askText = t
        askTarget = null
        askOpen = true
        LauncherBus.consume()
    }
    // Saat ve tarih
    LaunchedEffect(Unit) {
        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
        while (true) {
            val t = LocalTime.now()
            minuteOfDay = t.hour * 60 + t.minute
            dateText = LocalDate.now().format(dateFormat)
            delay(15_000)
        }
    }
    // Silinen hesapların sekmelerini ve sabitlerini kaldır (liste yüklenmeden önce hiçbir şey silinmez).
    LaunchedEffect(accountsState, restored) {
        if (accountsState == null || !restored) return@LaunchedEffect
        state.tabs.mapNotNull { DesktopApps.aiAccountId(it.app) }.filter { it !in rowsById }.forEach { id ->
            opened.remove(id)
            hub.release(id)
        }
        state = Desktop.retain(state) { app -> DesktopApps.aiAccountId(app)?.let { it in rowsById } ?: true }
    }
    // Açık AI sekmelerinin web oturumunu aç (zaten açıksa hub bir şey yapmaz).
    val aiIds = state.tabs.mapNotNull { DesktopApps.aiAccountId(it.app) }
    LaunchedEffect(aiIds, accountsState) {
        aiIds.forEach { id ->
            if (opened.add(id)) {
                val acc = container.aiAccountRepository.get(id)
                if (acc != null) hub.open(acc) else opened.remove(id)
            }
        }
    }
    // Moddan çıkınca bu kabuğun açtığı bütün oturumlar kapanır (çerezler kaydedilir).
    DisposableEffect(Unit) { onDispose { opened.toList().forEach { hub.release(it) } } }
    LaunchedEffect(toastText) {
        if (toastText != null) {
            delay(3500)
            toastText = null
        }
    }

    // ---- Uygulama / sekme eylemleri ----
    fun openApp(app: String) {
        launcher = false
        val pkg = DesktopApps.packageOf(app)
        if (pkg == null) {
            state = Desktop.open(state, app)
            return
        }
        // Cihazdaki uygulama: tam ekran açılır; çubukta açık sekme olarak izlenir.
        val activity = context.findActivity()
        if (activity != null && InstalledApps.launch(activity, pkg)) {
            scope.launch { store.recordLaunch(pkg) }
            state = Desktop.open(state, app)
        } else {
            toastText = context.getString(R.string.desktop_app_launch_failed)
        }
    }

    // Pencere animasyonla kapanır (aşağı küçülerek): sekme durumdan hemen çıkar, içerik animasyon bitene dek bileşimde kalır;
    // AI oturumu animasyon bitince bırakılır (kapanırken sayfa boşalmasın).
    fun closeTab(id: Int) {
        val tab = state.byId(id) ?: return
        if (!DesktopApps.isExternal(tab.app)) closing = closing + tab
        state = Desktop.close(state, id)
    }

    fun finishClose(tab: DesktopTab) {
        closing = closing.filter { it.id != tab.id }
        val accountId = DesktopApps.aiAccountId(tab.app) ?: return
        // Aynı hesap bu arada yeniden açıldıysa oturum korunur.
        if (state.byApp(tab.app) == null) {
            opened.remove(accountId)
            hub.release(accountId)
        }
    }

    fun closeApp(app: String) {
        state.tabsOf(app).forEach { closeTab(it.id) }
    }

    fun closeAllTabs() {
        state.tabs.toList().forEach { closeTab(it.id) }
    }

    /** Yeni açılan sekmenin "ikondan büyüme" başlangıç noktası: alt çubuktaki simgesinin merkezi (yoksa alt orta). */
    fun revealOriginFor(app: String): () -> TransformOrigin = {
        val r = dockBounds[app]
        val w = areaSize.width.toFloat()
        val h = areaSize.height.toFloat()
        if (r == null || w <= 0f || h <= 0f) {
            TransformOrigin(0.5f, 1f)
        } else {
            TransformOrigin(((r.center.x - areaRoot.x) / w).coerceIn(0f, 1f), ((r.center.y - areaRoot.y) / h).coerceIn(0f, 2f))
        }
    }

    fun trackBounds(app: String, rect: Rect?) {
        if (rect == null) {
            dockBounds.remove(app)
        } else {
            dockBounds[app] = rect
        }
    }

    // ---- Dosyayı AI sayfasına verme ----
    fun sendToAi(accountId: Long, label: String, load: () -> UploadFile?) {
        val aiName = rowsById[accountId]?.displayName.orEmpty()
        scope.launch {
            val st = hub.state(accountId).value
            val handle = (st as? WebChatHub.State.Ready)?.takeIf { !it.borrowed }?.let { ready ->
                container.webMonitor.entries.value.firstOrNull { it.id == ready.entryId && it.isLive }?.let { container.webMonitor.handle(it.id) }
            }
            if (handle == null) {
                toastText = context.getString(R.string.desktop_upload_not_ready, aiName)
                return@launch
            }
            toastText = context.getString(R.string.desktop_upload_sending, label)
            val up = withContext(Dispatchers.IO) { load() }
            if (up == null) {
                toastText = context.getString(R.string.desktop_upload_unreadable)
                return@launch
            }
            val ok = handle.attachManual(up.name, up.mime, up.bytes)
            toastText = if (ok) context.getString(R.string.desktop_upload_ok, up.name, aiName) else context.getString(R.string.desktop_upload_failed, up.name)
        }
    }

    fun sendFile(file: File, accountId: Long) {
        scope.launch {
            // Normal erişimle okunabiliyorsa eskisi gibi; okunamıyorsa (örn. /data/data) ve Shizuku bağlıysa onun akışıyla.
            val direct = withContext(Dispatchers.IO) { file.canRead() }
            val size = withContext(Dispatchers.IO) { if (direct) file.length() else ShizukuFiles.sizeOf(file.path) ?: 0L }
            when {
                size > FileBrowse.MAX_UPLOAD_BYTES ->
                    toastText = context.getString(R.string.desktop_upload_too_big, file.name, FileBrowse.humanSize(FileBrowse.MAX_UPLOAD_BYTES))

                direct -> sendToAi(accountId, file.name) { FileBrowse.readForUpload(file) }

                else -> sendToAi(accountId, file.name) { FileBrowse.readStream(file.name, null, { ShizukuFiles.openRead(file.path) }) }
            }
        }
    }

    fun handleSystemDrop(accountId: Long, event: DragEvent): Boolean {
        val clip = event.clipData ?: return false
        val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        if (uris.isEmpty()) return false
        // Başka uygulamanın içeriğini okuma izni (bırakma sırasında alınmalı); okuma bitene dek referans tutulur.
        val activity = context.findActivity()
        if (activity != null) dropPermissions = ActivityCompat.requestDragAndDropPermissions(activity, event)
        uris.forEach { uri -> sendToAi(accountId, queryName(context, uri)) { readDropped(context, uri) } }
        return true
    }

    /** Dosya sürüklenirken parmağın altındaki alt çubuk öğesi açık bir AI sekmesiyse onun anahtarı. */
    fun dockAiAt(pointerPx: Offset): String? {
        val p = pointerPx + areaRoot
        return dockBounds.entries.firstOrNull { (app, rect) ->
            DesktopApps.aiAccountId(app) != null && state.byApp(app) != null && rect.contains(p)
        }?.key
    }

    // ---- Uygulama bilgisi (ad, renk, simge) ----
    val installedByPkg = installed?.associateBy { it.packageName }

    fun infoOf(app: String): AppInfo? {
        val aiId = DesktopApps.aiAccountId(app)
        if (aiId != null) {
            val account = rowsById[aiId] ?: return null
            val provider = account.webProvider() ?: return null
            return AppInfo(app, account.displayName, provider.tint(), provider)
        }
        if (app == DesktopApps.FILES) return AppInfo(app, filesLabel, scheme.primary)
        if (app == DesktopApps.KRIPTON) return AppInfo(app, kriptonLabel, scheme.primary)
        val pkg = DesktopApps.packageOf(app) ?: return null
        val label = installedByPkg?.get(pkg)?.label ?: return null
        return AppInfo(app, label, scheme.primary)
    }

    // ---- Launcher: rozetler, eylemler ----
    val badgeCounts = remember(sysNotifs) { Notifs.badgeCounts(sysNotifs) }
    val ownUnread = KriptonInbox.unread(inbox)
    fun badgeOf(app: String): Int = when {
        app == DesktopApps.KRIPTON -> ownUnread + (badgeCounts[context.packageName] ?: 0)
        else -> DesktopApps.packageOf(app)?.let { badgeCounts[it] } ?: 0
    }
    val totalUnread = Notifs.visible(sysNotifs).count { !it.ongoing && !it.isSummary } + ownUnread
    val ownApps = listOfNotNull(infoOf(DesktopApps.KRIPTON), infoOf(DesktopApps.FILES))
    val aiApps = aiRows.mapNotNull { infoOf(DesktopApps.ai(it.id)) }
    val isDefaultHome = remember(launcher, settingsOpen) { HomeRole.isDefault(context) }
    val darkNow = androidx.compose.foundation.isSystemInDarkTheme()
    val wallpaper by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, launcherPrefs.wallpaper) {
        value = withContext(Dispatchers.IO) { Wallpaper.load(context, launcherPrefs.wallpaper)?.asImageBitmap() }
    }
    val settingShortcuts = listOf(
        stringResource(R.string.l_st_wifi) to android.provider.Settings.ACTION_WIFI_SETTINGS,
        stringResource(R.string.l_st_bluetooth) to android.provider.Settings.ACTION_BLUETOOTH_SETTINGS,
        stringResource(R.string.l_st_display) to android.provider.Settings.ACTION_DISPLAY_SETTINGS,
        stringResource(R.string.l_st_sound) to android.provider.Settings.ACTION_SOUND_SETTINGS,
        stringResource(R.string.l_st_battery) to android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS,
        stringResource(R.string.l_st_apps) to android.provider.Settings.ACTION_APPLICATION_SETTINGS,
        stringResource(R.string.l_st_storage) to android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        stringResource(R.string.l_st_access) to android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS,
        stringResource(R.string.l_st_defaults) to android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
        stringResource(R.string.l_st_language) to android.provider.Settings.ACTION_LOCALE_SETTINGS,
    )
    val widgetAdder = rememberWidgetAdder(
        controller = container.launcher.widgets,
        onAdded = { id, rows -> homeState = Home.addItem(homeState, HomeItem.Widget(id, rows), homeState.pageCount - 1) },
        onFailed = { toastText = context.getString(R.string.l_widget_failed) },
    )
    val wallpaperPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val v = withContext(Dispatchers.IO) { Wallpaper.import(context, uri) }
                if (v != null) store.editLauncherPrefs { it.copy(wallpaper = v) } else toastText = context.getString(R.string.l_wallpaper_failed)
            }
        }
    }
    val contactsPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    fun closeOverlays() {
        launcher = false
        panelOpen = false
        askOpen = false
        drawerFocus = false
    }

    fun startIntent(i: android.content.Intent): Boolean = runCatching { context.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    /** Yazıyı AI'a gönderir: sekmeyi açar, oturum hazır olunca giriş kutusuna yazıp yollar. */
    fun askAi(accountId: Long, text: String) {
        val aiName = rowsById[accountId]?.displayName.orEmpty()
        closeOverlays()
        openApp(DesktopApps.ai(accountId))
        toastText = context.getString(R.string.l_ask_sending, aiName)
        scope.launch {
            val ready = kotlinx.coroutines.withTimeoutOrNull(40_000L) {
                hub.state(accountId).first { it is WebChatHub.State.Ready && !it.borrowed }
            } as? WebChatHub.State.Ready
            val handle = ready?.let { r -> container.webMonitor.entries.value.firstOrNull { it.id == r.entryId && it.isLive }?.let { container.webMonitor.handle(it.id) } }
            if (handle == null) {
                toastText = context.getString(R.string.desktop_upload_not_ready, aiName)
                return@launch
            }
            delay(700)
            toastText = if (handle.sendPromptManual(text)) context.getString(R.string.l_ask_sent, aiName) else context.getString(R.string.l_ask_failed, aiName)
        }
    }

    fun attachToAi(accountId: Long, uri: Uri) {
        openApp(DesktopApps.ai(accountId))
        sendToAi(accountId, queryName(context, uri)) { readDropped(context, uri) }
    }

    fun toggleTheme() {
        val effectiveDark = when (themePreference) {
            ThemePreference.DARK -> true
            ThemePreference.LIGHT -> false
            ThemePreference.SYSTEM -> darkNow
        }
        onThemeChange(if (effectiveDark) ThemePreference.LIGHT else ThemePreference.DARK)
    }

    val homeActions = HomeActions(
        open = { openApp(it) },
        change = { homeState = it },
        togglePin = { state = Desktop.togglePin(state, it) },
        openDrawer = {
            drawerFocus = false
            launcher = true
        },
        openNotifications = { panelOpen = true },
        openSearch = {
            drawerFocus = true
            launcher = true
        },
        openAsk = { id ->
            askTarget = id
            askText = ""
            askOpen = true
        },
        ask = { id, t -> askAi(id, t) },
        addWidget = { widgetPicker = true },
        openSettings = { settingsOpen = true },
        removeWidget = { container.launcher.widgets.delete(it) },
    )

    val drawerActions = DrawerActions(
        open = { openApp(it) },
        addToHome = { homeState = Home.addApp(homeState, it, 0) },
        removeFromHome = { homeState = Home.removeApp(homeState, it) },
        togglePin = { state = Desktop.togglePin(state, it) },
        setHidden = { pkg, hide -> scope.launch { store.editLauncherPrefs { it.copy(hiddenApps = if (hide) it.hiddenApps + pkg else it.hiddenApps - pkg) } } },
        uninstall = { startIntent(AppCatalog.uninstallIntent(it)) },
        appInfo = { startIntent(AppCatalog.appInfoIntent(it)) },
        shortcut = { container.launcher.apps.startShortcut(it) },
        openFile = { f ->
            closeOverlays()
            if (f.isDirectory) {
                openApp(DesktopApps.FILES)
            } else if (!FileOpen.launch(context, f, null)) {
                toastText = context.getString(R.string.desktop_files_no_app)
            }
        },
        dial = { startIntent(android.content.Intent(android.content.Intent.ACTION_DIAL, Uri.parse("tel:$it"))) },
        setting = {
            closeOverlays()
            startIntent(android.content.Intent(it))
        },
        web = {
            closeOverlays()
            startIntent(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(SearchEngine.webUrl(it))))
        },
        ask = { id, t -> askAi(id, t) },
        copy = { text ->
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)?.setPrimaryClip(android.content.ClipData.newPlainText("", text))
            toastText = context.getString(R.string.l_copied, text)
        },
        setSort = { sort -> scope.launch { store.editLauncherPrefs { it.copy(drawerSort = sort) } } },
        dismiss = {
            launcher = false
            drawerFocus = false
        },
    )

    val activeId = state.activeId
    val dockItems = Desktop.dock(state).mapNotNull { e ->
        infoOf(e.app)?.let { info ->
            // Aynı uygulamanın birden çok penceresi: kartlarda "Dosyalar · 2" gibi numaralanır.
            val tabs = e.tabIds.mapIndexed { i, id ->
                val label = if (e.grouped) context.getString(R.string.desktop_tab_window_label, info.title, i + 1) else info.title
                DockTab(id, label, id == activeId)
            }
            DockItem(info, e.tabId, e.pinned, tabs.any { it.active }, tabs, DesktopApps.allowsMultiple(e.app), if (launcherPrefs.badges) badgeOf(e.app) else 0)
        }
    }
    val openApps = state.tabs.map { it.app }.toSet()
    val pinnedSet = state.pinned.toSet()
    val dropHover = if (fileDrag.file != null) dockAiAt(fileDrag.pointer) else null

    // Ana ekran olarak: geri tuşu masaüstünden çıkmaz (yalnız açık katmanları kapatır).
    BackHandler(enabled = isHome) { }
    BackHandler(enabled = launcher || showPerm || panelOpen || askOpen || settingsOpen || widgetPicker) {
        when {
            showPerm -> showPerm = false

            settingsOpen -> settingsOpen = false

            widgetPicker -> widgetPicker = false

            askOpen -> askOpen = false

            panelOpen -> panelOpen = false

            else -> {
                launcher = false
                drawerFocus = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(scheme.background, k.nodeFill.copy(alpha = 0.55f), scheme.background))),
    ) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            areaRoot = it.positionInRoot()
                            areaSize = it.size
                        },
                ) {
                    // Hiçbir sekme görünmüyorsa: boş masaüstü.
                    if (restored && state.active == null) {
                        LauncherHome(
                            container = container,
                            state = homeState,
                            prefs = launcherPrefs,
                            clock = formatClock(minuteOfDay),
                            date = dateText,
                            infoOf = { infoOf(it) },
                            openApps = openApps,
                            pinned = pinnedSet,
                            badgeOf = { badgeOf(it) },
                            ai = aiApps,
                            unread = totalUnread,
                            wallpaper = wallpaper,
                            actions = homeActions,
                        )
                    }

                    // Bütün sekmeler bileşimde kalır; yalnız görünen sekme ekrandadır (diğerleri ekran dışında, saydam).
                    CompositionLocalProvider(LocalFileChooserOwnedByShell provides true, LocalInAppMessage provides { toastText = it }) {
                        // Kapanmakta olan sekmeler de çizilir (animasyon bitene dek); anahtar aynı kaldığı için içerik korunur.
                        val closingIds = closing.map { it.id }.toSet()
                        val layers = (state.tabs + closing.filter { c -> state.tabs.none { it.id == c.id } })
                            .filter { !DesktopApps.isExternal(it.app) }
                            .sortedBy { it.id }
                        layers.forEach { tab ->
                            key(tab.id) {
                                val visible = tab.id == activeId
                                val aiId = DesktopApps.aiAccountId(tab.app)
                                TabLayer(
                                    visible = visible,
                                    closing = tab.id in closingIds,
                                    revealOrigin = if (tab.id in restoredIds) null else revealOriginFor(tab.app),
                                    onClosed = { finishClose(tab) },
                                    background = scheme.background,
                                ) {
                                    when {
                                        aiId != null -> {
                                            val account = rowsById[aiId]
                                            val title = account?.displayName ?: "AI"
                                            AiDropArea(
                                                label = stringResource(R.string.desktop_drop_here, title),
                                                tint = account?.webProvider()?.tint() ?: scheme.primary,
                                                onSystemDrop = { ev -> handleSystemDrop(aiId, ev) },
                                            ) {
                                                AiWindowContent(
                                                    accountId = aiId,
                                                    account = account,
                                                    container = container,
                                                    onOpenMonitor = { openApp(DesktopApps.KRIPTON) },
                                                )
                                            }
                                        }

                                        tab.app == DesktopApps.FILES -> DesktopFilesWindow(
                                            drag = fileDrag,
                                            aiTargets = state.tabs.mapNotNull { t ->
                                                DesktopApps.aiAccountId(t.app)?.let { id -> rowsById[id]?.let { AiTarget(id, it.displayName) } }
                                            },
                                            areaOrigin = { areaRoot },
                                            shizukuEnabled = shizukuFiles,
                                            showHints = prefs.showHints,
                                            defaultHidden = prefs.filesHidden,
                                            defaultExt = prefs.filesExt,
                                            defaultGrid = prefs.filesGrid,
                                            onSend = { f, id -> sendFile(f, id) },
                                            onDragDrop = { f ->
                                                dockAiAt(fileDrag.pointer)?.let { app -> DesktopApps.aiAccountId(app)?.let { sendFile(f, it) } }
                                            },
                                        )

                                        else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                                            // Sekme genişliğine göre telefon/tablet düzeni (ekran genişliğine göre değil).
                                            AppRoot(
                                                themePreference = themePreference,
                                                onThemeChange = onThemeChange,
                                                playIntro = false,
                                                windowClassOverride = windowClassFor(maxWidth.value.toInt()),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!imeVisible && dockVisible) {
                    Dock(
                        items = dockItems,
                        launcherOpen = launcher,
                        desktopShown = state.active == null,
                        clock = formatClock(minuteOfDay),
                        fullscreen = fullscreen,
                        showLabels = prefs.dockLabels,
                        showClock = prefs.showClock,
                        dropHoverApp = dropHover,
                        hasOpenTabs = state.tabs.isNotEmpty(),
                        onLauncher = {
                            dockPeekTick++
                            launcher = !launcher
                        },
                        onItem = { item ->
                            dockPeekTick++
                            launcher = false
                            val tabId = item.tabId
                            when {
                                // Harici uygulamaya dokunmak onu yeniden öne getirir.
                                DesktopApps.isExternal(item.info.app) -> openApp(item.info.app)

                                // Görünen sekmede değilse öne getirir (grupta: görünen ya da ilk pencere); açık değilse açar.
                                !item.active && tabId != null -> {
                                    state = Desktop.activate(state, tabId)
                                }

                                !item.active -> openApp(item.info.app)

                                // Zaten görünen grupta yeniden dokunmak sıradaki pencereye geçer; tek pencerede bir şey yapmaz.
                                item.grouped -> {
                                    state = Desktop.cycleInGroup(state, item.info.app)
                                }

                                else -> Unit
                            }
                        },
                        onClose = { item -> item.tabId?.let { closeTab(it) } },
                        onTogglePin = { item -> state = Desktop.togglePin(state, item.info.app) },
                        onMovePin = { item, delta -> state = Desktop.movePinned(state, item.info.app, delta) },
                        onReload = { item ->
                            DesktopApps.aiAccountId(item.info.app)?.let { id -> rowsById[id]?.let { hub.reopen(it) } }
                        },
                        onTabActivate = { id -> state = Desktop.activate(state, id) },
                        onTabClose = { id -> closeTab(id) },
                        onNewWindow = { item ->
                            launcher = false
                            state = Desktop.openNew(state, item.info.app)
                        },
                        onCloseApp = { item -> closeApp(item.info.app) },
                        onCloseAll = { closeAllTabs() },
                        onShowDesktop = {
                            launcher = false
                            state = Desktop.showDesktop(state)
                        },
                        onToggleFullscreen = { scope.launch { store.setDesktopFullscreen(!fullscreen) } },
                        onBounds = { app, rect -> trackBounds(app, rect) },
                    )
                }
            }

            if (launcher) {
                Box(Modifier.fillMaxSize().padding(bottom = dockPad)) {
                    AppDrawer(
                        container = container,
                        prefs = launcherPrefs,
                        apps = launcherApps,
                        own = ownApps,
                        ai = aiApps,
                        infoOf = { infoOf(it) },
                        openApps = openApps,
                        pinned = pinnedSet,
                        onHome = homeState.pages.flatMap { p -> p.items.mapNotNull { (it as? HomeItem.App)?.app } }.toSet(),
                        badgeOf = { badgeOf(it) },
                        settingShortcuts = settingShortcuts,
                        startFocused = drawerFocus,
                        actions = drawerActions,
                    )
                }
            }

            if (panelOpen) {
                NotificationPanel(
                    clock = formatClock(minuteOfDay),
                    date = dateText,
                    onOpenKripton = {
                        panelOpen = false
                        openApp(DesktopApps.KRIPTON)
                    },
                    onToggleTheme = { toggleTheme() },
                    onSettings = {
                        panelOpen = false
                        settingsOpen = true
                    },
                    onDismiss = { panelOpen = false },
                )
            }

            if (askOpen) {
                AskSheet(
                    ai = aiApps,
                    initialTarget = askTarget,
                    initialText = askText,
                    onSend = { id, t -> askAi(id, t) },
                    onAttach = { id, uri ->
                        askOpen = false
                        attachToAi(id, uri)
                    },
                    onOpenTab = { id ->
                        askOpen = false
                        openApp(DesktopApps.ai(id))
                    },
                    onDismiss = { askOpen = false },
                )
            }

            if (widgetPicker) {
                WidgetPicker(
                    providers = remember(widgetPicker) { container.launcher.widgets.providers() },
                    context = context,
                    onPick = {
                        widgetPicker = false
                        widgetAdder.start(it)
                    },
                    onDismiss = { widgetPicker = false },
                )
            }

            if (settingsOpen) {
                val hiddenLabels = launcherApps.orEmpty().filter { it.packageName in launcherPrefs.hiddenApps }.map { it.packageName to it.label }
                Box(Modifier.fillMaxSize().zIndex(9200f)) {
                    LauncherSettings(
                        prefs = launcherPrefs,
                        isDefaultHome = isDefaultHome,
                        hiddenLabels = hiddenLabels,
                        onChange = { change -> scope.launch { store.editLauncherPrefs(change) } },
                        onPickWallpaper = { wallpaperPicker.launch("image/*") },
                        onSystemWallpaper = { scope.launch { store.editLauncherPrefs { it.copy(wallpaper = Wallpaper.SYSTEM) } } },
                        onClearWallpaper = { scope.launch { store.editLauncherPrefs { it.copy(wallpaper = "") } } },
                        onRequestContacts = { contactsPermission.launch(android.Manifest.permission.READ_CONTACTS) },
                        onSetHome = { HomeRole.request(context) },
                        onAddWidget = {
                            settingsOpen = false
                            widgetPicker = true
                        },
                        onAddPage = { homeState = Home.addPage(homeState) },
                        onClose = { settingsOpen = false },
                    )
                }
            }

            if (!dockVisible && !imeVisible) {
                DockHandle(
                    onReveal = {
                        dockPeek = true
                        dockPeekTick++
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).zIndex(8000f),
                )
            }

            toastText?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.surface,
                    border = BorderStroke(1.5.dp, k.nodeBorder),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = (if (dockVisible) DOCK_HEIGHT else 0.dp) + 16.dp, start = 16.dp, end = 16.dp)
                        .zIndex(9000f),
                ) {
                    Text(msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
        }

        // Sürüklenen dosyanın parmağı izleyen etiketi (alt çubuğun da üstünde görünsün diye en dışta).
        fileDrag.file?.let { f ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = scheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .zIndex(9500f)
                    .offset { IntOffset((fileDrag.pointer.x + areaRoot.x).roundToInt() - 24, (fileDrag.pointer.y + areaRoot.y).roundToInt() - 56) },
            ) {
                Text(f.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), maxLines = 1)
            }
        }

        if (showPerm) {
            DesktopConfirm(
                title = stringResource(R.string.desktop_perm_title),
                body = stringResource(R.string.desktop_perm_body),
                confirmText = stringResource(R.string.desktop_perm_grant),
                dismissText = stringResource(R.string.desktop_perm_later),
                onConfirm = {
                    showPerm = false
                    scope.launch { store.setDesktopPermissionAsked(true) }
                    filesAccess.request()
                },
                onDismiss = {
                    showPerm = false
                    scope.launch { store.setDesktopPermissionAsked(true) }
                },
            )
        }
    }
}

/** AI sekmesinin içeriği: sayfa hazırsa web arayüzü, değilse durum bildirimi. */
@Composable
private fun AiWindowContent(
    accountId: Long,
    account: AiAccount?,
    container: AppContainer,
    onOpenMonitor: () -> Unit,
) {
    val hub = container.webChatHub
    val k = KriptonTheme.colors
    val provider = account?.webProvider()
    val state by hub.state(accountId).collectAsStateWithLifecycle()
    val entries by container.webMonitor.entries.collectAsStateWithLifecycle()
    val s = state
    when {
        account == null || provider == null -> Notice(stringResource(R.string.web_chats_no_account), null, null)

        s is WebChatHub.State.Failed -> Notice(stringResource(R.string.web_chats_failed), stringResource(R.string.web_chats_retry)) { hub.reopen(account) }

        s is WebChatHub.State.Ready -> {
            val entry = entries.firstOrNull { it.id == s.entryId && it.isLive }
            when {
                entry == null -> Notice(stringResource(R.string.web_chats_closed), stringResource(R.string.web_chats_retry)) { hub.reopen(account) }
                s.borrowed -> Notice(stringResource(R.string.web_chats_busy), stringResource(R.string.web_chats_open_monitor), onOpenMonitor)
                else -> ChatPage(handle = container.webMonitor.handle(entry.id), installFileChooser = false)
            }
        }

        else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            com.kriptoncod.app.ui.components.KriptonLoader()
            Text(
                stringResource(R.string.web_chats_opening),
                style = MaterialTheme.typography.bodySmall,
                color = k.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Bırakılan içeriğin görünen adı (yoksa yolun son parçası). */
private fun queryName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment ?: "dosya"

/** Başka uygulamanın bıraktığı içeriği okur (en çok [FileBrowse.MAX_UPLOAD_BYTES]); okunamazsa null. */
private fun readDropped(context: Context, uri: Uri): UploadFile? {
    val name = queryName(context, uri)
    val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    return FileBrowse.readStream(name, mime, { context.contentResolver.openInputStream(uri) })
}
