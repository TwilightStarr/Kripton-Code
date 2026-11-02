package com.kriptoncod.app.launcher.ui

import android.os.StatFs
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.R
import com.kriptoncod.app.data.run.RunProgress
import com.kriptoncod.app.launcher.notify.KriptonInbox
import com.kriptoncod.app.launcher.notify.NotificationHub
import com.kriptoncod.app.launcher.notify.Notifs
import com.kriptoncod.app.ui.desktop.DesktopPlatform
import com.kriptoncod.app.ui.desktop.FileBrowse
import com.kriptoncod.app.ui.theme.KriptonTheme
import com.kriptoncod.app.widget.RunWidgetData
import kotlinx.coroutines.flow.flowOf

/** Ana ekran panolarının ortak çerçevesi. */
@Composable
private fun PanelCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val base = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).panelSurface(18.dp)
    Column(if (onClick != null) base.clickable(onClick = onClick).padding(16.dp) else base.padding(16.dp)) { content() }
}

@Composable
internal fun ClockPanel(clock: String, date: String) {
    PanelCard {
        Text(clock, style = MaterialTheme.typography.displayMedium)
        if (date.isNotBlank()) Text(date, style = MaterialTheme.typography.titleMedium, color = KriptonTheme.colors.muted)
    }
}

/** Satır içi "AI'a sor": yaz, AI seç, gönder. */
@Composable
internal fun AiAskPanel(ai: List<Pair<Long, String>>, onAsk: (Long, String) -> Unit, onOpenSheet: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Long?>(null) }
    val target = picked ?: ai.firstOrNull()?.first
    PanelCard {
        Text(stringResource(R.string.l_ask_title), style = MaterialTheme.typography.titleSmall)
        if (ai.isEmpty()) {
            Text(stringResource(R.string.l_ask_no_ai), style = MaterialTheme.typography.bodySmall, color = KriptonTheme.colors.muted, modifier = Modifier.padding(top = 6.dp))
            return@PanelCard
        }
        LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ai, key = { it.first }) { (id, name) -> Pill(name, { picked = id }, selected = id == target) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.l_ask_hint)) },
                modifier = Modifier.weight(1f),
                maxLines = 3,
            )
            IconButton(
                enabled = text.isNotBlank() && target != null,
                onClick = {
                    target?.let { onAsk(it, text.trim()) }
                    text = ""
                },
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.l_ask_send)) }
        }
        Text(
            stringResource(R.string.l_ask_more),
            style = MaterialTheme.typography.labelMedium,
            color = KriptonTheme.colors.muted,
            modifier = Modifier.clickable(onClick = onOpenSheet).padding(top = 8.dp),
        )
    }
}

@Composable
internal fun RunStatusPanel(container: AppContainer, onOpen: () -> Unit) {
    val runs by container.runRepository.observeRuns().collectAsStateWithLifecycle(initialValue = emptyList())
    val active = RunWidgetData.pickActive(runs)
    val jobsFlow = remember(active?.id) { active?.let { container.runRepository.observeJobs(it.id) } ?: flowOf(emptyList()) }
    val jobs by jobsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    PanelCard(onClick = onOpen) {
        Text(stringResource(R.string.l_run_title), style = MaterialTheme.typography.titleSmall)
        if (active == null) {
            Text(stringResource(R.string.l_run_none), style = MaterialTheme.typography.bodySmall, color = KriptonTheme.colors.muted, modifier = Modifier.padding(top = 6.dp))
        } else {
            val p = RunProgress.of(active.status, jobs, active.createdAt, System.currentTimeMillis())
            val cur = RunWidgetData.summarize(active, jobs)
            Text(
                stringResource(R.string.l_run_line, active.id, p.percent, cur.done, cur.total),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (p.indeterminate) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            } else {
                LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            cur.nodeTitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = KriptonTheme.colors.muted, maxLines = 1, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

@Composable
internal fun StoragePanel(onOpenFiles: () -> Unit) {
    val stat = remember { runCatching { StatFs(DesktopPlatform.storageRoot().path) }.getOrNull() }
    val total = stat?.totalBytes ?: 0L
    val free = stat?.availableBytes ?: 0L
    PanelCard(onClick = onOpenFiles) {
        Text(stringResource(R.string.l_storage_title), style = MaterialTheme.typography.titleSmall)
        if (total > 0) {
            val used = total - free
            Text(
                stringResource(R.string.l_storage_line, FileBrowse.humanSize(used), FileBrowse.humanSize(total)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            LinearProgressIndicator(progress = { (used.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.l_storage_open), style = MaterialTheme.typography.labelMedium, color = KriptonTheme.colors.muted, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
internal fun NotificationsPanel(onOpen: () -> Unit) {
    val sys by NotificationHub.items.collectAsStateWithLifecycle()
    val inbox by KriptonInbox.items.collectAsStateWithLifecycle()
    val connected by NotificationHub.connected.collectAsStateWithLifecycle()
    val visible = Notifs.visible(sys).filter { !it.ongoing }
    val unreadOwn = KriptonInbox.unread(inbox)
    PanelCard(onClick = onOpen) {
        Text(stringResource(R.string.l_notif_title), style = MaterialTheme.typography.titleSmall)
        val line = when {
            !connected && unreadOwn == 0 -> stringResource(R.string.l_notif_need_access)
            visible.isEmpty() && unreadOwn == 0 -> stringResource(R.string.l_notif_none)
            else -> stringResource(R.string.l_notif_summary, visible.size, unreadOwn)
        }
        Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        visible.take(2).forEach {
            Row(Modifier.padding(top = 6.dp)) {
                Text(it.appLabel, style = MaterialTheme.typography.labelMedium, color = KriptonTheme.colors.muted, modifier = Modifier.width(96.dp), maxLines = 1)
                Text(it.title.ifBlank { it.text }, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}
