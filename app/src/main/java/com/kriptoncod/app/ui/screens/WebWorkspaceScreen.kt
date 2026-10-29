package com.kriptoncod.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.R
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.web.WebChatHub
import com.kriptoncod.app.data.web.WebPhase
import com.kriptoncod.app.ui.adaptive.LocalWindowClass
import com.kriptoncod.app.ui.adaptive.WindowClass
import com.kriptoncod.app.ui.theme.KriptonTheme
import com.kriptoncod.app.ui.theme.LocalAnimationsEnabled
import com.kriptoncod.app.ui.workspace.FOCUS_BOOST
import com.kriptoncod.app.ui.workspace.GAP_DP
import com.kriptoncod.app.ui.workspace.LayoutPreset
import com.kriptoncod.app.ui.workspace.PANES_PER_PAGE
import com.kriptoncod.app.ui.workspace.PaneBox
import com.kriptoncod.app.ui.workspace.PaneWeight
import com.kriptoncod.app.ui.workspace.TYPING_BOOST
import com.kriptoncod.app.ui.workspace.WorkspaceCodec
import com.kriptoncod.app.ui.workspace.WorkspaceSnapshot
import com.kriptoncod.app.ui.workspace.badgedPages
import com.kriptoncod.app.ui.workspace.effectivePin
import com.kriptoncod.app.ui.workspace.finishedAccounts
import com.kriptoncod.app.ui.workspace.layoutPane
import com.kriptoncod.app.ui.workspace.pageOfItem
import com.kriptoncod.app.ui.workspace.pagedItems
import com.kriptoncod.app.ui.workspace.pagesWithPin
import com.kriptoncod.app.ui.workspace.paneAt
import com.kriptoncod.app.ui.workspace.resizePane
import com.kriptoncod.app.ui.workspace.swapItems
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

private val OpenListSaver = listSaver<SnapshotStateList<Long>, Long>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

/**
 * Çalışma alanı: tablette birden çok AI'ın web arayüzü AYNI ANDA açılır ve elle kullanılır.
 *
 *  - Her pencere gerçek web sayfasıdır; sayfadaki "dosya ekle" düğmesi ve klavyeyle yazma her pencerede çalışır
 *    (dosya seçici ekran başına bir kez kurulur, hangi pencere isterse ona verir).
 *  - Bir sayfada en çok 4 pencere görünür; fazlası "Sonraki sayfa" ile açılır. Yerleşim, o sayfadaki AI sayısına göre değişir.
 *  - Her pencerenin sağ-alt köşesindeki tutamaçla boyutu sürüklenerek değiştirilir (komşu pencereler aynı oranda küçülür,
 *    pencereler asla üst üste binmez).
 *  - Dokunulan ya da klavyeyle yazılan pencere otomatik büyür; klavye açıkken yazılan pencere belirgin biçimde genişler.
 *  - Düzen kaydedilir (açık AI'lar, boyutlar, sabit pencere, hazır düzen); uygulama açılınca aynı çalışma alanı gelir.
 *  - Hazır düzenler ("Eşit böl", "Biri büyük, diğerleri yan şeritte"), sabitleme (pin: her sayfada görünür),
 *    başlığı sürükleyerek yer değiştirme, başlığa çift dokunarak tam ekran, başka sayfadaki AI yanıtı bitince sayfa noktasında rozet.
 */
