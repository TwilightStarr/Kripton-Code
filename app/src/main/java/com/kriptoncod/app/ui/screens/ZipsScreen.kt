package com.kriptoncod.app.ui.screens

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.R
import com.kriptoncod.app.data.files.ZipArchive
import com.kriptoncod.app.ui.components.KBadge
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.KChip
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ZipKind { GITHUB, AI, RUN }

/** Listedeki tek zip: arşiv kaydı (GitHub'dan çekilen / AI değişikliği) ya da bir run'ın sonuç zip'i. */
private class ZipItem(
    val key: String,
    val createdAt: Long,
    val kind: ZipKind,
    val title: String,
    val detail: String,
    val size: Long,
    val count: Int,
    val file: File,
    val archiveId: String?,
)

/**
 * "Zip'ler" sekmesi: GitHub'dan çekilen proje zip'leri, AI'ların her adımda yaptığı değişiklik zip'leri ve run sonuçları
 * tek listede. Her biri gezilebilir (dosya listesi + metin önizleme) ve cihaza indirilebilir. Yeni zip gelince liste kendiliğinden güncellenir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZipsScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val k = KriptonTheme.colors
    val records by container.zipArchive.records.collectAsStateWithLifecycle()
    val runs by container.runRepository.observeRuns().collectAsStateWithLifecycle(initialValue = emptyList())

    val items = remember(records, runs) {
        val archived = records.map {
            ZipItem(
                key = it.id, createdAt = it.createdAt,
                kind = if (it.kind == ZipArchive.KIND_REPO) ZipKind.GITHUB else ZipKind.AI,
                title = it.title, detail = it.detail, size = it.sizeBytes, count = it.fileCount,
                file = container.zipArchive.file(it.id), archiveId = it.id,
            )
        }
        val results = runs.mapNotNull { r ->
            val path = r.zipPath ?: return@mapNotNull null
            val f = runCatching { container.outputStore.resolve(path) }.getOrNull()?.takeIf { it.isFile } ?: return@mapNotNull null
            ZipItem(
                key = "run-${r.id}", createdAt = r.createdAt, kind = ZipKind.RUN, title = "Run #${r.id}",
                detail = r.repo.orEmpty(), size = f.length(), count = -1, file = f, archiveId = null,
            )
        }
        (archived + results).sortedByDescending { it.createdAt }
    }

    var filter by remember { mutableStateOf<ZipKind?>(null) }
    var browsing by remember { mutableStateOf<Pair<ZipItem, List<Pair<String, ByteArray>>?>?>(null) }
    var confirmDelete by remember { mutableStateOf<ZipItem?>(null) }
    var pendingSave by remember { mutableStateOf<ZipItem?>(null) }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val item = pendingSave
        pendingSave = null
        if (uri != null && item != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out -> item.file.inputStream().use { it.copyTo(out) } } != null
                    }.getOrDefault(false)
                }
                Toast.makeText(context, if (ok) R.string.zips_saved else R.string.zips_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val shown = items.filter { filter == null || it.kind == filter }
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.zips_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.zips_intro), style = MaterialTheme.typography.bodyMedium, color = k.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KChip(stringResource(R.string.zips_filter_all), filter == null, { filter = null })
            KChip(stringResource(R.string.zips_kind_github), filter == ZipKind.GITHUB, { filter = ZipKind.GITHUB })
            KChip(stringResource(R.string.zips_kind_ai), filter == ZipKind.AI, { filter = ZipKind.AI })
            KChip(stringResource(R.string.zips_kind_run), filter == ZipKind.RUN, { filter = ZipKind.RUN })
        }
        if (shown.isEmpty()) {
            Text(stringResource(R.string.zips_empty), style = MaterialTheme.typography.bodyMedium, color = k.muted)
        }
        shown.forEach { z ->
            NodeCard(modifier = Modifier.fillMaxWidth(), accent = if (z.kind == ZipKind.AI) NodeAccent.Green else NodeAccent.Blue) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(z.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    KBadge(
                        text = stringResource(
                            when (z.kind) {
                                ZipKind.GITHUB -> R.string.zips_kind_github
                                ZipKind.AI -> R.string.zips_kind_ai
                                ZipKind.RUN -> R.string.zips_kind_run
                            },
                        ),
                        accent = if (z.kind == ZipKind.AI) NodeAccent.Green else NodeAccent.Blue,
                    )
                }
                val meta = buildList {
                    add(fmt.format(Date(z.createdAt)))
                    add(Formatter.formatShortFileSize(context, z.size))
                    if (z.count >= 0) add(stringResource(R.string.zips_files, z.count))
                    if (z.detail.isNotBlank()) add(z.detail)
                }.joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = k.muted, modifier = Modifier.padding(top = 4.dp))
                FlowRow(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KButton(text = stringResource(R.string.zips_browse), onClick = { browsing = z to null })
                    KButton(
                        text = stringResource(R.string.zips_save),
                        style = KButtonStyle.Outline,
                        onClick = {
                            pendingSave = z
                            saver.launch("kripton-${z.kind.name.lowercase()}-${z.key}.zip")
                        },
                    )
                    if (z.archiveId != null) {
                        KButton(text = stringResource(R.string.action_delete), style = KButtonStyle.Danger, onClick = { confirmDelete = z })
                    }
                }
            }
        }
    }

    browsing?.let { (item, loaded) ->
        LaunchedEffect(item.key) {
            if (loaded == null) {
                val entries = withContext(Dispatchers.IO) {
                    runCatching { ZipArchive.readEntries(item.file.readBytes()).sortedBy { it.first } }.getOrDefault(emptyList())
                }
                browsing = item to entries
            }
        }
        ZipBrowserDialog(title = item.title, entries = loaded, onDismiss = { browsing = null })
    }

    confirmDelete?.let { z ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.zips_delete_title)) },
            text = { Text(stringResource(R.string.zips_delete_text, z.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    z.archiveId?.let { container.zipArchive.delete(it) }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Zip içeriği: dosya listesi; bir dosyaya dokununca metin önizlemesi (ilk 20.000 karakter). [entries] null: yükleniyor. */
@Composable
private fun ZipBrowserDialog(title: String, entries: List<Pair<String, ByteArray>>?, onDismiss: () -> Unit) {
    val k = KriptonTheme.colors
    var selected by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            val current = entries?.firstOrNull { it.first == selected }
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    entries == null -> Text(stringResource(R.string.zips_loading), color = k.muted)

                    entries.isEmpty() -> Text(stringResource(R.string.zips_open_failed), color = k.muted)

                    current != null -> {
                        TextButton(onClick = { selected = null }) { Text(stringResource(R.string.action_back)) }
                        Text(current.first, style = MaterialTheme.typography.labelLarge)
                        val bytes = current.second
                        val text = if (bytes.any { it == 0.toByte() }) {
                            stringResource(R.string.zips_binary)
                        } else {
                            bytes.toString(Charsets.UTF_8).let { if (it.length > 20_000) it.take(20_000) + "\n…" else it }
                        }
                        Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }

                    else -> entries.forEach { (path, bytes) ->
                        TextButton(onClick = { selected = path }, modifier = Modifier.fillMaxWidth()) {
                            Text(path, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            Text("${bytes.size / 1024 + 1} KB", style = MaterialTheme.typography.bodySmall, color = k.muted)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.zips_close)) } },
    )
}
