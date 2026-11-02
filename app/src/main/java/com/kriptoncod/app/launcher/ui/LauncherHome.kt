package com.kriptoncod.app.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.R
import com.kriptoncod.app.data.web.WebChatHub
import com.kriptoncod.app.launcher.LauncherPrefs
import com.kriptoncod.app.launcher.home.HomeItem
import com.kriptoncod.app.launcher.home.HomeRow
import com.kriptoncod.app.launcher.home.HomeRows
import com.kriptoncod.app.launcher.home.HomeState
import com.kriptoncod.app.launcher.home.Home
import com.kriptoncod.app.launcher.home.PanelKind
import com.kriptoncod.app.launcher.widgets.HostedWidget
import com.kriptoncod.app.ui.desktop.AppInfo
import com.kriptoncod.app.ui.desktop.DesktopApps
import com.kriptoncod.app.ui.desktop.DesktopAppIcon
import com.kriptoncod.app.ui.theme.KriptonTheme

/** Ana ekranın dışarıya açtığı eylemler (kabuk uygular). */
internal class HomeActions(
    val open: (String) -> Unit,
    val change: (HomeState) -> Unit,
    val togglePin: (String) -> Unit,
    val openDrawer: () -> Unit,
    val openNotifications: () -> Unit,
    val openSearch: () -> Unit,
    val openAsk: (Long?) -> Unit,
    val ask: (Long, String) -> Unit,
    val addWidget: () -> Unit,
    val openSettings: () -> Unit,
    val removeWidget: (Int) -> Unit,
)