@OptIn(ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
fun WebWorkspaceScreen(container: AppContainer, onBack: () -> Unit, onOpenMonitor: () -> Unit = {}) {
    val hub = container.webChatHub
    val accountsState: List<AiAccount>? by container.aiAccountRepository.observeAll().collectAsStateWithLifecycle(initialValue = null)
    val windowClass = LocalWindowClass.current
    val perPage = if (windowClass == WindowClass.Compact) 1 else PANES_PER_PAGE
    val animations = LocalAnimationsEnabled.current

    val rows = remember(accountsState) {
        accountsState.orEmpty().filter { it.webProvider() != null }.sortedBy { it.displayName.lowercase() }
    }
    val rowsById = remember(rows) { rows.associateBy { it.id } }

    val open = rememberSaveable(saver = OpenListSaver) { mutableStateListOf<Long>() }
    val weights = remember { mutableStateMapOf<Long, PaneWeight>() }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var focusedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var maximizedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var growOnFocus by rememberSaveable { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }
    var pinnedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var presetKey by rememberSaveable { mutableStateOf(LayoutPreset.EQUAL.key) }
    val preset = LayoutPreset.fromKey(presetKey)
    val sidebar = preset == LayoutPreset.SIDEBAR
    val unseen = remember { mutableStateListOf<Long>() }
    val store = container.themeStore
    var restored by remember { mutableStateOf(false) }
    val typing = WindowInsets.isImeVisible
    val opened = remember { mutableSetOf<Long>() }

    fun closePane(id: Long) {
        open.remove(id)
        weights.remove(id)
        opened.remove(id)
        hub.release(id)
        if (focusedId == id) focusedId = null
        if (maximizedId == id) maximizedId = null
        if (pinnedId == id) pinnedId = null
        unseen.remove(id)
        page = page.coerceIn(0, pagesWithPin(open.toList(), pinnedId, perPage) - 1)
    }

    fun togglePane(id: Long) {
        if (id in open) {
            closePane(id)
        } else {
            open.add(id)
            focusedId = id
            // Yeni açılan pencere hangi sayfaya düştüyse oraya geç (5. AI ikinci sayfada açılır; sabit pencere her sayfadadır).
            page = pageOfItem(open.toList(), pinnedId, id, perPage) ?: page
        }
    }

    fun togglePin(id: Long) {
        pinnedId = if (pinnedId == id) null else id
        page = page.coerceIn(0, pagesWithPin(open.toList(), pinnedId, perPage) - 1)
    }

    // Sürüklenerek yer değiştirme: iki pencerenin sırası değişir, ama bölme boyutları yerinde kalır (boyut yuvaya aittir).
    fun swapPanes(a: Long, b: Long) {
        val pin = effectivePin(open.toList(), pinnedId, perPage)
        if (a == pin || b == pin) return
        val swapped = swapItems(open.toList(), a, b)
        if (swapped == open.toList()) return
        swapped.forEachIndexed { i, id -> open[i] = id }
        val wa = weights[a]
        val wb = weights[b]
        if (wb != null) weights[a] = wb else weights.remove(a)
        if (wa != null) weights[b] = wa else weights.remove(b)
    }

    // Silinen hesapların pencerelerini kapat (liste yüklenmeden önce hiçbir şey silinmez).
    LaunchedEffect(accountsState) {
        if (accountsState == null) return@LaunchedEffect
        open.filter { it !in rowsById }.forEach { closePane(it) }
    }
    // Eklenen pencerelerin web oturumunu aç (zaten açıksa hub bir şey yapmaz).
    LaunchedEffect(open.toList(), accountsState) {
        open.toList().forEach { id ->
            if (opened.add(id)) {
                val acc = container.aiAccountRepository.get(id)
                if (acc != null) hub.open(acc) else opened.remove(id)
            }
        }
    }
    // Sayfa başına düşen sayı değişince (döndürme/bölme) ya da sabit pencere değişince geçerli sayfayı sınırlar içinde tut.
    LaunchedEffect(open.size, perPage, pinnedId) {
        page = page.coerceIn(0, pagesWithPin(open.toList(), pinnedId, perPage) - 1)
    }
    // Kayıtlı düzeni geri yükle (hesaplar yüklenince, bir kez). Ekran yeniden kurulduysa (döndürme) açık liste zaten durur;
    // bu durumda yalnız bellekte tutulmayan boyutlar geri gelir.
    LaunchedEffect(accountsState) {
        if (restored || accountsState == null) return@LaunchedEffect
        val snap = WorkspaceCodec.decode(store.workspaceLayoutFlow.first()).retaining(rowsById.keys)
        if (open.isEmpty()) {
            open.addAll(snap.open)
            pinnedId = snap.pinned
            presetKey = snap.preset.key
            growOnFocus = snap.growOnFocus
            page = 0
        }
        weights.putAll(snap.weights.filterKeys { it in open })
        restored = true
    }
    // Değişiklikleri kaydet (sürüklerken sık yazmamak için kısa bir bekleyişle).
    LaunchedEffect(restored) {
        if (!restored) return@LaunchedEffect
        snapshotFlow {
            WorkspaceCodec.encode(WorkspaceSnapshot(open.toList(), weights.toMap(), pinnedId, LayoutPreset.fromKey(presetKey), growOnFocus))
        }.distinctUntilChanged().drop(1).debounce(400).collect { store.setWorkspaceLayout(it) }
    }
    // Bildirim rozeti: görünmeyen bir sayfadaki AI yanıtı bitirince o sayfanın noktasında işaret çıkar.
    val monitorEntries by container.webMonitor.entries.collectAsStateWithLifecycle()
    val prevPhases = remember { mutableMapOf<Long, WebPhase>() }
    LaunchedEffect(monitorEntries) {
        val now = HashMap<Long, WebPhase>()
        monitorEntries.filter { it.isLive }.forEach { now.putIfAbsent(it.accountId, it.phase) } // en yeni oturum öncelikli
        val visible = pagedItems(open.toList(), pinnedId, page, perPage)
        finishedAccounts(prevPhases, now).forEach { id ->
            if (id in open && id !in visible && id !in unseen) unseen.add(id)
        }
        prevPhases.clear()
        prevPhases.putAll(now)
    }
    // Sayfa görününce o sayfadaki (ve artık açık olmayan) rozetler temizlenir.
    LaunchedEffect(page, open.toList(), pinnedId, perPage) {
        val visible = pagedItems(open.toList(), pinnedId, page, perPage)
        unseen.removeAll { it in visible || it !in open }
    }
    // Ekrandan çıkınca bu ekranın açtığı tüm oturumlar kapanır (çerezler kaydedilir).
    DisposableEffect(Unit) { onDispose { open.toList().forEach { hub.release(it) } } }

    BackHandler(enabled = maximizedId != null) { maximizedId = null }
    // Dosya seçici bütün pencereler için TEK kez kurulur.
    WebFileChooserEffect()

    val pages = pagesWithPin(open.toList(), pinnedId, perPage)
    val safePage = page.coerceIn(0, pages - 1)
    val effPin = effectivePin(open.toList(), pinnedId, perPage)
    val badged = badgedPages(open.toList(), pinnedId, unseen.toSet(), perPage)
    val effFocus = focusedId?.takeIf { it in open }

    Column(Modifier.fillMaxSize().imePadding()) {
        WorkspaceTopBar(
            rows = rows,
            openIds = open,
            page = safePage,
            pages = pages,
            growOnFocus = growOnFocus,
            preset = preset,
            showPresets = perPage > 1,
            onPreset = { p ->
                presetKey = p.key
                weights.clear()
            },
            onBack = onBack,
            onToggle = ::togglePane,
            onPrev = { page = (safePage - 1).coerceAtLeast(0) },
            onNext = { page = (safePage + 1).coerceAtMost(pages - 1) },
            onGrowChange = { growOnFocus = it },
            onReset = { weights.clear() },
        )
        HorizontalDivider(color = KriptonTheme.colors.nodeBorder.copy(alpha = 0.5f))

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
            if (open.isEmpty()) {
                EmptyWorkspace()
            } else {
                AnimatedContent(
                    targetState = safePage,
                    transitionSpec = {
                        if (!animations) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            val dir = if (targetState > initialState) 1 else -1
                            (
                                fadeIn(tween(240, delayMillis = 80)) +
                                    slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { dir * it / 5 }
                                ) togetherWith (
                                fadeOut(tween(120)) +
                                    slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -dir * it / 5 }
                                )
                        }
                    },
                    label = "workspacePage",
                ) { pg ->
                    val items = pagedItems(open.toList(), pinnedId, pg, perPage)
                    val boost = when {
                        effFocus == null -> 1f
                        typing -> TYPING_BOOST
                        growOnFocus -> FOCUS_BOOST
                        else -> 1f
                    }
                    PageGrid(
                        items = items,
                        rowsById = rowsById,
                        weights = weights,
                        focusId = effFocus,
                        maximizedId = maximizedId,
                        boost = boost,
                        sidebar = sidebar,
                        pinnedId = effPin,
                        dragging = dragging,
                        animations = animations,
                        container = container,
                        onFocus = { focusedId = it },
                        onClose = ::closePane,
                        onToggleMax = { id -> maximizedId = if (maximizedId == id) null else id },
                        onTogglePin = ::togglePin,
                        onSwap = ::swapPanes,
                        onDragState = { dragging = it },
                        onOpenMonitor = onOpenMonitor,
                    )
                }
            }
        }
        if (pages > 1) {
            PageDots(count = pages, current = safePage, badged = badged, onPick = { page = it })
        }
    }
}

