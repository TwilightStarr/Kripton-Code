package com.kriptoncod.app.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.ui.components.KriptonCodLogo
import com.kriptoncod.app.ui.screens.Avatar
import com.kriptoncod.app.ui.screens.tint
import com.kriptoncod.app.ui.screens.webProvider
import com.kriptoncod.app.ui.theme.KriptonTheme

/** Görev çubuğundaki bir pencere düğmesi. */
data class TaskItem(
    val id: Int,
    val title: String,
    val focused: Boolean,
    val minimized: Boolean,
    val tint: Color,
    val icon: @Composable () -> Unit,
)

@Composable
internal fun Taskbar(
    items: List<TaskItem>,
    startOpen: Boolean,
    clock: String,
    fullscreen: Boolean,
    onStart: () -> Unit,
    onItem: (Int) -> Unit,
    onSideBySide: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme
    Column {
        HorizontalDivider(thickness = 1.5.dp, color = k.nodeBorder)
        Row(
            Modifier.fillMaxWidth().height(TASKBAR_HEIGHT).background(scheme.surface).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (startOpen) k.nodeFill else Color.Transparent)
                    .clickable(onClick = onStart),
                contentAlignment = Alignment.Center,
            ) { AppsGlyph(scheme.primary, 24.dp) }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(1.dp).height(28.dp).background(k.nodeBorder))
            LazyRow(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(items, key = { it.id }) { t -> TaskChip(t) { onItem(t.id) } }
            }
            IconButton(onClick = onSideBySide, modifier = Modifier.size(44.dp)) { SplitGlyph(k.muted) }
            IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(44.dp)) {
                FullscreenGlyph(if (fullscreen) scheme.primary else k.muted)
            }
            Text(
                clock,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

internal val TASKBAR_HEIGHT = 56.dp

@Composable
private fun TaskChip(t: TaskItem, onClick: () -> Unit) {
    val k = KriptonTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .height(44.dp)
            .widthIn(min = 56.dp, max = 190.dp)
            .alpha(if (t.minimized) 0.6f else 1f)
            .clip(shape)
            .background(if (t.focused) k.nodeFill else Color.Transparent)
            .then(if (t.focused) Modifier.border(1.5.dp, k.nodeBorder, shape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp).align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            t.icon()
            Spacer(Modifier.width(8.dp))
            Text(t.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Açık pencere işareti (odaktaysa uzun).
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
                .width(if (t.focused) 24.dp else 8.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(t.tint),
        )
    }
}

/** Başlat menüsü: AI'lar, uygulamalar ve masaüstü eylemleri. Dışına dokunmak kapatır. */
@Composable
internal fun StartMenu(
    aiRows: List<AiAccount>,
    openApps: Set<String>,
    fullscreen: Boolean,
    onOpenAi: (AiAccount) -> Unit,
    onOpenFiles: () -> Unit,
    onOpenKripton: () -> Unit,
    onSideBySide: () -> Unit,
    onTile: () -> Unit,
    onCascade: () -> Unit,
    onShowDesktop: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onExit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 8.dp)
                .widthIn(max = 340.dp)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .border(1.5.dp, k.nodeBorder, RoundedCornerShape(16.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            SectionLabel(stringResource(R.string.desktop_menu_ai))
            if (aiRows.isEmpty()) {
                Text(
                    stringResource(R.string.desktop_menu_no_ai),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            aiRows.forEach { a ->
                val provider = a.webProvider() ?: return@forEach
                MenuRow(
                    label = a.displayName,
                    active = DesktopApps.ai(a.id) in openApps,
                    activeTint = provider.tint(),
                    icon = { Avatar(provider, 26.dp) },
                    onClick = { onOpenAi(a) },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = k.nodeBorder.copy(alpha = 0.5f))
            SectionLabel(stringResource(R.string.desktop_menu_apps))
            MenuRow(
                label = stringResource(R.string.desktop_app_files),
                active = DesktopApps.FILES in openApps,
                activeTint = scheme.primary,
                icon = { FolderGlyph(scheme.primary, 26.dp) },
                onClick = onOpenFiles,
            )
            MenuRow(
                label = stringResource(R.string.desktop_app_kripton),
                active = DesktopApps.KRIPTON in openApps,
                activeTint = scheme.primary,
                icon = { KriptonCodLogo(logoSize = 26.dp) },
                onClick = onOpenKripton,
            )
            MenuRow(label = stringResource(R.string.desktop_menu_side_by_side), icon = { SplitGlyph(scheme.onSurface, 26.dp) }, onClick = onSideBySide)
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = k.nodeBorder.copy(alpha = 0.5f))
            MenuRow(label = stringResource(R.string.desktop_menu_tile), icon = { MaximizeGlyph(scheme.onSurface, restore = false, size = 22.dp) }, onClick = onTile)
            MenuRow(label = stringResource(R.string.desktop_menu_cascade), icon = { MaximizeGlyph(scheme.onSurface, restore = true, size = 22.dp) }, onClick = onCascade)
            MenuRow(label = stringResource(R.string.desktop_menu_show_desktop), icon = { MonitorGlyph(scheme.onSurface, 24.dp) }, onClick = onShowDesktop)
            MenuRow(
                label = stringResource(R.string.desktop_menu_fullscreen),
                active = fullscreen,
                activeTint = scheme.primary,
                icon = { FullscreenGlyph(scheme.onSurface, 24.dp) },
                onClick = onToggleFullscreen,
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = k.nodeBorder.copy(alpha = 0.5f))
            MenuRow(
                label = stringResource(R.string.desktop_menu_exit),
                icon = { CloseGlyph(k.danger, 22.dp) },
                labelColor = k.danger,
                onClick = onExit,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = KriptonTheme.colors.muted,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun MenuRow(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    active: Boolean = false,
    activeTint: Color = Color.Unspecified,
    labelColor: Color = Color.Unspecified,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (active) Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(activeTint))
    }
}
