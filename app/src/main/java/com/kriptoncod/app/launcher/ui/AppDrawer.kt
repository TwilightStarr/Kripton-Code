package com.kriptoncod.app.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.R
import com.kriptoncod.app.launcher.AppShortcut
import com.kriptoncod.app.launcher.DrawerSort
import com.kriptoncod.app.launcher.LauncherApp
import com.kriptoncod.app.launcher.LauncherPrefs
import com.kriptoncod.app.launcher.search.ContactSearch
import com.kriptoncod.app.launcher.search.SearchEngine
import com.kriptoncod.app.launcher.search.SearchInput
import com.kriptoncod.app.launcher.search.SearchResult
import com.kriptoncod.app.ui.desktop.AppInfo
import com.kriptoncod.app.ui.desktop.DesktopApps
import com.kriptoncod.app.ui.desktop.FilesAccess
import com.kriptoncod.app.ui.desktop.FileBrowse
import com.kriptoncod.app.ui.desktop.PackageIcon
import com.kriptoncod.app.ui.theme.KriptonTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Çekmece ve arama eylemleri (kabuk uygular). */
internal class DrawerActions(
    val open: (String) -> Unit,
    val addToHome: (String) -> Unit,
    val removeFromHome: (String) -> Unit,
    val togglePin: (String) -> Unit,
    val setHidden: (String, Boolean) -> Unit,
    val uninstall: (String) -> Unit,
    val appInfo: (String) -> Unit,
    val shortcut: (AppShortcut) -> Unit,
    val openFile: (File) -> Unit,
    val dial: (String) -> Unit,
    val setting: (String) -> Unit,
    val web: (String) -> Unit,
    val ask: (Long, String) -> Unit,
    val copy: (String) -> Unit,
    val setSort: (DrawerSort) -> Unit,
    val dismiss: () -> Unit,
)

private sealed interface Cell {
    data class Header(val text: String) : Cell
    data class Item(val app: LauncherApp?, val info: AppInfo) : Cell
}

