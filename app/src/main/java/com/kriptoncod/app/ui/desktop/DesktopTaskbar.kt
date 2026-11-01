package com.kriptoncod.app.ui.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.theme.KriptonTheme
import com.kriptoncod.app.ui.theme.LocalMotionScale
import com.kriptoncod.app.ui.theme.Motion

/** Bir uygulamanın tek penceresi (çubuktaki pencere kartı): [label] kartta yazan ad, [active] görünen pencere mi. */
@Immutable
class DockTab(val id: Int, val label: String, val active: Boolean)

/**
 * Alt çubuktaki bir uygulama. [tabId] null ise uygulama yalnız sabit (açık değil); [active] görünen sekme bu uygulamada
 * demek. [tabs]: uygulamanın pencereleri; birden çoksa öğe bir GRUP olarak çizilir (sayı rozeti). [multi]: uygulama yeni
 * pencere açabilir mi.
 */
@Immutable
class DockItem(
    val info: AppInfo,
    val tabId: Int?,
    val pinned: Boolean,
    val active: Boolean,
    val tabs: List<DockTab> = emptyList(),
    val multi: Boolean = false,
) {
    val open: Boolean get() = tabId != null
    val grouped: Boolean get() = tabs.size > 1
}

internal val DOCK_HEIGHT = 60.dp

/**
 * Alt çubuk (Linux başlatıcısındaki dock/panel gibi): soldan uygulamalar düğmesi, sabitlenmiş uygulamalar ve açık sekmeler,
 * sağda masaüstünü göster / tam ekran / saat. Dokununca uygulama sekme olarak açılır ya da öne gelir; uzun basış ya da
 * sağ tık menü açar (pencere kartları, yenile, yeni pencere, sabitle, kapat, tümünü kapat). Çalışan uygulamanın altında
 * gösterge çizgisi durur (görünende uzun); birden çok penceresi olan uygulamada sayı rozeti çıkar.
 *
 * @param dropHoverApp dosya sürüklenirken parmağın altındaki AI öğesi (vurgulanır).
 * @param onBounds öğelerin ekrandaki yeri (dosyayı AI sekmesine bırakmak için); null = öğe artık yok.
 */
@Composable
internal fun Dock(
    items: List<DockItem>,
    launcherOpen: Boolean,
    desktopShown: Boolean,
    clock: String,
    fullscreen: Boolean,
    showLabels: Boolean,
    showClock: Boolean,
    dropHoverApp: String?,
    hasOpenTabs: Boolean,
    onLauncher: () -> Unit,
    onItem: (DockItem) -> Unit,
    onClose: (DockItem) -> Unit,
    onTogglePin: (DockItem) -> Unit,
    onMovePin: (DockItem, Int) -> Unit,
    onReload: (DockItem) -> Unit,
    onTabActivate: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    onNewWindow: (DockItem) -> Unit,
    onCloseApp: (DockItem) -> Unit,
    onCloseAll: () -> Unit,
    onShowDesktop: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onBounds: (String, Rect?) -> Unit,
) {
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme
    val launcherText = stringResource(R.string.desktop_start)
    val motion = LocalMotionScale.current
    var desktopMenu by remember { mutableStateOf(false) }
    Column {
        HorizontalDivider(thickness = 1.5.dp, color = k.nodeBorder)
        Row(
            Modifier.fillMaxWidth().height(DOCK_HEIGHT).background(scheme.surface).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (launcherOpen) k.nodeFill else Color.Transparent)
                    .semantics { contentDescription = launcherText }
                    .clickable(onClick = onLauncher),
                contentAlignment = Alignment.Center,
            ) { AppsGlyph(scheme.primary, 26.dp) }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(1.dp).height(30.dp).background(k.nodeBorder))
            LazyRow(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(items, key = { it.info.app }) { item ->
                    DockChip(
                        item = item,
                        hovered = dropHoverApp == item.info.app,
                        showLabel = showLabels,
                        onClick = { onItem(item) },
                        onClose = { onClose(item) },
                        onTogglePin = { onTogglePin(item) },
                        onMovePin = { delta -> onMovePin(item, delta) },
                        onReload = { onReload(item) },
                        onTabActivate = onTabActivate,
                        onTabClose = onTabClose,
                        onNewWindow = { onNewWindow(item) },
                        onCloseApp = { onCloseApp(item) },
                        onBounds = onBounds,
                        // Çubuğa giren/çıkan/yer değiştiren öğeler yumuşakça kayar (animasyonlar kapalıysa anında).
                        modifier = if (motion.enabled) {
                            Modifier.animateItem(
                                fadeInSpec = motion.spec(Motion.MEDIUM),
                                placementSpec = motion.spec(Motion.MEDIUM),
                                fadeOutSpec = motion.spec(Motion.SHORT),
                            )
                        } else {
                            Modifier
                        },
                    )
                }
            }
            // Masaüstünü göster; uzun basış / sağ tık: bütün sekmeleri kapat.
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .onSecondaryClick { desktopMenu = true }
                    .combinedClickable(onClick = onShowDesktop, onLongClick = { desktopMenu = true }),
                contentAlignment = Alignment.Center,
            ) {
                MonitorGlyph(if (desktopShown) scheme.primary else k.muted)
                DropdownMenu(expanded = desktopMenu, onDismissRequest = { desktopMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.desktop_menu_show_desktop)) },
                        onClick = {
                            desktopMenu = false
                            onShowDesktop()
                        },
                    )
                    if (hasOpenTabs) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.desktop_close_all_tabs)) },
                            onClick = {
                                desktopMenu = false
                                onCloseAll()
                            },
                        )
                    }
                }
            }
            IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(46.dp)) {
                FullscreenGlyph(if (fullscreen) scheme.primary else k.muted)
            }
            if (showClock) Text(clock, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}

