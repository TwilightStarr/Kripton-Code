package com.kriptoncod.app.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R
import com.kriptoncod.app.launcher.DrawerSort
import com.kriptoncod.app.launcher.LauncherPrefs
import com.kriptoncod.app.launcher.notify.NotificationHub
import com.kriptoncod.app.launcher.search.ContactSearch
import com.kriptoncod.app.ui.theme.KriptonTheme
import androidx.compose.foundation.background

/** Launcher ayarları (tam ekran). Her değişiklik anında kaydedilir. */
@Composable
internal fun LauncherSettings(
    prefs: LauncherPrefs,
    isDefaultHome: Boolean,
    hiddenLabels: List<Pair<String, String>>,
    onChange: ((LauncherPrefs) -> LauncherPrefs) -> Unit,
    onPickWallpaper: () -> Unit,
    onSystemWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onRequestContacts: () -> Unit,
    onSetHome: () -> Unit,
    onAddWidget: () -> Unit,
    onAddPage: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val k = KriptonTheme.colors
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.l_set_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text(stringResource(R.string.l_close)) }
        }
        HorizontalDivider(color = k.nodeBorder)
        LazyColumn(Modifier.fillMaxSize()) {
            item { Head(R.string.l_set_home) }
            item {
                Info(stringResource(if (isDefaultHome) R.string.l_set_is_home else R.string.l_set_not_home))
                if (!isDefaultHome) TextButton(onClick = onSetHome, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.l_set_make_home)) }
            }
            item { SliderRow(R.string.l_set_columns, prefs.homeColumns.toFloat(), 3f, 7f, 3) { v -> onChange { it.copy(homeColumns = LauncherPrefs.cleanColumns(v.toInt())) } } }
            item { SliderRow(R.string.l_set_icon, prefs.iconScale, LauncherPrefs.ICON_MIN, LauncherPrefs.ICON_MAX, 0) { v -> onChange { it.copy(iconScale = LauncherPrefs.cleanIcon(v)) } } }
            item { SwitchRow(R.string.l_set_labels, prefs.showLabels) { v -> onChange { it.copy(showLabels = v) } } }
            item {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = onAddWidget) { Text(stringResource(R.string.l_menu_widget)) }
                    TextButton(onClick = onAddPage) { Text(stringResource(R.string.l_set_add_page)) }
                }
            }

            item { Head(R.string.l_set_drawer) }
            item {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DrawerSort.entries.forEach { s ->
                        Pill(
                            stringResource(when (s) { DrawerSort.ALPHA -> R.string.l_sort_alpha; DrawerSort.MOST_USED -> R.string.l_sort_used; DrawerSort.NEWEST -> R.string.l_sort_new }),
                            { onChange { it.copy(drawerSort = s) } },
                            selected = prefs.drawerSort == s,
                        )
                    }
                }
            }
            if (hiddenLabels.isNotEmpty()) {
                item { Info(stringResource(R.string.l_set_hidden)) }
                items(hiddenLabels, key = { it.first }) { (pkg, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onChange { it.copy(hiddenApps = it.hiddenApps - pkg) } }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.l_unhide), color = k.muted)
                    }
                }
            }

            item { Head(R.string.l_set_search) }
            item { SwitchRow(R.string.l_set_search_files, prefs.searchFiles) { v -> onChange { it.copy(searchFiles = v) } } }
            item {
                SwitchRow(R.string.l_set_search_contacts, prefs.searchContacts) { v ->
                    onChange { it.copy(searchContacts = v) }
                    if (v && !ContactSearch.hasPermission(context)) onRequestContacts()
                }
            }
            item { SwitchRow(R.string.l_set_search_calc, prefs.searchCalc) { v -> onChange { it.copy(searchCalc = v) } } }
            item { SwitchRow(R.string.l_set_search_web, prefs.searchWeb) { v -> onChange { it.copy(searchWeb = v) } } }

            item { Head(R.string.l_set_notifs) }
            item { SwitchRow(R.string.l_set_badges, prefs.badges) { v -> onChange { it.copy(badges = v) } } }
            item { SwitchRow(R.string.l_set_kripton_panel, prefs.kriptonNotificationsInPanel) { v -> onChange { it.copy(kriptonNotificationsInPanel = v) } } }
            item {
                val granted = NotificationHub.hasAccess(context)
                Info(stringResource(if (granted) R.string.l_set_access_on else R.string.l_set_access_off))
                TextButton(onClick = { runCatching { context.startActivity(NotificationHub.accessIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.l_access_grant))
                }
            }

            item { Head(R.string.l_set_gestures) }
            item { SwitchRow(R.string.l_set_swipe_up, prefs.gestureSwipeUp) { v -> onChange { it.copy(gestureSwipeUp = v) } } }
            item { SwitchRow(R.string.l_set_swipe_down, prefs.gestureSwipeDown) { v -> onChange { it.copy(gestureSwipeDown = v) } } }

            item { Head(R.string.l_set_wallpaper) }
            item {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = onPickWallpaper) { Text(stringResource(R.string.l_set_wp_pick)) }
                    TextButton(onClick = onSystemWallpaper) { Text(stringResource(R.string.l_set_wp_system)) }
                    TextButton(onClick = onClearWallpaper) { Text(stringResource(R.string.l_set_wp_clear)) }
                }
            }
            item { SliderRow(R.string.l_set_wp_dim, prefs.wallpaperDim, 0f, LauncherPrefs.DIM_MAX, 0) { v -> onChange { it.copy(wallpaperDim = LauncherPrefs.cleanDim(v)) } } }
        }
    }
}

@Composable
private fun Head(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp))
}

@Composable
private fun Info(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = KriptonTheme.colors.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
private fun SwitchRow(label: Int, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(label: Int, value: Float, min: Float, max: Float, steps: Int, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
        Slider(value = local.coerceIn(min, max), onValueChange = { local = it }, onValueChangeFinished = { onChange(local) }, valueRange = min..max, steps = steps)
    }
}

/** Widget seçici: uygulamaya göre gruplanmış kurulu widget'lar. */
@Composable
internal fun WidgetPicker(
    providers: List<AppWidgetProviderInfo>,
    context: Context,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val pm = context.packageManager
    Scrim(onDismiss) {
        Column(
            Modifier
                .align(Alignment.Center)
                .safeDrawingPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .swallowTaps()
                .panelSurface(20.dp)
                .padding(12.dp),
        ) {
            Text(stringResource(R.string.l_menu_widget), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
            if (providers.isEmpty()) {
                Text(stringResource(R.string.l_widget_none), color = KriptonTheme.colors.muted, modifier = Modifier.padding(12.dp))
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(providers, key = { it.provider.flattenToString() }) { p ->
                    val app = remember(p) { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p.provider.packageName, 0)).toString() }.getOrDefault(p.provider.packageName) }
                    val name = remember(p) { p.loadLabel(pm) ?: app }
                    Column(Modifier.fillMaxWidth().clickable { onPick(p) }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(app, style = MaterialTheme.typography.bodySmall, color = KriptonTheme.colors.muted, maxLines = 1)
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.l_close)) }
        }
    }
}