/**
 * Uygulama çekmecesi + evrensel arama. Boş sorguda: Kripton, AI'lar ve alfabetik (ya da en çok kullanılan / en yeni) uygulamalar,
 * sağda harf çubuğu. Sorgu varsa: uygulamalar, Kripton, AI, hesap makinesi, dosyalar, kişiler, sistem ayarları, "AI'a sor", web.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppDrawer(
    container: AppContainer,
    prefs: LauncherPrefs,
    apps: List<LauncherApp>?,
    own: List<AppInfo>,
    ai: List<AppInfo>,
    infoOf: (String) -> AppInfo?,
    openApps: Set<String>,
    pinned: Set<String>,
    onHome: Set<String>,
    badgeOf: (String) -> Int,
    settingShortcuts: List<Pair<String, String>>,
    startFocused: Boolean,
    actions: DrawerActions,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val k = KriptonTheme.colors
    var query by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val q = query.trim()
    LaunchedEffect(startFocused) { if (startFocused) runCatching { focus.requestFocus() } }

    val unhidden = remember(apps, prefs.hiddenApps, showHidden) {
        apps.orEmpty().filter { showHidden || it.packageName !in prefs.hiddenApps }
    }
    val sorted = remember(unhidden, prefs.drawerSort, prefs.launchCounts) {
        when (prefs.drawerSort) {
            DrawerSort.ALPHA -> unhidden
            DrawerSort.MOST_USED -> unhidden.sortedWith(compareByDescending<LauncherApp> { prefs.launchCounts[it.packageName] ?: 0 }.thenBy { it.label.lowercase() })
            DrawerSort.NEWEST -> unhidden.sortedByDescending { it.installedAt }
        }
    }

    // Hızlı sonuçlar anında; dosya ve kişi sonuçları gecikmeli (arka planda).
    val quick = remember(q, apps, own, ai, settingShortcuts, prefs) {
        SearchEngine.quick(
            SearchInput(
                query = q,
                apps = apps.orEmpty(),
                own = own.map { it.app to it.title },
                ai = ai.mapNotNull { a -> DesktopApps.aiAccountId(a.app)?.let { it to a.title } },
                settings = settingShortcuts,
                prefs = prefs,
            ),
        )
    }
    var slow by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    LaunchedEffect(q, prefs.searchFiles, prefs.searchContacts) {
        slow = emptyList()
        if (q.length < 2) return@LaunchedEffect
        delay(250)
        slow = withContext(Dispatchers.IO) {
            val out = ArrayList<SearchResult>()
            if (prefs.searchFiles && FilesAccess.isGranted(context)) {
                container.launcher.files.ensure()
                container.launcher.files.search(q).forEach { out.add(SearchResult.FileHit(it.file, it.isDir)) }
            }
            if (prefs.searchContacts) out.addAll(ContactSearch.search(context, q))
            out
        }
    }

    Box(Modifier.fillMaxSize().background(scheme.background.copy(alpha = 0.97f)).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.l_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, Modifier.size(20.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.l_drawer_options)) }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        DrawerSort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(stringResource(sortLabel(s)) + if (s == prefs.drawerSort) "  ✓" else "") },
                                onClick = {
                                    sortMenu = false
                                    actions.setSort(s)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.l_show_hidden) + if (showHidden) "  ✓" else "") },
                            onClick = {
                                sortMenu = false
                                showHidden = !showHidden
                            },
                        )
                    }
                }
            }

            if (q.isNotEmpty()) {
                SearchResultList(quick + slow, infoOf, actions)
            } else {
                val suggestions = remember(apps, prefs) { SearchEngine.suggestions(apps.orEmpty(), prefs) }
                DrawerGrid(
                    cells = buildCells(prefs, sorted, own, ai, suggestions, stringResource(R.string.l_sec_kripton), stringResource(R.string.l_sec_ai), stringResource(R.string.l_sec_suggested)),
                    apps = apps,
                    loading = apps == null,
                    prefs = prefs,
                    openApps = openApps,
                    pinned = pinned,
                    onHome = onHome,
                    badgeOf = badgeOf,
                    container = container,
                    actions = actions,
                )
            }
        }
    }
}

private fun sortLabel(s: DrawerSort): Int = when (s) {
    DrawerSort.ALPHA -> R.string.l_sort_alpha
    DrawerSort.MOST_USED -> R.string.l_sort_used
    DrawerSort.NEWEST -> R.string.l_sort_new
}

private fun buildCells(
    prefs: LauncherPrefs,
    apps: List<LauncherApp>,
    own: List<AppInfo>,
    ai: List<AppInfo>,
    suggestions: List<LauncherApp>,
    kriptonTitle: String,
    aiTitle: String,
    suggestedTitle: String,
): List<Cell> {
    val out = ArrayList<Cell>()
    if (own.isNotEmpty()) {
        out.add(Cell.Header(kriptonTitle))
        own.forEach { out.add(Cell.Item(null, it)) }
    }
    if (ai.isNotEmpty()) {
        out.add(Cell.Header(aiTitle))
        ai.forEach { out.add(Cell.Item(null, it)) }
    }
    if (suggestions.isNotEmpty() && prefs.drawerSort == DrawerSort.ALPHA) {
        out.add(Cell.Header(suggestedTitle))
        suggestions.forEach { out.add(Cell.Item(it, appInfo(it))) }
    }
    if (prefs.drawerSort == DrawerSort.ALPHA) {
        var last = ' '
        apps.forEach { a ->
            if (a.letter != last) {
                last = a.letter
                out.add(Cell.Header(a.letter.toString()))
            }
            out.add(Cell.Item(a, appInfo(a)))
        }
    } else {
        apps.forEach { out.add(Cell.Item(it, appInfo(it))) }
    }
    return out
}

private fun appInfo(a: LauncherApp) = AppInfo(DesktopApps.pkg(a.packageName), a.label, Color.Unspecified)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerGrid(
    cells: List<Cell>,
    apps: List<LauncherApp>?,
    loading: Boolean,
    prefs: LauncherPrefs,
    openApps: Set<String>,
    pinned: Set<String>,
    onHome: Set<String>,
    badgeOf: (String) -> Int,
    container: AppContainer,
    actions: DrawerActions,
) {
    val state = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val k = KriptonTheme.colors
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (loading) {
                Text(stringResource(R.string.l_loading), color = k.muted, modifier = Modifier.padding(24.dp))
            }
            LazyVerticalGrid(
                state = state,
                columns = GridCells.Adaptive((84 * prefs.iconScale).dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            ) {
                cells.forEachIndexed { i, c ->
                    when (c) {
                        is Cell.Header -> item(key = "h$i", span = { GridItemSpan(maxLineSpan) }) {
                            Text(c.text, style = MaterialTheme.typography.labelLarge, color = k.muted, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp))
                        }

                        is Cell.Item -> item(key = "i${c.info.app}$i") {
                            DrawerCell(c, prefs, c.info.app in openApps, c.info.app in pinned, c.info.app in onHome, badgeOf(c.info.app), container, actions)
                        }
                    }
                }
            }
        }
        if (prefs.drawerSort == DrawerSort.ALPHA && !apps.isNullOrEmpty()) {
            val letters = remember(cells) { cells.mapIndexedNotNull { i, c -> (c as? Cell.Header)?.takeIf { it.text.length == 1 }?.let { it.text to i } } }
            LetterRail(letters.map { it.first }) { letter ->
                letters.firstOrNull { it.first == letter }?.let { scope.launch { state.scrollToItem(it.second) } }
            }
        }
    }
}

@Composable
private fun LetterRail(letters: List<String>, onPick: (String) -> Unit) {
    var h by remember { mutableStateOf(IntSize.Zero) }
    Column(
        Modifier
            .width(26.dp)
            .fillMaxHeight()
            .onSizeChanged { h = it }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { o -> pick(letters, o.y, h.height, onPick) },
                    onVerticalDrag = { c, _ -> pick(letters, c.position.y, h.height, onPick) },
                )
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { l -> Text(l, style = MaterialTheme.typography.labelSmall, color = KriptonTheme.colors.muted, modifier = Modifier.clickable { onPick(l) }) }
    }
}

private fun pick(letters: List<String>, y: Float, height: Int, onPick: (String) -> Unit) {
    if (letters.isEmpty() || height <= 0) return
    val i = ((y / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
    onPick(letters[i])
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerCell(
    c: Cell.Item,
    prefs: LauncherPrefs,
    open: Boolean,
    pinned: Boolean,
    onHome: Boolean,
    badge: Int,
    container: AppContainer,
    actions: DrawerActions,
) {
    var menu by remember { mutableStateOf(false) }
    val pkg = c.app?.packageName
    val shortcuts = remember(menu, pkg) { if (menu && pkg != null) container.launcher.apps.shortcuts(pkg) else emptyList() }
    val hidden = pkg != null && pkg in prefs.hiddenApps
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = { actions.open(c.info.app) }, onLongClick = { menu = true })
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (c.app != null) {
                Box(Modifier.size((52 * prefs.iconScale).dp + 8.dp), contentAlignment = Alignment.Center) {
                    PackageIcon(c.app.packageName, (52 * prefs.iconScale).dp)
                    CountBadge(if (prefs.badges) badge else 0, Modifier.align(Alignment.TopEnd))
                }
            } else {
                BadgedIcon(c.info, (52 * prefs.iconScale).dp, if (prefs.badges) badge else 0)
            }
            if (prefs.showLabels) {
                Text(
                    c.info.title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
                )
            }
            Box(Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(if (open) MaterialTheme.colorScheme.primary else Color.Transparent))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            shortcuts.forEach { s ->
                DropdownMenuItem(text = { Text(s.label, maxLines = 1) }, onClick = {
                    menu = false
                    actions.shortcut(s)
                })
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (onHome) R.string.l_remove_home else R.string.l_add_home)) },
                onClick = {
                    menu = false
                    if (onHome) actions.removeFromHome(c.info.app) else actions.addToHome(c.info.app)
                },
            )
            DropdownMenuItem(text = { Text(stringResource(if (pinned) R.string.l_unpin_dock else R.string.l_pin_dock)) }, onClick = {
                menu = false
                actions.togglePin(c.info.app)
            })
            if (pkg != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (hidden) R.string.l_unhide else R.string.l_hide)) },
                    onClick = {
                        menu = false
                        actions.setHidden(pkg, !hidden)
                    },
                )
                DropdownMenuItem(text = { Text(stringResource(R.string.l_app_info)) }, onClick = {
                    menu = false
                    actions.appInfo(pkg)
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.l_uninstall)) }, onClick = {
                    menu = false
                    actions.uninstall(pkg)
                })
            }
        }
    }
}

@Composable
private fun SearchResultList(
    results: List<SearchResult>,
    infoOf: (String) -> AppInfo?,
    actions: DrawerActions,
) {
    val k = KriptonTheme.colors
    if (results.isEmpty()) {
        Text(stringResource(R.string.l_search_empty), color = k.muted, modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(results, key = { it.id }) { r ->
            when (r) {
                is SearchResult.App -> Row2(
                    icon = { PackageIcon(r.app.packageName, 36.dp) },
                    title = r.app.label,
                    sub = stringResource(R.string.l_res_app),
                ) { actions.open(DesktopApps.pkg(r.app.packageName)) }

                is SearchResult.Own -> Row2(
                    icon = { infoOf(r.app)?.let { com.kriptoncod.app.ui.desktop.DesktopAppIcon(it, 36.dp) } },
                    title = r.title,
                    sub = stringResource(R.string.l_res_kripton),
                ) { actions.open(r.app) }

                is SearchResult.Ai -> Row2(
                    icon = { infoOf(DesktopApps.ai(r.accountId))?.let { com.kriptoncod.app.ui.desktop.DesktopAppIcon(it, 36.dp) } },
                    title = r.name,
                    sub = stringResource(R.string.l_res_ai),
                ) { actions.open(DesktopApps.ai(r.accountId)) }

                is SearchResult.AskAi -> Row2(
                    icon = { Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(24.dp)) },
                    title = stringResource(R.string.l_res_ask, r.name),
                    sub = r.prompt,
                ) { actions.ask(r.accountId, r.prompt) }

                is SearchResult.Calc -> Row2(
                    icon = { Text("=", style = MaterialTheme.typography.titleLarge) },
                    title = r.value,
                    sub = r.expression + " · " + stringResource(R.string.l_res_copy),
                ) { actions.copy(r.value) }

                is SearchResult.FileHit -> Row2(
                    icon = { Text(if (r.isDir) "📁" else FileBrowse.badge(r.file.name).ifEmpty { "·" }, style = MaterialTheme.typography.labelLarge) },
                    title = r.file.name,
                    sub = r.file.parent.orEmpty(),
                ) { actions.openFile(r.file) }

                is SearchResult.Contact -> Row2(
                    icon = { Icon(Icons.Filled.Phone, null, Modifier.size(24.dp)) },
                    title = r.name,
                    sub = r.number,
                ) { actions.dial(r.number) }

                is SearchResult.Setting -> Row2(
                    icon = { Icon(Icons.Filled.Settings, null, Modifier.size(24.dp)) },
                    title = r.title,
                    sub = stringResource(R.string.l_res_setting),
                ) { actions.setting(r.action) }

                is SearchResult.Web -> Row2(
                    icon = { Icon(Icons.Filled.Search, null, Modifier.size(24.dp)) },
                    title = stringResource(R.string.l_res_web, r.query),
                    sub = "",
                ) { actions.web(r.query) }
            }
        }
    }
}

@Composable
private fun Row2(icon: @Composable () -> Unit, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = KriptonTheme.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