/**
 * Çubuktaki öğe. Açık uygulama sekme gibi görünür (simge + ad; görünen sekmede kapatma düğmesi); yalnız sabit olan uygulama
 * yalnız simgedir. Birden çok penceresi varsa köşede sayı rozeti çıkar. Uzun basış / sağ tık menüsü: pencere kartları,
 * yenile, yeni pencere, sabitle/kaldır, sola/sağa taşı, kapat / tümünü kapat.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockChip(
    item: DockItem,
    hovered: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onTogglePin: () -> Unit,
    onMovePin: (Int) -> Unit,
    onReload: () -> Unit,
    onTabActivate: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    onNewWindow: () -> Unit,
    onCloseApp: () -> Unit,
    onBounds: (String, Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val k = KriptonTheme.colors
    val motion = LocalMotionScale.current
    val shape = RoundedCornerShape(12.dp)
    val tint = item.info.tint
    val app = item.info.app
    var menu by remember { mutableStateOf(false) }
    val boundsNow by rememberUpdatedState(onBounds)
    DisposableEffect(app) { onDispose { boundsNow(app, null) } }

    // Arka plan ve gösterge, motion belirteçleriyle yumuşakça değişir.
    val fill by animateColorAsState(
        targetValue = when {
            hovered -> tint.copy(alpha = 0.28f)
            item.active -> k.nodeFill
            else -> Color.Transparent
        },
        animationSpec = motion.spec(Motion.SHORT),
        label = "dockChipFill",
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (item.active) 24.dp else 8.dp,
        animationSpec = motion.spec(Motion.MEDIUM),
        label = "dockIndicator",
    )

    Box(
        modifier
            .height(48.dp)
            .onGloballyPositioned { boundsNow(app, it.boundsInRoot()) }
            .clip(shape)
            .background(fill)
            .then(if (item.active || hovered) Modifier.border(1.5.dp, if (hovered) tint else k.nodeBorder, shape) else Modifier)
            .onSecondaryClick { menu = true }
            .combinedClickable(onClick = onClick, onLongClick = { menu = true }),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp).align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopAppIcon(item.info, 28.dp)
            if (item.open && showLabel) {
                Spacer(Modifier.width(8.dp))
                Text(
                    item.info.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
            }
            if (item.open && item.active) {
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier.size(26.dp).clip(CircleShape).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { CloseGlyph(k.muted, 12.dp) }
            }
        }
        // Çalışan uygulama göstergesi (görünen sekmede uzun).
        if (item.open) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .width(indicatorWidth)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tint),
            )
        }
        // Grup rozeti: aynı uygulamanın pencere sayısı.
        if (item.grouped) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(tint),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.tabs.size.coerceAtMost(9).toString() + if (item.tabs.size > 9) "+" else "",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            // Pencere önizlemeleri: uygulamanın açık pencereleri küçük kartlar olarak; karta dokun = o pencereye geç.
            if (item.open) {
                Row(
                    Modifier
                        .widthIn(max = 260.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item.tabs.forEach { tab ->
                        TabCard(
                            tab = tab,
                            info = item.info,
                            onActivate = {
                                menu = false
                                onTabActivate(tab.id)
                            },
                            onClose = {
                                // Son pencere kapanınca menü de kapanır; diğer durumda açık kalır ve kart kaybolur.
                                if (item.tabs.size <= 1) menu = false
                                onTabClose(tab.id)
                            },
                        )
                    }
                }
                HorizontalDivider(color = k.nodeBorder.copy(alpha = 0.4f))
            }
            if (item.open && item.info.provider != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.desktop_tab_reload)) },
                    onClick = {
                        menu = false
                        onReload()
                    },
                )
            }
            if (item.multi) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.desktop_tab_new_window)) },
                    onClick = {
                        menu = false
                        onNewWindow()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (item.pinned) R.string.desktop_unpin else R.string.desktop_pin)) },
                onClick = {
                    menu = false
                    onTogglePin()
                },
            )
            if (item.pinned) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.desktop_move_left)) },
                    onClick = {
                        menu = false
                        onMovePin(-1)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.desktop_move_right)) },
                    onClick = {
                        menu = false
                        onMovePin(1)
                    },
                )
            }
            if (item.open) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (item.grouped) R.string.desktop_tab_close_all else R.string.desktop_tab_close)) },
                    onClick = {
                        menu = false
                        if (item.grouped) onCloseApp() else onClose()
                    },
                )
            }
        }
    }
}

/**
 * Bir pencerenin küçük kartı: renkli üst şerit + uygulama simgesi, altında ad; köşede kapatma. Görünen pencerenin çerçevesi
 * uygulama renginde. (Canlı küçük görüntü yok: WebView/PDF gibi içerikleri çizmek pahalı ve kararsızdır; kart ad + simgedir.)
 */
