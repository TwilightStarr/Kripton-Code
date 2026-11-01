package com.kriptoncod.app.ui.desktop

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.view.DragEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.app.DragAndDropPermissionsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.R
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.web.WebChatHub
import com.kriptoncod.app.domain.ThemePreference
import com.kriptoncod.app.ui.adaptive.windowClassFor
import com.kriptoncod.app.ui.components.KriptonCodLogo
import com.kriptoncod.app.ui.navigation.AppRoot
import com.kriptoncod.app.ui.screens.Avatar
import com.kriptoncod.app.ui.screens.ChatPage
import com.kriptoncod.app.ui.screens.Notice
import com.kriptoncod.app.ui.screens.WebFileChooserEffect
import com.kriptoncod.app.ui.screens.tint
import com.kriptoncod.app.ui.screens.webProvider
import com.kriptoncod.app.ui.theme.KriptonTheme
import com.kriptoncod.app.ui.theme.LocalAnimationsEnabled
import java.io.File
import java.time.LocalTime
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
 * Masaüstü modu: tam ekran, taşınabilir/boyutlandırılabilir pencereler, görev çubuğu, dosya yöneticisi.
 *
 *  - AI web arayüzleri sade pencerelerdir: başlıktan sürükle = taşı, kenar/köşeden sürükle = boyutlandır, çift dokun = büyüt,
 *    kenara sürükle = yarım/çeyrek/tam ekrana yaslan. İçlerine normal klavyeyle yazılır (klavye açılınca alan küçülür, pencere sığdırılır).
 *  - "Kripton Code" penceresi uygulamanın bütün ekranlarını (AppRoot) barındırır.
 *  - Dosyalar penceresi (tüm dosyalara erişim izniyle) dosyaları AI penceresine sürükle-bırak ya da menüyle verir;
 *    başka uygulamadan bırakılan dosyalar da kabul edilir (bölünmüş ekran).
 *  - Yan yana uygulama: kurulu bir uygulama Android bölünmüş ekranında Kripton Code'un yanında açılır.
 *  - Pencere düzeni kaydedilir; mod bir kez açılınca uygulama hep bununla başlar ([DesktopModeHost]).
 *
 * Pencere kuralları [Desktop] içindedir (ekrandan bağımsız); harici ekran/DeX desteği için yalnız yeni bir alan verilir.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun DesktopShell(
    container: AppContainer,
    themePreference: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val d = density.density
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val store = container.themeStore
    val hub = container.webChatHub
    val animations = LocalAnimationsEnabled.current
    val desktopMode = LocalDesktopMode.current

    val fullscreen by store.desktopFullscreenFlow.collectAsStateWithLifecycle(initialValue = true)
    DesktopImmersiveEffect(fullscreen)
    // Dosya seçici bütün pencereler için TEK kez kurulur; içerideki ekranlar kendi seçicisini kurmaz.
    WebFileChooserEffect()

    val accountsState: List<AiAccount>? by container.aiAccountRepository.observeAll().collectAsStateWithLifecycle(initialValue = null)
    val aiRows = remember(accountsState) { accountsState.orEmpty().filter { it.webProvider() != null }.sortedBy { it.displayName.lowercase() } }
    val rowsById = remember(aiRows) { aiRows.associateBy { it.id } }

    var state by remember { mutableStateOf(DesktopState()) }
    var area by remember { mutableStateOf(DSize(0f, 0f)) }
    var areaRoot by remember { mutableStateOf(Offset.Zero) }
    var restored by remember { mutableStateOf(false) }
    var interacting by remember { mutableStateOf(false) }
    var snapPreview by remember { mutableStateOf<WinMode?>(null) }
    var pointerDp by remember { mutableStateOf(Offset.Zero) }
    var startMenu by remember { mutableStateOf(false) }
    var sbsOpen by remember { mutableStateOf(false) }
    var showPerm by remember { mutableStateOf(false) }
    var toastText by remember { mutableStateOf<String?>(null) }
    var minuteOfDay by remember { mutableIntStateOf(0) }
    val fileDrag = remember { FileDrag() }
    val opened = remember { mutableSetOf<Long>() }
    var dropPermissions by remember { mutableStateOf<DragAndDropPermissionsCompat?>(null) }
    val filesAccess = rememberFilesAccess()
    val imeVisible = WindowInsets.isImeVisible

    // ---- Kayıt / geri yükleme ----
    LaunchedEffect(area.w > 0f) {
        if (area.w <= 0f || restored) return@LaunchedEffect
        val saved = DesktopCodec.decode(store.desktopLayoutFlow.first())
        state = if (saved.windows.isEmpty()) {
            Desktop.open(DesktopState(), DesktopApps.KRIPTON, area, mode = WinMode.MAXIMIZED)
        } else {
            saved
        }
        restored = true
        if (!store.desktopPermissionAsked.first()) {
            if (FilesAccess.isGranted(context)) store.setDesktopPermissionAsked(true) else showPerm = true
        }
    }
    LaunchedEffect(restored) {
        if (!restored) return@LaunchedEffect
        snapshotFlow { DesktopCodec.encode(state) }.distinctUntilChanged().drop(1).debounce(500).collect { store.setDesktopLayout(it) }
    }
    // Saat
    LaunchedEffect(Unit) {
        while (true) {
            val t = LocalTime.now()
            minuteOfDay = t.hour * 60 + t.minute
            delay(15_000)
        }
    }
    // Silinen hesapların pencerelerini kapat (liste yüklenmeden önce hiçbir şey silinmez).
    LaunchedEffect(accountsState, restored) {
        if (accountsState == null || !restored) return@LaunchedEffect
        state.windows.forEach { w ->
            val id = DesktopApps.aiAccountId(w.app)
            if (id != null && id !in rowsById) state = Desktop.close(state, w.id)
        }
    }
    // Açık AI pencerelerinin web oturumunu aç (zaten açıksa hub bir şey yapmaz).
    val aiIds = state.windows.mapNotNull { DesktopApps.aiAccountId(it.app) }
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

    // ---- Pencere eylemleri ----
    fun open(app: String, mode: WinMode = WinMode.NORMAL) {
        if (area.w <= 0f) return
        state = Desktop.open(state, app, area, mode = mode)
        startMenu = false
    }

    fun closeWindow(win: DesktopWindow) {
        DesktopApps.aiAccountId(win.app)?.let { id ->
            opened.remove(id)
            hub.release(id)
        }
        state = Desktop.close(state, win.id)
    }

    fun moveStart(win: DesktopWindow, startLocalPx: Offset, titleWidthPx: Float) {
        val eff = Desktop.boxOf(win, area)
        pointerDp = Offset(eff.x + startLocalPx.x / d, eff.y + startLocalPx.y / d)
        state = Desktop.beginMove(state, win.id, area, startLocalPx.x / titleWidthPx)
        interacting = true
    }

    fun moveDelta(id: Int, dx: Float, dy: Float) {
        state = Desktop.moveBy(state, id, dx, dy, area)
        pointerDp += Offset(dx, dy)
        snapPreview = Desktop.snapZone(pointerDp.x, pointerDp.y, area)
    }

    fun moveEnd(id: Int) {
        snapPreview?.let { state = Desktop.setMode(state, id, it) }
        snapPreview = null
        interacting = false
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
        if (file.length() > FileBrowse.MAX_UPLOAD_BYTES) {
            toastText = context.getString(R.string.desktop_upload_too_big, file.name, FileBrowse.humanSize(FileBrowse.MAX_UPLOAD_BYTES))
        } else {
            sendToAi(accountId, file.name) { FileBrowse.readForUpload(file) }
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

    /** Uygulama içi sürüklemede parmağın altındaki (en üstteki) pencere AI penceresiyse o. */
    fun aiWindowAt(pointerPx: Offset): DesktopWindow? {
        val x = pointerPx.x / d
        val y = pointerPx.y / d
        val top = state.windows.asReversed().firstOrNull { !it.minimized && Desktop.boxOf(it, area).contains(x, y) }
        return top?.takeIf { DesktopApps.aiAccountId(it.app) != null }
    }

    val hoverWin = if (fileDrag.file != null) aiWindowAt(fileDrag.pointer) else null
    val focusedId = state.focused?.id
    val openApps = state.windows.map { it.app }.toSet()

    BackHandler(enabled = startMenu || sbsOpen || showPerm) {
        when {
            showPerm -> showPerm = false
            sbsOpen -> sbsOpen = false
            else -> startMenu = false
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
                        .onGloballyPositioned { areaRoot = it.positionInRoot() }
                        .onSizeChanged { area = DSize(it.width / d, it.height / d) }
                        .drawBehind {
                            // Hafif nokta ızgarası: masaüstü dokusu.
                            val step = 36.dp.toPx()
                            var y = step / 2
                            while (y < size.height) {
                                var x = step / 2
                                while (x < size.width) {
                                    drawCircle(Color.White.copy(alpha = 0.05f), 1.4.dp.toPx(), Offset(x, y))
                                    x += step
                                }
                                y += step
                            }
                        },
                ) {
                    if (state.windows.none { !it.minimized } && restored) {
                        Text(
                            stringResource(R.string.desktop_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = k.muted,
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        )
                    }

                    val zOrder = state.windows.mapIndexed { i, w -> w.id to (i + 1).toFloat() }.toMap()
                    CompositionLocalProvider(LocalFileChooserOwnedByShell provides true) {
                        state.windows.sortedBy { it.id }.forEach { win ->
                            key(win.id) {
                                val aiId = DesktopApps.aiAccountId(win.app)
                                val account = aiId?.let { rowsById[it] }
                                val provider = account?.webProvider()
                                val tint = provider?.tint() ?: scheme.primary
                                val title = when {
                                    aiId != null -> account?.displayName ?: "AI"
                                    win.app == DesktopApps.FILES -> stringResource(R.string.desktop_app_files)
                                    else -> stringResource(R.string.desktop_app_kripton)
                                }
                                WindowFrame(
                                    win = win,
                                    area = area,
                                    z = zOrder[win.id] ?: 1f,
                                    focused = focusedId == win.id,
                                    tint = tint,
                                    title = title,
                                    animate = animations && !interacting,
                                    dropHover = hoverWin?.id == win.id,
                                    dropLabel = stringResource(R.string.desktop_drop_here, title),
                                    acceptSystemDrop = aiId != null,
                                    onSystemDrop = { ev -> aiId != null && handleSystemDrop(aiId, ev) },
                                    onFocus = { state = Desktop.focus(state, win.id) },
                                    onMinimize = { state = Desktop.minimize(state, win.id) },
                                    onToggleMax = { state = Desktop.toggleMaximize(state, win.id) },
                                    onClose = { closeWindow(win) },
                                    onMoveStart = { p, w -> moveStart(state.byId(win.id) ?: win, p, w) },
                                    onMove = { dx, dy -> moveDelta(win.id, dx, dy) },
                                    onMoveEnd = { moveEnd(win.id) },
                                    onResizeStart = { interacting = true },
                                    onResize = { edges, dx, dy -> state = Desktop.resizeBy(state, win.id, edges, dx, dy, area) },
                                    onResizeEnd = { interacting = false },
                                    icon = {
                                        when {
                                            provider != null -> Avatar(provider, 22.dp)
                                            win.app == DesktopApps.FILES -> FolderGlyph(scheme.primary, 22.dp)
                                            else -> KriptonCodLogo(logoSize = 22.dp)
                                        }
                                    },
                                    titleActions = {
                                        if (account != null) {
                                            IconButton(onClick = { hub.reopen(account) }, modifier = Modifier.size(34.dp)) {
                                                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.web_chats_reload), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                ) {
                                    when {
                                        aiId != null -> AiWindowContent(
                                            accountId = aiId,
                                            account = account,
                                            container = container,
                                            onOpenMonitor = { open(DesktopApps.KRIPTON) },
                                            onFocus = { state = Desktop.focus(state, win.id) },
                                        )

                                        win.app == DesktopApps.FILES -> DesktopFilesWindow(
                                            drag = fileDrag,
                                            aiTargets = state.windows.mapNotNull { w ->
                                                DesktopApps.aiAccountId(w.app)?.let { id -> rowsById[id]?.let { AiTarget(id, it.displayName) } }
                                            },
                                            areaOrigin = { areaRoot },
                                            onSend = { f, id -> sendFile(f, id) },
                                            onDragDrop = { f -> aiWindowAt(fileDrag.pointer)?.let { w -> DesktopApps.aiAccountId(w.app)?.let { sendFile(f, it) } } },
                                        )

                                        else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                                            // Pencere genişliğine göre telefon/tablet düzeni (ekran genişliğine göre değil).
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

                    snapPreview?.let { zone -> Desktop.modeBox(zone, area)?.let { SnapPreview(it, scheme.primary) } }

                    // Sürüklenen dosyanın parmağı izleyen etiketi.
                    fileDrag.file?.let { f ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = scheme.surface,
                            tonalElevation = 0.dp,
                            modifier = Modifier
                                .zIndex(5000f)
                                .offset { IntOffset(fileDrag.pointer.x.roundToInt() - 24, fileDrag.pointer.y.roundToInt() - 56) },
                        ) {
                            Text(f.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), maxLines = 1)
                        }
                    }
                }

                if (!imeVisible) {
                    val items = state.windows.sortedBy { it.id }.map { w ->
                        val aiId = DesktopApps.aiAccountId(w.app)
                        val account = aiId?.let { rowsById[it] }
                        val provider = account?.webProvider()
                        val tint = provider?.tint() ?: scheme.primary
                        TaskItem(
                            id = w.id,
                            title = when {
                                aiId != null -> account?.displayName ?: "AI"
                                w.app == DesktopApps.FILES -> stringResource(R.string.desktop_app_files)
                                else -> stringResource(R.string.desktop_app_kripton)
                            },
                            focused = focusedId == w.id,
                            minimized = w.minimized,
                            tint = tint,
                            icon = {
                                when {
                                    provider != null -> Avatar(provider, 22.dp)
                                    w.app == DesktopApps.FILES -> FolderGlyph(scheme.primary, 22.dp)
                                    else -> KriptonCodLogo(logoSize = 22.dp)
                                }
                            },
                        )
                    }
                    Taskbar(
                        items = items,
                        startOpen = startMenu,
                        clock = formatClock(minuteOfDay),
                        fullscreen = fullscreen,
                        onStart = { startMenu = !startMenu },
                        onItem = { id ->
                            state = Desktop.toggleFromTaskbar(state, id)
                            startMenu = false
                        },
                        onSideBySide = {
                            startMenu = false
                            sbsOpen = true
                        },
                        onToggleFullscreen = { scope.launch { store.setDesktopFullscreen(!fullscreen) } },
                    )
                }
            }

            if (startMenu && !imeVisible) {
                Box(Modifier.fillMaxSize().padding(bottom = TASKBAR_HEIGHT + 2.dp)) {
                    StartMenu(
                        aiRows = aiRows,
                        openApps = openApps,
                        fullscreen = fullscreen,
                        onOpenAi = { open(DesktopApps.ai(it.id)) },
                        onOpenFiles = { open(DesktopApps.FILES) },
                        onOpenKripton = { open(DesktopApps.KRIPTON) },
                        onSideBySide = {
                            startMenu = false
                            sbsOpen = true
                        },
                        onTile = {
                            state = Desktop.tile(state, area)
                            startMenu = false
                        },
                        onCascade = {
                            state = Desktop.cascade(state, area)
                            startMenu = false
                        },
                        onShowDesktop = {
                            state = Desktop.minimizeAll(state)
                            startMenu = false
                        },
                        onToggleFullscreen = { scope.launch { store.setDesktopFullscreen(!fullscreen) } },
                        onExit = {
                            startMenu = false
                            desktopMode.requestExit()
                        },
                        onDismiss = { startMenu = false },
                    )
                }
            }

            toastText?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, k.nodeBorder),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = TASKBAR_HEIGHT + 16.dp, start = 16.dp, end = 16.dp)
                        .zIndex(9000f),
                ) {
                    Text(msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
        }

        if (sbsOpen) {
            SideBySidePicker(
                onDismiss = { sbsOpen = false },
                onPick = { app ->
                    sbsOpen = false
                    val activity = context.findActivity()
                    if (activity == null || !SideBySide.launch(activity, app.packageName)) {
                        Toast.makeText(context, R.string.desktop_sbs_failed, Toast.LENGTH_SHORT).show()
                    } else if (!activity.isInMultiWindowMode) {
                        Toast.makeText(context, R.string.desktop_sbs_not_split, Toast.LENGTH_LONG).show()
                    }
                },
            )
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

/** AI penceresinin içeriği: sayfa hazırsa web arayüzü, değilse durum bildirimi. */
@Composable
private fun AiWindowContent(
    accountId: Long,
    account: AiAccount?,
    container: AppContainer,
    onOpenMonitor: () -> Unit,
    onFocus: () -> Unit,
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

                else -> ChatPage(handle = container.webMonitor.handle(entry.id), installFileChooser = false, onFocus = onFocus)
            }
        }

        else -> Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
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