/**
 * Ana ekran: sayfalar (uygulama kısayolları, Kripton panoları, Android widget'ları), üstte arama + bildirim, altta AI hızlı erişim
 * çubuğu. Yukarı kaydır: çekmece; aşağı kaydır: bildirim paneli; boşluğa uzun bas: ana ekran menüsü.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LauncherHome(
    container: AppContainer,
    state: HomeState,
    prefs: LauncherPrefs,
    clock: String,
    date: String,
    infoOf: (String) -> AppInfo?,
    openApps: Set<String>,
    pinned: Set<String>,
    badgeOf: (String) -> Int,
    ai: List<AppInfo>,
    unread: Int,
    wallpaper: ImageBitmap?,
    actions: HomeActions,
) {
    val scheme = MaterialTheme.colorScheme
    val pager = rememberPagerState(pageCount = { state.pageCount })
    var menu by remember { mutableStateOf(false) }
    val cols = prefs.homeColumns

    val conn = remember(prefs.gestureSwipeUp, prefs.gestureSwipeDown) {
        object : NestedScrollConnection {
            var acc = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                acc += available.y
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val a = acc
                acc = 0f
                if (prefs.gestureSwipeUp && (a < -120f || available.y < -1800f)) {
                    actions.openDrawer()
                } else if (prefs.gestureSwipeDown && (a > 120f || available.y > 1800f)) {
                    actions.openNotifications()
                }
                return Velocity.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (wallpaper != null) {
            Image(wallpaper, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = prefs.wallpaperDim)))
        }
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(conn)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { menu = true }) },
        ) {
            // Üst şerit: arama hapı + bildirim zili
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(scheme.surface.copy(alpha = 0.85f))
                        .clickable(onClick = actions.openSearch)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, Modifier.size(20.dp), tint = KriptonTheme.colors.muted)
                    Spacer(Modifier.size(10.dp))
                    Text(stringResource(R.string.l_search_hint), style = MaterialTheme.typography.bodyMedium, color = KriptonTheme.colors.muted, maxLines = 1)
                }
                Spacer(Modifier.size(8.dp))
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(scheme.surface.copy(alpha = 0.85f)).clickable(onClick = actions.openNotifications),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Notifications, stringResource(R.string.l_notif_title), Modifier.size(22.dp))
                    CountBadge(unread, Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp))
                }
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                val items = state.pages.getOrNull(page)?.items.orEmpty()
                val rows = remember(items, cols) { HomeRows.build(items, cols) }
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.l_home_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = KriptonTheme.colors.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(rows, key = { r -> rowKey(r) }) { row ->
                            when (row) {
                                is HomeRow.Apps -> AppsRow(row, cols, prefs, infoOf, openApps, pinned, badgeOf, state, page, actions)
                                is HomeRow.Single -> SingleItem(row.item, container, state, clock, date, ai, actions, infoOf)
                            }
                        }
                    }
                }
            }

            if (state.pageCount > 1) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(state.pageCount) { i ->
                        Box(
                            Modifier.padding(3.dp).size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                                .background(if (i == pager.currentPage) scheme.primary else KriptonTheme.colors.muted.copy(alpha = 0.5f)),
                        )
                    }
                }
            }

            AiRail(container, ai, actions)
        }

        HomeMenu(menu, { menu = false }, actions)
    }
}

private fun rowKey(r: HomeRow): String = when (r) {
    is HomeRow.Apps -> "apps:" + r.apps.joinToString(",") { it.app }
    is HomeRow.Single -> r.item.key
}

@Composable
private fun HomeMenu(show: Boolean, dismiss: () -> Unit, a: HomeActions) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DropdownMenu(expanded = show, onDismissRequest = dismiss) {
            DropdownMenuItem(text = { Text(stringResource(R.string.l_menu_widget)) }, onClick = {
                dismiss()
                a.addWidget()
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.l_menu_apps)) }, onClick = {
                dismiss()
                a.openDrawer()
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.l_menu_settings)) }, onClick = {
                dismiss()
                a.openSettings()
            })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppsRow(
    row: HomeRow.Apps,
    cols: Int,
    prefs: LauncherPrefs,
    infoOf: (String) -> AppInfo?,
    openApps: Set<String>,
    pinned: Set<String>,
    badgeOf: (String) -> Int,
    state: HomeState,
    page: Int,
    actions: HomeActions,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        for (i in 0 until cols) {
            val item = row.apps.getOrNull(i)
            val info = item?.let { infoOf(it.app) }
            if (item == null || info == null) {
                Spacer(Modifier.weight(1f))
            } else {
                var menu by remember(item.app) { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .combinedClickable(onClick = { actions.open(item.app) }, onLongClick = { menu = true })
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BadgedIcon(info, (52 * prefs.iconScale).dp, if (prefs.badges) badgeOf(item.app) else 0)
                        if (prefs.showLabels) {
                            Text(
                                info.title,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
                            )
                        }
                        Box(Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(if (item.app in openApps) info.tint else Color.Transparent))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(if (item.app in pinned) R.string.l_unpin_dock else R.string.l_pin_dock)) }, onClick = {
                            menu = false
                            actions.togglePin(item.app)
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.l_move_earlier)) }, onClick = {
                            menu = false
                            actions.change(Home.move(state, item.key, -1))
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.l_move_later)) }, onClick = {
                            menu = false
                            actions.change(Home.move(state, item.key, 1))
                        })
                        if (state.pageCount > 1) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.l_move_page)) },
                                onClick = {
                                    menu = false
                                    actions.change(Home.moveToPage(state, item.key, (page + 1) % state.pageCount))
                                },
                            )
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.l_remove_home)) }, onClick = {
                            menu = false
                            actions.change(Home.remove(state, item.key))
                        })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SingleItem(
    item: HomeItem,
    container: AppContainer,
    state: HomeState,
    clock: String,
    date: String,
    ai: List<AppInfo>,
    actions: HomeActions,
    infoOf: (String) -> AppInfo?,
) {
    var menu by remember(item.key) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { menu = true })) {
        when (item) {
            is HomeItem.Panel -> when (item.kind) {
                PanelKind.CLOCK -> ClockPanel(clock, date)

                PanelKind.AI_ASK -> AiAskPanel(
                    ai = ai.mapNotNull { a -> DesktopApps.aiAccountId(a.app)?.let { it to a.title } },
                    onAsk = actions.ask,
                    onOpenSheet = { actions.openAsk(null) },
                )

                PanelKind.RUN_STATUS -> RunStatusPanel(container) { actions.open(DesktopApps.KRIPTON) }

                PanelKind.STORAGE -> StoragePanel { actions.open(DesktopApps.FILES) }

                PanelKind.NOTIFICATIONS -> NotificationsPanel(actions.openNotifications)
            }

            is HomeItem.Widget -> HostedWidget(container.launcher.widgets, item.widgetId, item.rows, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))

            is HomeItem.App -> infoOf(item.app)?.let { DesktopAppIcon(it, 48.dp) }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.l_move_up)) }, onClick = {
                menu = false
                actions.change(Home.move(state, item.key, -1))
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.l_move_down)) }, onClick = {
                menu = false
                actions.change(Home.move(state, item.key, 1))
            })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.l_remove_home)) },
                onClick = {
                    menu = false
                    if (item is HomeItem.Widget) actions.removeWidget(item.widgetId)
                    actions.change(Home.remove(state, item.key))
                },
            )
        }
    }
}

/** AI'lara tek dokunuşla erişim: soldaki "Sor" düğmesi hızlı soru sayfasını açar, her AI kendi sekmesini açar. */
@Composable
private fun AiRail(container: AppContainer, ai: List<AppInfo>, actions: HomeActions) {
    val scheme = MaterialTheme.colorScheme
    val k = KriptonTheme.colors
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "ask") {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(scheme.primary).clickable { actions.openAsk(null) }.padding(horizontal = 16.dp, vertical = 10.dp),
            ) { Text(stringResource(R.string.l_rail_ask), style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary) }
        }
        items(ai, key = { it.app }) { info ->
            val id = DesktopApps.aiAccountId(info.app)
            val st = if (id != null) container.webChatHub.state(id).collectAsStateWithLifecycle().value else null
            val live = st is WebChatHub.State.Ready
            var menu by remember(info.app) { mutableStateOf(false) }
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(scheme.surface.copy(alpha = 0.9f))
                        .combinedClickableCompat(onClick = { actions.open(info.app) }, onLong = { menu = true })
                        .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesktopAppIcon(info, 24.dp)
                    Spacer(Modifier.size(6.dp))
                    Text(info.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthLimit())
                    Spacer(Modifier.size(6.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (live) scheme.primary else k.muted.copy(alpha = 0.4f)))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.l_rail_open)) }, onClick = {
                        menu = false
                        actions.open(info.app)
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.l_rail_ask_this)) }, onClick = {
                        menu = false
                        actions.openAsk(id)
                    })
                }
            }
        }
    }
}

private fun Modifier.widthLimit(): Modifier = this.then(Modifier.padding(0.dp)).widthIn(max = 110.dp)

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLong: () -> Unit): Modifier = this.combinedClickable(onClick = onClick, onLongClick = onLong)
