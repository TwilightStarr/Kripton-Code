package com.kriptoncod.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kriptoncod.app.R
import com.kriptoncod.app.data.serial.SerialMarkers
import com.kriptoncod.app.data.serial.SerialPrompt
import com.kriptoncod.app.data.web.WebProvider
import com.kriptoncod.app.ui.components.KBadge
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KChip
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme

/** Önizlemedeki düzenlenebilir adım. [key] sıra değişse de kararlı kalır. */
private data class Draft(
    val key: Int,
    val prompt: SerialPrompt,
    val enabled: Boolean = true,
)

/** Önizlemede gösterilen örnek işaret; gerçek çalışmada her alışveriş için rastgele üretilir. */
private const val SAMPLE_NONCE = "ABC123"

/**
 * Seri çalışmayı BAŞLATMADAN önce adımları gösterir ve ayarlatır: sıra, atlama, hangi sohbete gideceği,
 * dosya beklenip beklenmediği, metin ve AI'a giden tam mesaj. [onStart] yalnız açık adımlarla, son sırasıyla çağrılır.
 */
@Composable
fun SerialPreviewDialog(
    initial: List<SerialPrompt>,
    provider: WebProvider?,
    starting: Boolean,
    error: String?,
    onBack: () -> Unit,
    onStart: (List<SerialPrompt>) -> Unit,
) {
    val k = KriptonTheme.colors
    var drafts by remember { mutableStateOf(initial.mapIndexed { i, p -> Draft(i, p) }) }
    var expandedKey by remember { mutableStateOf<Int?>(null) }

    fun move(key: Int, delta: Int) {
        val from = drafts.indexOfFirst { it.key == key }
        val to = from + delta
        if (from < 0 || to !in drafts.indices) return
        drafts = drafts.toMutableList().also { list -> list.add(to, list.removeAt(from)) }
    }

    fun change(key: Int, f: (Draft) -> Draft) {
        drafts = drafts.map { if (it.key == key) f(it) else it }
    }

    val active = drafts.filter { it.enabled }
    // Sohbet numaraları: ilk açık adım her zaman sohbeti açar; sonra yalnız "yeni sohbet" işaretliler.
    val chatNo = HashMap<Int, Int>()
    var chats = 0
    active.forEachIndexed { i, d ->
        if (i == 0 || d.prompt.newChat) chats++
        chatNo[d.key] = chats
    }
    val withFiles = active.count { it.prompt.expectFiles }

    Dialog(
        onDismissRequest = { if (!starting) onBack() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, enabled = !starting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                    Text(stringResource(R.string.serial_preview_title), style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.serial_preview_info), style = MaterialTheme.typography.bodySmall, color = k.muted)
                    Text(
                        stringResource(R.string.serial_preview_summary, active.size, chats, withFiles),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(drafts, key = { _, d -> d.key }) { index, d ->
                        PreviewCard(
                            index = index,
                            count = drafts.size,
                            draft = d,
                            startsChat = d.enabled && active.firstOrNull()?.key == d.key,
                            chatNo = chatNo[d.key],
                            provider = provider,
                            expanded = expandedKey == d.key,
                            onToggleExpand = { expandedKey = if (expandedKey == d.key) null else d.key },
                            onPrompt = { p -> change(d.key) { it.copy(prompt = p) } },
                            onEnabled = { on -> change(d.key) { it.copy(enabled = on) } },
                            onMoveUp = { move(d.key, -1) },
                            onMoveDown = { move(d.key, 1) },
                        )
                    }
                }
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    error?.let {
                        Text(
                            stringResource(R.string.serial_launch_failed, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (active.isEmpty()) {
                        Text(stringResource(R.string.serial_preview_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    KButton(
                        text = stringResource(R.string.serial_preview_start, active.size),
                        onClick = { onStart(active.map { it.prompt }) },
                        enabled = active.isNotEmpty() && !starting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(
    index: Int,
    count: Int,
    draft: Draft,
    startsChat: Boolean,
    chatNo: Int?,
    provider: WebProvider?,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onPrompt: (SerialPrompt) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val k = KriptonTheme.colors
    val p = draft.prompt
    val header = if (startsChat || (draft.enabled && p.newChat)) stringResource(R.string.serial_preview_project_header) else ""
    NodeCard(modifier = Modifier.fillMaxWidth().alpha(if (draft.enabled) 1f else 0.5f), accent = NodeAccent.Blue) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}. ${p.title}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onMoveUp, enabled = index > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.serial_preview_move_up))
                }
                IconButton(onClick = onMoveDown, enabled = index < count - 1) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.serial_preview_move_down))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (draft.enabled && chatNo != null) KBadge(text = stringResource(R.string.serial_preview_chat, chatNo))
                KBadge(
                    text = stringResource(if (p.expectFiles) R.string.serial_preview_files else R.string.serial_preview_nofiles),
                    accent = if (p.expectFiles) NodeAccent.Green else NodeAccent.Blue,
                )
                Text(
                    stringResource(R.string.serial_preview_include),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                Switch(checked = draft.enabled, onCheckedChange = onEnabled)
            }

            if (draft.enabled) {
                if (startsChat) {
                    Text(stringResource(R.string.serial_preview_first_locked), style = MaterialTheme.typography.bodySmall, color = k.muted)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KChip(
                            label = stringResource(R.string.serial_preview_same_chat),
                            selected = !p.newChat,
                            onClick = { onPrompt(p.copy(newChat = false)) },
                            modifier = Modifier.weight(1f),
                        )
                        KChip(
                            label = stringResource(R.string.serial_preview_new_chat),
                            selected = p.newChat,
                            onClick = { onPrompt(p.copy(newChat = true)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KChip(
                        label = stringResource(R.string.serial_preview_files),
                        selected = p.expectFiles,
                        onClick = { onPrompt(p.copy(expectFiles = true)) },
                        modifier = Modifier.weight(1f),
                    )
                    KChip(
                        label = stringResource(R.string.serial_preview_nofiles),
                        selected = !p.expectFiles,
                        onClick = { onPrompt(p.copy(expectFiles = false)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    stringResource(if (p.expectFiles) R.string.serial_preview_route_files else R.string.serial_preview_route_nofiles),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )
            }

            if (!expanded) {
                Text(p.body, style = MaterialTheme.typography.bodySmall, color = k.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onToggleExpand) {
                Text(stringResource(if (expanded) R.string.serial_preview_collapse else R.string.serial_preview_expand))
            }
            if (expanded) {
                OutlinedTextField(
                    value = p.body,
                    onValueChange = { onPrompt(p.copy(body = it)) },
                    label = { Text(stringResource(R.string.serial_preview_edit_label)) },
                    minLines = 3,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.serial_preview_full_message), style = MaterialTheme.typography.labelLarge)
                val full = if (provider != null) {
                    SerialMarkers.wrap(provider, header, p.body, SAMPLE_NONCE, p.expectFiles)
                } else {
                    p.body
                }
                Text(full, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = k.muted)
            }
        }
    }
}
