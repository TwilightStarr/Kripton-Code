package com.kriptoncod.app.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.R
import com.kriptoncod.app.launcher.notify.InboxItem
import com.kriptoncod.app.launcher.notify.KIND_DONE
import com.kriptoncod.app.launcher.notify.KIND_FAILED
import com.kriptoncod.app.launcher.notify.KriptonInbox
import com.kriptoncod.app.launcher.notify.LNotification
import com.kriptoncod.app.launcher.notify.NotificationHub
import com.kriptoncod.app.launcher.notify.Notifs
import com.kriptoncod.app.launcher.notify.QuickToggles
import com.kriptoncod.app.ui.theme.KriptonTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Kendi bildirim panelimiz: Android bildirim gölgesinin yerini alır. Üstte hızlı düğmeler; altında Kripton'un kendi olayları,
 * sonra uygulamaya göre gruplanmış sistem bildirimleri, en altta sürekli (müzik, indirme…) olanlar. Kartı yana kaydırmak kapatır,
 * dokunmak ilgili uygulamayı açar, satır içi yanıt desteklenir.
 */
@Composable
internal fun NotificationPanel(
    clock: String,
    date: String,
    onOpenKripton: () -> Unit,
    onToggleTheme: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val k = KriptonTheme.colors
    val sys by NotificationHub.items.collectAsStateWithLifecycle()
    val connected by NotificationHub.connected.collectAsStateWithLifecycle()
    val inbox by KriptonInbox.items.collectAsStateWithLifecycle()
    val torch by QuickToggles.torch.collectAsStateWithLifecycle()
    val visible = remember(sys) { Notifs.visible(sys) }
    val (events, ongoing) = remember(visible) { Notifs.split(visible) }
    val groups = remember(events) { Notifs.group(events) }
    val inboxShown = remember(inbox) { inbox.sortedByDescending { it.time } }
    val justNow = stringResource(R.string.l_now)

    Scrim(onDismiss, alpha = 0.55f) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(10.dp)
                .fillMaxWidth()
                .heightIn(max = 760.dp)
                .align(Alignment.TopCenter)
                .swallowTaps()
                .panelSurface(22.dp)
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(clock, style = MaterialTheme.typography.headlineMedium)
                    Text(date, style = MaterialTheme.typography.bodySmall, color = k.muted)
                }
                TextButton(onClick = {
                    NotificationHub.clearAll()
                    KriptonInbox.clear()
                }) { Text(stringResource(R.string.l_clear_all)) }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Toggle(stringResource(R.string.l_qt_wifi), false, Modifier.weight(1f)) { QuickToggles.start(context, QuickToggles.internetPanel()) }
                Toggle(stringResource(R.string.l_qt_bt), false, Modifier.weight(1f)) { QuickToggles.start(context, QuickToggles.bluetooth()) }
                Toggle(stringResource(R.string.l_qt_volume), false, Modifier.weight(1f)) { QuickToggles.start(context, QuickToggles.volume()) }
                if (QuickToggles.hasTorch(context)) Toggle(stringResource(R.string.l_qt_torch), torch, Modifier.weight(1f)) { QuickToggles.toggleTorch(context) }
                Toggle(stringResource(R.string.l_qt_theme), false, Modifier.weight(1f), onToggleTheme)
                Toggle(stringResource(R.string.l_qt_settings), false, Modifier.weight(1f), onSettings)
            }

            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!connected) {
                    item(key = "access") {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(k.nodeFill).padding(12.dp)) {
                            Text(stringResource(R.string.l_access_title), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.l_access_body), style = MaterialTheme.typography.bodySmall, color = k.muted, modifier = Modifier.padding(top = 4.dp))
                            TextButton(onClick = { runCatching { context.startActivity(NotificationHub.accessIntent(context).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }) {
                                Text(stringResource(R.string.l_access_grant))
                            }
                        }
                    }
                }
                if (inboxShown.isNotEmpty()) {
                    item(key = "h_kripton") { Section(stringResource(R.string.l_notif_kripton)) }
                    items(inboxShown, key = { "k${it.id}" }) { item ->
                        Swipeable(onDismiss = { KriptonInbox.remove(item.id) }) {
                            InboxCard(item, Notifs.ago(System.currentTimeMillis(), item.time, justNow)) {
                                KriptonInbox.remove(item.id)
                                onOpenKripton()
                            }
                        }
                    }
                }
                groups.forEach { g ->
                    item(key = "g${g.pkg}") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { Section(g.label + if (g.count > 1) "  (${g.count})" else "") }
                            IconButton(onClick = { NotificationHub.clearPackage(g.pkg) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Close, stringResource(R.string.l_clear_all), Modifier.size(16.dp))
                            }
                        }
                    }
                    items(g.items, key = { it.key }) { n ->
                        Swipeable(onDismiss = { if (n.clearable) NotificationHub.dismiss(n.key) }) {
                            NotifCard(n, Notifs.ago(System.currentTimeMillis(), n.postTime, justNow), onDismiss)
                        }
                    }
                }
                if (ongoing.isNotEmpty()) {
                    item(key = "h_ongoing") { Section(stringResource(R.string.l_notif_ongoing)) }
                    items(ongoing, key = { "o" + it.key }) { n -> NotifCard(n, "", onDismiss) }
                }
                if (inboxShown.isEmpty() && groups.isEmpty() && ongoing.isEmpty() && connected) {
                    item(key = "empty") { Text(stringResource(R.string.l_notif_none), color = k.muted, modifier = Modifier.padding(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = KriptonTheme.colors.muted, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}

@Composable
private fun Toggle(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val k = KriptonTheme.colors
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) MaterialTheme.colorScheme.primary else k.nodeFill)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Yana kaydırınca [onDismiss] çağıran sarmalayıcı. */
@Composable
private fun Swipeable(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    var dx by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .offset { IntOffset(dx.roundToInt(), 0) }
            .graphicsLayer { alpha = (1f - abs(dx) / 700f).coerceIn(0.2f, 1f) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(dx) > 260f) onDismiss()
                        dx = 0f
                    },
                    onDragCancel = { dx = 0f },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        dx += delta
                    },
                )
            },
    ) { content() }
}