@Composable
private fun WorkspaceTopBar(
    rows: List<AiAccount>,
    openIds: List<Long>,
    page: Int,
    pages: Int,
    growOnFocus: Boolean,
    preset: LayoutPreset,
    showPresets: Boolean,
    onPreset: (LayoutPreset) -> Unit,
    onBack: () -> Unit,
    onToggle: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onGrowChange: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                stringResource(R.string.ws_title),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = growOnFocus,
                onClick = { onGrowChange(!growOnFocus) },
                label = { Text(stringResource(R.string.ws_grow_focus), maxLines = 1) },
            )
            if (showPresets) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { menu = true }) { Text(stringResource(R.string.ws_presets), maxLines = 1) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        listOf(
                            LayoutPreset.EQUAL to R.string.ws_preset_equal,
                            LayoutPreset.SIDEBAR to R.string.ws_preset_sidebar,
                        ).forEach { (p, label) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(label)) },
                                leadingIcon = {
                                    if (p == preset) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    menu = false
                                    onPreset(p)
                                },
                            )
                        }
                    }
                }
            }
            TextButton(onClick = onReset) { Text(stringResource(R.string.ws_reset), maxLines = 1) }
            if (pages > 1) {
                IconButton(onClick = onPrev, enabled = page > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.ws_prev))
                }
                Text(stringResource(R.string.ws_page, page + 1, pages), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = onNext, enabled = page < pages - 1) {
                    Text(stringResource(R.string.ws_next), maxLines = 1)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rows, key = { it.id }) { a ->
                val provider = a.webProvider() ?: return@items
                FilterChip(
                    selected = a.id in openIds,
                    onClick = { onToggle(a.id) },
                    label = { Text(a.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Avatar(provider, 22.dp) },
                )
            }
        }
    }
}

