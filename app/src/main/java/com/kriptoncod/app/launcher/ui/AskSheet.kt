package com.kriptoncod.app.launcher.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.desktop.AppInfo
import com.kriptoncod.app.ui.desktop.DesktopApps
import com.kriptoncod.app.ui.theme.KriptonTheme

/**
 * Tek dokunuşla AI'a sor: AI seç, yaz (ya da başka uygulamadan "Paylaş" ile gelen metin hazır olur), gönder ya da dosya ekle.
 * Gönderilen metin AI'ın sohbet sekmesinde açılır ve yanıtı orada izlenir.
 */
@Composable
internal fun AskSheet(
    ai: List<AppInfo>,
    initialTarget: Long?,
    initialText: String,
    onSend: (Long, String) -> Unit,
    onAttach: (Long, Uri) -> Unit,
    onOpenTab: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val k = KriptonTheme.colors
    val ids = ai.mapNotNull { a -> DesktopApps.aiAccountId(a.app)?.let { it to a } }
    var target by remember { mutableStateOf(initialTarget ?: ids.firstOrNull()?.first) }
    var text by remember { mutableStateOf(initialText) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) target?.let { onAttach(it, uri) } }

    Scrim(onDismiss) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .imePadding()
                .padding(10.dp)
                .fillMaxWidth()
                .swallowTaps()
                .panelSurface(22.dp)
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.l_ask_title), style = MaterialTheme.typography.titleMedium)
            if (ids.isEmpty()) {
                Text(stringResource(R.string.l_ask_no_ai), style = MaterialTheme.typography.bodyMedium, color = k.muted, modifier = Modifier.padding(vertical = 12.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.l_close)) }
                return@Column
            }
            LazyRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ids, key = { it.first }) { (id, info) -> Pill(info.title, { target = id }, selected = id == target) }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.l_ask_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
            )
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { picker.launch("*/*") }) { Text(stringResource(R.string.l_ask_attach)) }
                TextButton(onClick = { target?.let(onOpenTab) }) { Text(stringResource(R.string.l_ask_open_tab)) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    Button(enabled = text.isNotBlank() && target != null, onClick = { target?.let { onSend(it, text.trim()) } }) { Text(stringResource(R.string.l_ask_send)) }
                }
            }
        }
    }
}