@Composable
private fun InboxCard(item: InboxItem, ago: String, onClick: () -> Unit) {
    val k = KriptonTheme.colors
    val mark = when (item.kind) {
        KIND_DONE -> "✓"
        KIND_FAILED -> "!"
        else -> "•"
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(k.nodeFill).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(mark, style = MaterialTheme.typography.titleMedium, color = if (item.kind == KIND_FAILED) k.danger else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.text, style = MaterialTheme.typography.bodySmall, color = k.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Text(ago, style = MaterialTheme.typography.labelSmall, color = k.muted)
    }
}

@Composable
private fun NotifCard(n: LNotification, ago: String, onClosePanel: () -> Unit) {
    val context = LocalContext.current
    val k = KriptonTheme.colors
    var replyFor by remember { mutableStateOf<Int?>(null) }
    var reply by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(k.nodeFill).padding(12.dp)) {
        Column(
            Modifier.fillMaxWidth().clickable {
                if (n.bigText.isNotBlank() && !expanded) {
                    expanded = true
                } else if (NotificationHub.open(context, n.key)) {
                    onClosePanel()
                }
            },
        ) {
            Row {
                Text(n.title.ifBlank { n.appLabel }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (ago.isNotEmpty()) Text(ago, style = MaterialTheme.typography.labelSmall, color = k.muted)
            }
            val body = if (expanded && n.bigText.isNotBlank()) n.bigText else n.text
            if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodySmall, color = k.muted, maxLines = if (expanded) 12 else 3, overflow = TextOverflow.Ellipsis)
        }
        if (n.progress >= 0 || n.indeterminate) {
            if (n.indeterminate || n.progress < 0) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            } else {
                LinearProgressIndicator(progress = { n.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
        if (n.actions.isNotEmpty()) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                n.actions.take(3).forEach { a ->
                    TextButton(onClick = {
                        if (a.acceptsReply) {
                            replyFor = if (replyFor == a.index) null else a.index
                        } else {
                            NotificationHub.runAction(context, n.key, a.index, null)
                        }
                    }) { Text(a.title, maxLines = 1) }
                }
            }
        }
        replyFor?.let { idx ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(reply, { reply = it }, modifier = Modifier.weight(1f), maxLines = 3)
                TextButton(enabled = reply.isNotBlank(), onClick = {
                    NotificationHub.runAction(context, n.key, idx, reply.trim())
                    reply = ""
                    replyFor = null
                }) { Text(stringResource(R.string.l_ask_send)) }
            }
        }
    }
}
