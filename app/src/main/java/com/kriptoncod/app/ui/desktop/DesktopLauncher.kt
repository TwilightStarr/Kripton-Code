package com.kriptoncod.app.ui.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.theme.KriptonTheme

/**
 * Uygulama başlatıcı (Linux'taki "Uygulamaları göster" ekranı): Kripton uygulamaları, AI'lar ve cihazdaki tüm uygulamalar;
 * arama kutusu vardır. Dokununca uygulama sekme olarak açılır; raptiye ya da uzun basış alt çubuğa sabitler/kaldırır.
 * Dışına dokunmak kapatır. Sistem iletişim kutusu (Dialog) KULLANILMAZ: tam ekran modda sistem çubuklarını geri getirir.
 *
 * @param installed null: cihazdaki uygulamalar henüz yükleniyor.
 */
@Composable
internal fun Launcher(
    kripton: List<AppInfo>,
    ai: List<AppInfo>,
    installed: List<AppInfo>?,
    openApps: Set<String>,
    pinned: Set<String>,
    fullscreen: Boolean,
    onOpen: (AppInfo) -> Unit,
    onTogglePin: (AppInfo) -> Unit,
    onToggleFullscreen: () -> Unit,
    /** null: Kripton zaten ana ekran. */
    onSetHome: (() -> Unit)?,
    onExit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val kriptonShown = kripton.filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) }
    val aiShown = ai.filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) }
    val installedShown = installed?.filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) }
    val nothing = kriptonShown.isEmpty() && aiShown.isEmpty() && installedShown != null && installedShown.isEmpty()
    val shape = RoundedCornerShape(20.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = 920.dp)
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .clip(shape)
                .background(scheme.surface)
                .border(1.5.dp, k.nodeBorder, shape)
                // Karta dokunmak zemine geçmesin.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.desktop_launcher_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (kriptonShown.isNotEmpty()) {
                    item(key = "h_kripton", span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.desktop_launcher_kripton)) }
                    items(kriptonShown, key = { it.app }) { a ->
                        LauncherTile(a, a.app in openApps, a.app in pinned, { onOpen(a) }, { onTogglePin(a) })
                    }
                }
                if (aiShown.isNotEmpty()) {
                    item(key = "h_ai", span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.desktop_menu_ai)) }
                    items(aiShown, key = { it.app }) { a ->
                        LauncherTile(a, a.app in openApps, a.app in pinned, { onOpen(a) }, { onTogglePin(a) })
                    }
                }
                if (installed == null) {
                    item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.desktop_launcher_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = k.muted,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else if (!installedShown.isNullOrEmpty()) {
                    item(key = "h_installed", span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.desktop_launcher_installed)) }
                    items(installedShown, key = { it.app }) { a ->
                        LauncherTile(a, a.app in openApps, a.app in pinned, { onOpen(a) }, { onTogglePin(a) })
                    }
                }
                if (nothing) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.desktop_launcher_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = k.muted,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp), color = k.nodeBorder.copy(alpha = 0.5f))
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onToggleFullscreen) {
                    FullscreenGlyph(if (fullscreen) scheme.primary else scheme.onSurface, 20.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.desktop_menu_fullscreen), maxLines = 1)
                }
                if (onSetHome != null) {
                    TextButton(onClick = onSetHome) {
                        MonitorGlyph(scheme.onSurface, 20.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.desktop_menu_set_home), maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onExit, colors = ButtonDefaults.textButtonColors(contentColor = k.danger)) {
                    CloseGlyph(k.danger, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.desktop_menu_exit), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = KriptonTheme.colors.muted,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
    )
}

/** Başlatıcıdaki uygulama kutusu: simge, ad, açık işareti ve sabitleme raptiyesi. Uzun basış menü açar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherTile(
    info: AppInfo,
    open: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val pinText = stringResource(if (pinned) R.string.desktop_unpin else R.string.desktop_pin)
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(TILE_ICON + 8.dp), contentAlignment = Alignment.Center) { DesktopAppIcon(info, TILE_ICON) }
            Text(
                info.title,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Box(Modifier.padding(top = 4.dp).size(6.dp).clip(CircleShape).background(if (open) info.tint else Color.Transparent))
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(32.dp)
                .clip(CircleShape)
                .semantics { contentDescription = pinText }
                .clickable(onClick = onTogglePin),
            contentAlignment = Alignment.Center,
        ) {
            PinGlyph(if (pinned) scheme.primary else k.muted.copy(alpha = 0.6f), filled = pinned, size = 16.dp)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(pinText) },
                onClick = {
                    menu = false
                    onTogglePin()
                },
            )
        }
    }
}

/**
 * Boş masaüstü (hiçbir sekme görünmezken): büyük saat, tarih ve sabitlenmiş uygulamaların kısayolları.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopHome(
    clock: String,
    date: String,
    pinned: List<AppInfo>,
    openApps: Set<String>,
    onOpen: (AppInfo) -> Unit,
) {
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(clock, style = MaterialTheme.typography.displayMedium)
            if (date.isNotBlank()) Text(date, style = MaterialTheme.typography.titleMedium, color = k.muted)
            Spacer(Modifier.size(28.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.widthIn(max = 720.dp),
            ) {
                pinned.forEach { a ->
                    val shape = RoundedCornerShape(20.dp)
                    Column(
                        Modifier
                            .width(104.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOpen(a) }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(shape)
                                .background(scheme.surface.copy(alpha = 0.8f))
                                .border(1.5.dp, k.nodeBorder, shape),
                            contentAlignment = Alignment.Center,
                        ) { DesktopAppIcon(a, 44.dp) }
                        Text(
                            a.title,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Box(Modifier.padding(top = 4.dp).size(6.dp).clip(CircleShape).background(if (a.app in openApps) a.tint else Color.Transparent))
                    }
                }
            }
            Text(
                stringResource(R.string.desktop_empty),
                style = MaterialTheme.typography.bodySmall,
                color = k.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 28.dp),
            )
        }
    }
}