@Composable
private fun TabCard(tab: DockTab, info: AppInfo, onActivate: () -> Unit, onClose: () -> Unit) {
    val k = KriptonTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val switchText = stringResource(R.string.desktop_tab_switch_to, tab.label)
    val closeText = stringResource(R.string.desktop_tab_close)
    Column(
        Modifier
            .width(92.dp)
            .clip(shape)
            .background(k.nodeFill)
            .border(if (tab.active) 1.5.dp else 1.dp, if (tab.active) info.tint else k.nodeBorder, shape)
            .semantics { contentDescription = switchText }
            .clickable(onClick = onActivate),
    ) {
        Box(
            Modifier.fillMaxWidth().height(44.dp).background(info.tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            DesktopAppIcon(info, 26.dp)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .semantics { contentDescription = closeText }
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { CloseGlyph(k.muted, 10.dp) }
        }
        Text(
            tab.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

/**
 * Alt çubuk gizliyken alt kenarda duran ince tutamaç: dokun ya da yukarı kaydır -> çubuk geçici açılır (birkaç sn sonra yine gizlenir).
 */
@Composable
internal fun DockHandle(onReveal: () -> Unit, modifier: Modifier = Modifier) {
    val k = KriptonTheme.colors
    val reveal = stringResource(R.string.desktop_dock_reveal)
    Box(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics { contentDescription = reveal }
            .clickable(onClick = onReveal)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, drag -> if (drag < -4f) onReveal() }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.padding(bottom = 6.dp).width(72.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(k.muted.copy(alpha = 0.6f)))
    }
}