@Composable
private fun EmptyWorkspace() {
    val k = KriptonTheme.colors
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Email, contentDescription = null, tint = k.muted, modifier = Modifier.size(56.dp))
        Text(
            stringResource(R.string.ws_empty),
            style = MaterialTheme.typography.titleMedium,
            color = k.muted,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun PageDots(count: Int, current: Int, badged: Set<Int>, onPick: (Int) -> Unit) {
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == current) 22.dp else 8.dp, tween(220), label = "dotW")
            val badge = i in badged
            val label = if (badge) stringResource(R.string.ws_page_new_reply, i + 1) else stringResource(R.string.ws_page_n, i + 1)
            Box(Modifier.padding(horizontal = 3.dp).semantics { contentDescription = label }) {
                Box(
                    Modifier.width(w).height(8.dp).clip(CircleShape)
                        .background(if (i == current) k.arrow else k.nodeBorder)
                        .clickable { onPick(i) },
                )
                // Başka sayfadaki bir AI yanıtını bitirdi: sayfa noktasının köşesinde küçük işaret.
                if (badge) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-4).dp).size(9.dp)
                            .clip(CircleShape).background(scheme.tertiary)
                            .border(1.dp, scheme.surface, CircleShape),
                    )
                }
            }
        }
    }
}

/** Bir sayfadaki (en çok 4) pencereyi, [layoutPane] sonucuna göre animasyonlu yerleştirir. */
@Composable
private fun PageGrid(
    items: List<Long>,
    rowsById: Map<Long, AiAccount>,
    weights: SnapshotStateMap<Long, PaneWeight>,
    focusId: Long?,
    maximizedId: Long?,
    boost: Float,
    sidebar: Boolean,
    pinnedId: Long?,
    dragging: Boolean,
    animations: Boolean,
    container: AppContainer,
    onFocus: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onToggleMax: (Long) -> Unit,
    onTogglePin: (Long) -> Unit,
    onSwap: (Long, Long) -> Unit,
    onDragState: (Boolean) -> Unit,
    onOpenMonitor: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val gapPx = with(density) { GAP_DP.dp.toPx() }
        val focusIdx = items.indexOf(focusId).takeIf { it >= 0 }
        val boxes = layoutPane(
            count = items.size,
            width = wPx,
            height = hPx,
            weights = items.map { weights[it] ?: PaneWeight() },
            focus = focusIdx,
            focusBoost = boost,
            gap = gapPx,
            sidebar = sidebar,
        )
        items.forEachIndexed { i, id ->
            key(id) {
                val isMax = maximizedId == id
                // Büyütülmüş pencere varken diğerleri çizilmez (WebView'ları yalnız geri alınır, sayfa korunur).
                if (maximizedId == null || isMax) {
                    WorkspacePane(
                        accountId = id,
                        account = rowsById[id],
                        container = container,
                        target = if (isMax) PaneBox(0f, 0f, wPx, hPx) else boxes.getOrElse(i) { PaneBox(0f, 0f, wPx, hPx) },
                        animate = animations && !dragging,
                        focused = focusId == id,
                        maximized = isMax,
                        pinned = pinnedId == id,
                        canResize = items.size > 1 && !isMax,
                        canReorder = items.size > 1 && !isMax && pinnedId != id,
                        onFocus = { onFocus(id) },
                        onClose = { onClose(id) },
                        onToggleMax = { onToggleMax(id) },
                        onTogglePin = { onTogglePin(id) },
                        onReorderDrop = { cx, cy ->
                            val t = paneAt(boxes, cx, cy)
                            if (t != null && t != i) items.getOrNull(t)?.let { onSwap(id, it) }
                        },
                        onResizeStart = { onDragState(true) },
                        onResize = { dx, dy ->
                            val horizontal = wPx >= hPx
                            val dMain = if (horizontal) dx / wPx else dy / hPx
                            val dCross = if (horizontal) dy / hPx else dx / wPx
                            val current = items.map { weights[it] ?: PaneWeight() }
                            val updated = resizePane(items.size, current, i, dMain, dCross, sidebar)
                            items.forEachIndexed { j, pid -> weights[pid] = updated[j] }
                        },
                        onResizeEnd = { onDragState(false) },
                        onOpenMonitor = onOpenMonitor,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspacePane(
    accountId: Long,
    account: AiAccount?,
    container: AppContainer,
    target: PaneBox,
    animate: Boolean,
    focused: Boolean,
    maximized: Boolean,
    pinned: Boolean,
    canResize: Boolean,
    canReorder: Boolean,
    onFocus: () -> Unit,
    onClose: () -> Unit,
    onToggleMax: () -> Unit,
    onTogglePin: () -> Unit,
    onReorderDrop: (Float, Float) -> Unit,
    onResizeStart: () -> Unit,
    onResize: (Float, Float) -> Unit,
    onResizeEnd: () -> Unit,
    onOpenMonitor: () -> Unit,
) {
    val hub = container.webChatHub
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val provider = account?.webProvider()
    val tint = provider?.tint() ?: k.arrow
    val state by hub.state(accountId).collectAsStateWithLifecycle()
    val entries by container.webMonitor.entries.collectAsStateWithLifecycle()

    // Konum/boyut animasyonu; sürüklerken anlık (parmağı geriden izlemesin).
    val rect by animateRectAsState(
        targetValue = Rect(target.x, target.y, target.x + target.w, target.y + target.h),
        animationSpec = if (animate) tween<Rect>(300, easing = FastOutSlowInEasing) else snap<Rect>(),
        label = "paneRect",
    )
    // Pencere ilk eklendiğinde yumuşakça belirir.
    val appear = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (appear.value < 1f) appear.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }

    val borderColor by animateColorAsState(if (focused) tint else k.nodeBorder, tween(200), label = "paneBorder")
    val shape = RoundedCornerShape(14.dp)
    val onFocusNow by rememberUpdatedState(onFocus)
    val onToggleMaxNow by rememberUpdatedState(onToggleMax)
    val onDropNow by rememberUpdatedState(onReorderDrop)
    val onDragStateStart by rememberUpdatedState(onResizeStart)
    val onDragStateEnd by rememberUpdatedState(onResizeEnd)
    // Başlıktan sürükleyerek yer değiştirme: pencere parmağı izler, bırakılınca üstüne gelen pencereyle yer değiştirir.
    var reordering by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .offset { IntOffset((rect.left + dragOffset.x).roundToInt(), (rect.top + dragOffset.y).roundToInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .zIndex(
                if (reordering) {
                    3f
                } else if (focused || maximized) {
                    2f
                } else {
                    0f
                },
            )
            .graphicsLayer {
                alpha = appear.value * (if (reordering) 0.92f else 1f)
                val s = (0.94f + 0.06f * appear.value) * (if (reordering) 1.02f else 1f)
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(scheme.surface)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Başlık: AI adı, durum, yenile / büyüt / kapat.
            Row(
                Modifier.fillMaxWidth().height(40.dp)
                    .background(if (focused) tint.copy(alpha = 0.14f) else Color.Transparent)
                    // Çift dokunuş: tam ekran / geri al (düğmeler kendi dokunuşlarını alır).
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onToggleMaxNow() }) }
                    .pointerInput(canReorder) {
                        if (!canReorder) return@pointerInput
                        detectDragGestures(
                            onDragStart = {
                                dragOffset = Offset.Zero
                                reordering = true
                                onFocusNow()
                                onDragStateStart()
                            },
                            onDragEnd = {
                                val c = Offset(rect.center.x + dragOffset.x, rect.center.y + dragOffset.y)
                                reordering = false
                                dragOffset = Offset.Zero
                                onDropNow(c.x, c.y)
                                onDragStateEnd()
                            },
                            onDragCancel = {
                                reordering = false
                                dragOffset = Offset.Zero
                                onDragStateEnd()
                            },
                        ) { change, drag ->
                            change.consume()
                            dragOffset += drag
                        }
                    }
                    .padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (provider != null) {
                    Avatar(provider, 24.dp)
                    Box(Modifier.size(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        account?.displayName.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val dot = when (state) {
                    is WebChatHub.State.Ready -> k.arrow
                    is WebChatHub.State.Failed -> k.danger
                    else -> k.muted
                }
                Box(Modifier.padding(horizontal = 6.dp).size(8.dp).clip(CircleShape).background(dot))
                if (account != null && provider != null && state !is WebChatHub.State.Opening) {
                    IconButton(onClick = { hub.reopen(account) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.web_chats_reload), modifier = Modifier.size(18.dp))
                    }
                }
                if (!maximized) {
                    val pinDesc = stringResource(if (pinned) R.string.ws_unpin else R.string.ws_pin)
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(34.dp)) {
                        PinGlyph(pinned = pinned, tint = if (pinned) tint else scheme.onSurface, desc = pinDesc)
                    }
                }
                IconButton(onClick = onToggleMax, modifier = Modifier.size(34.dp)) {
                    MaxGlyph(restore = maximized, tint = scheme.onSurface)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ws_close), modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(color = k.nodeBorder.copy(alpha = 0.5f))

            Box(
                Modifier.weight(1f).fillMaxWidth()
                    // Dokunma sayfaya aynen geçer (tüketilmez); yalnız bu pencere "odaklı" olur ve büyür.
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                if (e.changes.any { it.pressed && !it.previousPressed }) onFocusNow()
                            }
                        }
                    },
            ) {
                val s = state
                when {
                    account == null || provider == null -> Notice(stringResource(R.string.web_chats_no_account), null, null)

                    s is WebChatHub.State.Failed -> Notice(
                        stringResource(R.string.web_chats_failed),
                        stringResource(R.string.web_chats_retry),
                    ) { hub.reopen(account) }

                    s is WebChatHub.State.Ready -> {
                        val entry = entries.firstOrNull { it.id == s.entryId && it.isLive }
                        when {
                            entry == null -> Notice(
                                stringResource(R.string.web_chats_closed),
                                stringResource(R.string.web_chats_retry),
                            ) { hub.reopen(account) }

                            s.borrowed -> Notice(
                                stringResource(R.string.web_chats_busy),
                                stringResource(R.string.web_chats_open_monitor),
                                onOpenMonitor,
                            )

                            else -> ChatPage(
                                handle = container.webMonitor.handle(entry.id),
                                installFileChooser = false,
                                onFocus = onFocus,
                            )
                        }
                    }

                    else -> Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        com.kriptoncod.app.ui.components.KriptonLoader()
                        Text(
                            stringResource(R.string.web_chats_opening),
                            style = MaterialTheme.typography.bodySmall,
                            color = k.muted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                if (canResize) {
                    ResizeHandle(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                        tint = if (focused) tint else k.muted,
                        onStart = onResizeStart,
                        onDelta = onResize,
                        onEnd = onResizeEnd,
                    )
                }
            }
        }
    }
}

/** Sağ-alt köşe tutamacı: sürüklenince pencere büyür/küçülür (sekme/pencere kenarı gibi). */
@Composable
private fun ResizeHandle(modifier: Modifier, tint: Color, onStart: () -> Unit, onDelta: (Float, Float) -> Unit, onEnd: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val desc = stringResource(R.string.ws_resize)
    val startNow by rememberUpdatedState(onStart)
    val deltaNow by rememberUpdatedState(onDelta)
    val endNow by rememberUpdatedState(onEnd)
    Box(
        modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surface.copy(alpha = 0.78f))
            .semantics { contentDescription = desc }
            .pointerInput(desc) {
                detectDragGestures(
                    onDragStart = { startNow() },
                    onDragEnd = { endNow() },
                    onDragCancel = { endNow() },
                ) { change, drag ->
                    change.consume()
                    deltaNow(drag.x, drag.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = 2.dp.toPx()
            val s = size.minDimension
            for (f in listOf(0.35f, 0.65f, 0.95f)) {
                drawLine(tint, Offset(s * f, s), Offset(s, s * f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
    }
}

/** Büyüt / geri al simgesi (çekirdek simge kümesinde bulunmadığı için çizilir). */
@Composable
private fun MaxGlyph(restore: Boolean, tint: Color) {
    Canvas(Modifier.size(16.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx())
        val s = size.minDimension
        if (!restore) {
            drawRect(tint, topLeft = Offset(s * 0.1f, s * 0.1f), size = androidx.compose.ui.geometry.Size(s * 0.8f, s * 0.8f), style = stroke)
        } else {
            drawRect(tint, topLeft = Offset(s * 0.28f, s * 0.05f), size = androidx.compose.ui.geometry.Size(s * 0.66f, s * 0.66f), style = stroke)
            drawRect(tint, topLeft = Offset(s * 0.06f, s * 0.3f), size = androidx.compose.ui.geometry.Size(s * 0.66f, s * 0.66f), style = stroke)
        }
    }
}

/** Sabitleme simgesi (çekirdek simge kümesinde yok): dolu = sabit, boş = sabit değil. */
@Composable
private fun PinGlyph(pinned: Boolean, tint: Color, desc: String) {
    Canvas(Modifier.size(16.dp).semantics { contentDescription = desc }) {
        val s = size.minDimension
        val stroke = 1.8.dp.toPx()
        drawCircle(
            tint,
            radius = s * 0.24f,
            center = Offset(s * 0.5f, s * 0.3f),
            style = if (pinned) Fill else Stroke(width = stroke),
        )
        drawLine(tint, Offset(s * 0.5f, s * 0.54f), Offset(s * 0.5f, s * 0.95f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
