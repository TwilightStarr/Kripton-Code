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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R
import com.kriptoncod.app.data.shizuku.ShizukuFiles
import com.kriptoncod.app.ui.theme.KriptonTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Satır menüsündeki eylemler. */
enum class FileAction { OPEN, OPEN_WITH, COPY, MOVE, RENAME, DELETE, PROPS }

@Composable
fun RenameDialog(file: File, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(file.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.desktop_files_rename)) },
        text = {
            OutlinedTextField(name, { name = it }, singleLine = true, isError = !FileOps.validName(name), modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = FileOps.validName(name)) { Text(stringResource(R.string.desktop_files_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.desktop_files_cancel)) } },
    )
}

@Composable
fun DeleteDialog(file: File, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.desktop_files_delete)) },
        text = {
            Text(stringResource(if (file.isDirectory) R.string.desktop_files_delete_folder_body else R.string.desktop_files_delete_body, file.name))
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.desktop_files_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.desktop_files_cancel)) } },
    )
}

/** Özellikler: ad, yol, boyut, tarih; MD5 / SHA-256 isteğe bağlı hesaplanır (büyük dosyada yavaş olabilir). */
@Composable
fun PropsDialog(entry: FileEntry, onDismiss: () -> Unit) {
    var hash by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val folder by produceState<Pair<Long, Int>?>(null, entry.file.path) {
        value = if (entry.isDir) withContext(Dispatchers.IO) { FileOps.folderStats(entry.file) } else null
    }
    val sums by produceState<Pair<String, String>?>(null, hash) {
        value = if (!hash || entry.isDir) {
            null
        } else {
            withContext(Dispatchers.IO) {
                fun open() = if (entry.file.canRead()) entry.file.inputStream() else ShizukuFiles.openRead(entry.file.path)
                runCatching {
                    val md5 = FileOps.checksum(open()!!, "MD5")
                    val sha = FileOps.checksum(open()!!, "SHA-256")
                    md5 to sha
                }.getOrDefault("?" to "?")
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Line(stringResource(R.string.desktop_files_prop_path), entry.file.path)
                if (entry.isDir) {
                    Line(
                        stringResource(R.string.desktop_files_prop_size),
                        folder?.let { "${FileBrowse.humanSize(it.first)} · " + stringResource(R.string.desktop_files_folder_files, it.second) } ?: "…",
                    )
                } else {
                    Line(stringResource(R.string.desktop_files_prop_size), "${FileBrowse.humanSize(entry.size)} (${entry.size} B)")
                    Line(stringResource(R.string.desktop_files_prop_type), FileBrowse.mimeFor(entry.name))
                }
                if (entry.modified > 0) Line(stringResource(R.string.desktop_files_prop_modified), fmt.format(Date(entry.modified)))
                if (!entry.isDir) {
                    if (!hash) {
                        TextButton(onClick = { hash = true }) { Text(stringResource(R.string.desktop_files_prop_hash)) }
                    } else {
                        Line("MD5", sums?.first ?: "…")
                        Line("SHA-256", sums?.second ?: "…")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.desktop_files_ok)) } },
    )
}

@Composable
private fun Line(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = KriptonTheme.colors.muted)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/** Harici uygulama seçimi + "her zaman bununla aç" (uzantı başına). */
@Composable
fun OpenWithDialog(file: File, onDismiss: () -> Unit, onFailed: () -> Unit) {
    val context = LocalContext.current
    var always by remember { mutableStateOf(false) }
    val apps by produceState<List<AppChoice>?>(null, file.path) { value = withContext(Dispatchers.IO) { FileOpen.candidates(context, file) } }
    val ext = FileBrowse.extension(file.name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.desktop_files_open_with)) },
        text = {
            Column {
                val list = apps
                when {
                    list == null -> Text("…")

                    list.isEmpty() -> Text(stringResource(R.string.desktop_files_no_app))

                    else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(list, key = { it.packageName }) { a ->
                            Text(
                                a.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (FileOpen.launch(context, file, a.packageName)) {
                                        if (always) FileOpen.remember(context, file, a.packageName)
                                        onDismiss()
                                    } else {
                                        onFailed()
                                    }
                                }.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
                if (ext.isNotEmpty()) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(always, { always = it })
                        Text(stringResource(R.string.desktop_files_always, ext), style = MaterialTheme.typography.bodySmall)
                    }
                    if (FileOpen.remembered(context, file) != null) {
                        TextButton(onClick = {
                            FileOpen.forget(context, file)
                            onDismiss()
                        }) { Text(stringResource(R.string.desktop_files_forget_default, ext)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (FileOpen.launch(context, file, null)) onDismiss() else onFailed() }) {
                Text(stringResource(R.string.desktop_files_system_chooser))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.desktop_files_cancel)) } },
    )
}
