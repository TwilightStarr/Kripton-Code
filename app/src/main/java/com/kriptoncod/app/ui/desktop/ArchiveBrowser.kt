package com.kriptoncod.app.ui.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R
import com.kriptoncod.app.data.files.ArchiveEntry
import com.kriptoncod.app.data.files.ArchiveTool
import com.kriptoncod.app.ui.theme.KriptonTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Arşivi klasör gibi gezme (ZArchiver tarzı): içine gir, tek tek ya da toplu seçip çıkar, bir dosyaya dokunup önizle.
 * Hiçbir şey kendiliğinden açılmaz; çıkarma işlemini üst pencere yürütür (ilerleme çubuğu ve iptal orada).
 */
@Composable
fun ArchiveBrowserDialog(
    archive: File,
    onExtractSelected: (Set<String>) -> Unit,
    onExtractAll: () -> Unit,
    onPreview: (ArchiveEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val k = KriptonTheme.colors
    val all by produceState<List<ArchiveEntry>?>(null, archive.path) {
        value = withContext(Dispatchers.IO) { runCatching { ArchiveTool.list(archive) }.getOrNull() ?: emptyList() }
    }
    var prefix by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val list = all
    val shown = remember(list, prefix) { if (list == null) emptyList() else ArchiveTool.children(list, prefix) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(archive.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(
                    "/" + prefix,
                    style = MaterialTheme.typography.labelMedium,
                    color = k.muted,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    list == null -> Text("…")

                    list.isEmpty() -> Text(stringResource(R.string.l_ar_empty), color = k.muted)

                    else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        if (prefix.isNotEmpty()) {
                            item(key = "..") {
                                Text(
                                    "..",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth().clickable { prefix = prefix.substringBeforeLast('/', "") }.padding(vertical = 10.dp),
                                )
                            }
                        }
                        items(shown, key = { it.path }) { e ->
                            Row(
                                Modifier.fillMaxWidth().clickable { if (e.isDir) prefix = e.path else onPreview(e) }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Checkbox(e.path in selected, { on -> selected = if (on) selected + e.path else selected - e.path })
                                Column(Modifier.weight(1f)) {
                                    Text((if (e.isDir) "📁 " else "") + e.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!e.isDir) Text(com.kriptoncod.app.ui.desktop.FileBrowse.humanSize(e.size), style = MaterialTheme.typography.labelSmall, color = k.muted)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(enabled = selected.isNotEmpty(), onClick = { onExtractSelected(selected) }) { Text(stringResource(R.string.l_ar_extract_selected), maxLines = 1) }
                TextButton(enabled = !list.isNullOrEmpty(), onClick = onExtractAll) { Text(stringResource(R.string.l_ar_extract_all), maxLines = 1) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.desktop_files_cancel)) } },
    )
}
