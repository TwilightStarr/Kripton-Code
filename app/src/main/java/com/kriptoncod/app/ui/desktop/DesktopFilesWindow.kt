package com.kriptoncod.app.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Açık bir AI penceresi: dosyanın gönderilebileceği hedef. */
data class AiTarget(val accountId: Long, val name: String)

/**
 * Dosyayı bir AI penceresine sürüklerken ortak durum: dosya yöneticisi başlatır ve parmağı izler,
 * kabuk parmağın altındaki pencereyi vurgular ve bırakınca dosyayı gönderir.
 * [pointer]: masaüstü alanına göre piksel konumu.
 */
@Stable
class FileDrag {
    var file by mutableStateOf<File?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set

    fun start(f: File, p: Offset) {
        file = f
        pointer = p
    }

    fun move(p: Offset) {
        pointer = p
    }

    /** Sürüklemeyi bitirir; bırakılan dosyayı verir (iptalde de dosya döner; çağıran konuma bakıp karar verir). */
    fun finish(): File? {
        val f = file
        file = null
        return f
    }
}

/**
 * Dosya yöneticisi penceresi. Tüm dosyalara erişim izni yoksa önce izin ister.
 * Bir dosya basılı tutulup sürüklenerek ya da satırdaki menüden açık bir AI penceresine gönderilir.
 *
 * @param areaOrigin masaüstü alanının ekrandaki (root) sol-üst köşesi; parmak konumunu alan koordinatına çevirmek için.
 */
@Composable
fun DesktopFilesWindow(
    drag: FileDrag,
    aiTargets: List<AiTarget>,
    areaOrigin: () -> Offset,
    onSend: (File, Long) -> Unit,
    /** Sürükleme bırakılınca çağrılır; parmağın son konumu [FileDrag.pointer]'dadır. */
    onDragDrop: (File) -> Unit,
) {
    val access = rememberFilesAccess()
    val root = remember { DesktopPlatform.storageRoot() }
    val homeLabel = stringResource(R.string.desktop_files_home)
    var dirPath by rememberSaveable { mutableStateOf(root.path) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    val dir = File(dirPath)

    val entries by produceState<List<FileEntry>?>(null, dirPath, showHidden, tick, access.granted) {
        value = withContext(Dispatchers.IO) { if (access.granted) FileBrowse.list(File(dirPath), showHidden) else emptyList() }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Toolbar(
            dir = dir,
            root = root,
            homeLabel = homeLabel,
            showHidden = showHidden,
            onNavigate = { dirPath = it.path },
            onToggleHidden = { showHidden = !showHidden },
            onRefresh = { tick++ },
        )
        HorizontalDivider(color = KriptonTheme.colors.nodeBorder.copy(alpha = 0.5f))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val list = entries
            when {
                !access.granted -> AccessCard(onGrant = access.request)

                list == null -> Unit

                list.isEmpty() -> Text(
                    stringResource(R.string.desktop_files_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KriptonTheme.colors.muted,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.file.path }) { e ->
                        FileRow(
                            entry = e,
                            drag = drag,
                            aiTargets = aiTargets,
                            areaOrigin = areaOrigin,
                            onOpenDir = { dirPath = e.file.path },
                            onSend = onSend,
                            onDragDrop = onDragDrop,
                        )
                    }
                }
            }
        }
        if (access.granted) {
            HorizontalDivider(color = KriptonTheme.colors.nodeBorder.copy(alpha = 0.5f))
            Text(
                stringResource(R.string.desktop_files_hint),
                style = MaterialTheme.typography.labelSmall,
                color = KriptonTheme.colors.muted,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Toolbar(
    dir: File,
    root: File,
    homeLabel: String,
    showHidden: Boolean,
    onNavigate: (File) -> Unit,
    onToggleHidden: () -> Unit,
    onRefresh: () -> Unit,
) {
    val k = KriptonTheme.colors
    val parent = FileBrowse.parentWithin(dir, root)
    val crumbs = FileBrowse.breadcrumbs(dir, root, homeLabel)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { parent?.let(onNavigate) }, enabled = parent != null, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.desktop_files_up), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { onNavigate(root) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Home, contentDescription = homeLabel, modifier = Modifier.size(20.dp))
            }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                crumbs.forEachIndexed { i, (label, f) ->
                    if (i > 0) Text("›", color = k.muted, modifier = Modifier.padding(horizontal = 2.dp))
                    TextButton(onClick = { onNavigate(f) }) { Text(label, maxLines = 1) }
                }
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.desktop_files_refresh), modifier = Modifier.size(20.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sık kullanılan klasörler (yalnız var olanlar).
            listOf("Download", "Documents", "Pictures", "DCIM").map { File(root, it) }.filter { it.isDirectory }.forEach { f ->
                FilterChip(
                    selected = FileBrowse.isInside(dir, f),
                    onClick = { onNavigate(f) },
                    label = { Text(f.name, maxLines = 1) },
                )
            }
            FilterChip(
                selected = showHidden,
                onClick = onToggleHidden,
                label = { Text(stringResource(R.string.desktop_files_hidden), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun AccessCard(onGrant: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
        NodeCard(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.desktop_files_need_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.desktop_files_need_body),
                style = MaterialTheme.typography.bodySmall,
                color = KriptonTheme.colors.muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            KButton(text = stringResource(R.string.desktop_files_grant), onClick = onGrant, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FileRow(
    entry: FileEntry,
    drag: FileDrag,
    aiTargets: List<AiTarget>,
    areaOrigin: () -> Offset,
    onOpenDir: () -> Unit,
    onSend: (File, Long) -> Unit,
    onDragDrop: (File) -> Unit,
) {
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme
    var rowRoot by remember { mutableStateOf(Offset.Zero) }
    val originNow by rememberUpdatedState(areaOrigin)
    val dropNow by rememberUpdatedState(onDragDrop)
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val dragging = drag.file == entry.file
    var menu by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(if (dragging) k.nodeFill else scheme.background)
            .onGloballyPositioned { rowRoot = it.positionInRoot() }
            .then(
                if (entry.isDir) {
                    Modifier.clickable(onClick = onOpenDir)
                } else {
                    // Dosya: basılı tutup sürükle -> AI penceresine bırak.
                    Modifier.pointerInput(entry.file.path) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { local -> drag.start(entry.file, rowRoot + local - originNow()) },
                            onDrag = { change, _ ->
                                change.consume()
                                drag.move(rowRoot + change.position - originNow())
                            },
                            onDragEnd = { drag.finish()?.let { f -> dropNow(f) } },
                            onDragCancel = { drag.finish() },
                        )
                    }
                },
            )
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.isDir) FolderGlyph(tint = scheme.primary) else FileGlyph(tint = k.muted)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!entry.isDir) {
                Text(
                    FileBrowse.humanSize(entry.size) + "  ·  " + dateFormat.format(Date(entry.modified)),
                    style = MaterialTheme.typography.labelSmall,
                    color = k.muted,
                    maxLines = 1,
                )
            }
        }
        if (!entry.isDir) {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.desktop_files_send_menu), modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (aiTargets.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.desktop_files_no_ai), style = MaterialTheme.typography.bodySmall) },
                            onClick = { menu = false },
                            enabled = false,
                        )
                    } else {
                        aiTargets.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.desktop_files_send_to, t.name), maxLines = 1) },
                                onClick = {
                                    menu = false
                                    onSend(entry.file, t.accountId)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    HorizontalDivider(color = k.nodeBorder.copy(alpha = 0.25f))
}
