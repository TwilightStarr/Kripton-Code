package com.kriptoncod.app.ui.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kriptoncod.app.R
import com.kriptoncod.app.data.files.AtomicFiles
import com.kriptoncod.app.launcher.ui.LocalInAppMessage
import com.kriptoncod.app.ui.desktop.FileOpen
import com.kriptoncod.app.ui.theme.KriptonTheme
import com.kriptoncod.app.ui.theme.ScopedTheme
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Hızlı başlangıç dosya adları (çalışma alanı boşken önerilir). */
private val STARTERS = listOf("main.kt", "index.html", "script.py", "notlar.md", "veri.json")

/**
 * Editör sekmesi: kendi temasıyla (Kripton/Obsidyen/Kehribar/Zümrüt/Kırağı/Kum) çok sekmeli kod düzenleyici ve çalışma alanı dosyaları.
 * Açık sekmeler ve kaydedilmemiş metin sekmeler/ekranlar arasında korunur ([EditorSession], [EditorDrafts]).
 */
@Composable
fun EditorScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    remember { EditorSession.ensureLoaded(context) }
    var theme by remember { mutableStateOf(EditorThemes.load(context)) }
    val onTheme: (EditorTheme) -> Unit = {
        theme = it
        EditorThemes.save(context, it)
    }
    val spec = theme.spec
    if (spec == null) {
        EditorWorkspaceContent(theme, onTheme, modifier)
    } else {
        ScopedTheme(spec) { EditorWorkspaceContent(theme, onTheme, modifier) }
    }
}

@Composable
private fun EditorWorkspaceContent(theme: EditorTheme, onTheme: (EditorTheme) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val message = LocalInAppMessage.current
    val cs = MaterialTheme.colorScheme
    val k = KriptonTheme.colors

    val root = remember { EditorWorkspace.root(context) }
    var dir by remember { mutableStateOf(root) }
    val curDir = dir.takeIf { it.isDirectory && EditorWorkspace.isInside(root, it) } ?: root
    var tick by remember { mutableIntStateOf(0) }
    var showFiles by remember { mutableStateOf(false) }
    var activeModified by remember { mutableStateOf(false) }

    var showThemes by remember { mutableStateOf(false) }
    var showNewFile by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var export by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }

    val tabs = EditorSession.open
    val active = EditorSession.active
    val filesVisible = showFiles || tabs.isEmpty() || active == null

    val exportOk = stringResource(R.string.editor_exported)
    val exportFail = stringResource(R.string.editor_export_failed)
    val importFail = stringResource(R.string.editor_import_failed)
    val createFail = stringResource(R.string.editor_create_failed)
    val existsMsg = stringResource(R.string.editor_exists)
    val invalidMsg = stringResource(R.string.editor_name_invalid)
    val importedFmt = stringResource(R.string.editor_imported)
    val externalFail = stringResource(R.string.desktop_files_unreadable)
    val themeDesc = stringResource(R.string.editor_theme)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val e = export
        export = null
        if (uri != null && e != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(e.second) } != null }
                        .getOrDefault(false)
                }
                message(if (ok) exportOk else exportFail)
            }
        }
    }
    fun startExport(name: String, bytes: ByteArray) {
        export = name to bytes
        exportLauncher.launch(name)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val target = curDir
            scope.launch {
                val n = withContext(Dispatchers.IO) { importUris(context, target, uris) }
                tick++
                message(if (n > 0) String.format(importedFmt, n) else importFail)
            }
        }
    }

    fun openFile(f: File) {
        EditorSession.openFile(context, f.path)
        showFiles = false
    }

    fun createFile(name: String) {
        if (!EditorWorkspace.validName(name)) {
            message(invalidMsg)
            return
        }
        val f = File(curDir, name.trim())
        if (f.exists()) {
            message(existsMsg)
            return
        }
        runCatching { AtomicFiles.write(f, ByteArray(0)) }
            .onSuccess {
                tick++
                openFile(f)
            }
            .onFailure { message(createFail) }
    }

    fun createFolder(name: String) {
        if (!EditorWorkspace.validName(name)) {
            message(invalidMsg)
            return
        }
        val f = File(curDir, name.trim())
        if (f.exists()) {
            message(existsMsg)
            return
        }
        if (f.mkdirs()) {
            tick++
            dir = f
        } else {
            message(createFail)
        }
    }

    fun rename(target: File, newName: String) {
        if (!EditorWorkspace.validName(newName)) {
            message(invalidMsg)
            return
        }
        val dest = File(target.parentFile ?: curDir, newName.trim())
        if (dest.exists()) {
            message(existsMsg)
            return
        }
        if (!target.renameTo(dest)) {
            message(createFail)
            return
        }
        if (target.isDirectory || dest.isDirectory) {
            val prefix = target.path + File.separator
            EditorSession.open.toList().filter { it.startsWith(prefix) }
                .forEach { EditorSession.renamed(context, it, dest.path + File.separator + it.removePrefix(prefix)) }
        } else {
            EditorSession.renamed(context, target.path, dest.path)
        }
        tick++
    }

    fun delete(target: File) {
        EditorSession.forget(context, target.path)
        target.deleteRecursively()
        tick++
    }

    fun isDirty(path: String): Boolean = EditorDrafts.has(path) || (path == active && !filesVisible && activeModified)

    Column(modifier.fillMaxSize().background(cs.background)) {
        // --- başlık ---
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(cs.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹›", color = cs.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Text(
                stringResource(R.string.nav_editor),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            if (tabs.isNotEmpty()) {
                IconButton(onClick = { showFiles = !filesVisible }) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.editor_files),
                        tint = if (filesVisible) cs.primary else k.muted,
                    )
                }
            }
            IconButton(onClick = { showNewFile = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.editor_new_file), tint = k.muted)
            }
            IconButton(onClick = { showThemes = true }, modifier = Modifier.semantics { contentDescription = themeDesc }) {
                Text("◐", color = k.muted, fontSize = 20.sp)
            }
        }

        // --- sekme şeridi ---
        if (tabs.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                for (path in tabs.toList()) {
                    EditorTab(
                        name = File(path).name,
                        selected = path == active && !filesVisible,
                        dirty = isDirty(path),
                        onClick = {
                            EditorSession.select(context, path)
                            showFiles = false
                        },
                    )
                }
            }
        }
        HorizontalDivider(color = k.nodeBorder)

        // --- gövde ---
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val a = active
            if (filesVisible || a == null) {
                val entries = remember(curDir, tick) { EditorWorkspace.list(curDir) }
                FilesPanel(
                    root = root,
                    dir = curDir,
                    entries = entries,
                    onDir = { dir = it },
                    onUp = { curDir.parentFile?.let { p -> if (EditorWorkspace.isInside(root, p)) dir = p } },
                    onOpen = ::openFile,
                    onNewFolder = { showNewFolder = true },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onStarter = { name -> createFile(EditorWorkspace.uniqueFile(curDir, name).name) },
                    onRename = { renameTarget = it },
                    onDelete = { deleteTarget = it },
                    onExport = { f ->
                        scope.launch {
                            val bytes = withContext(Dispatchers.IO) {
                                if (f.length() > MAX_EXPORT_BYTES) null else runCatching { f.readBytes() }.getOrNull()
                            }
                            if (bytes == null) message(exportFail) else startExport(f.name, bytes)
                        }
                    },
                )
            } else {
                val file = File(a)
                val exportLabel = stringResource(R.string.editor_export)
                key(a) {
                    CodeEditorPane(
                        file = file,
                        onClose = { EditorSession.close(context, a) },
                        onOpenExternal = { if (!FileOpen.launch(context, file, null)) message(externalFail) },
                        syntaxTheme = theme.syntax,
                        embedded = true,
                        onModifiedChange = { activeModified = it },
                        extraMenu = listOf(
                            EditorMenuAction(exportLabel) { text -> startExport(file.name, EditorSupport.encode(text, false)) },
                        ),
                    )
                }
            }
        }
    }

    // --- iletişim kutuları ---
    if (showThemes) {
        ThemeDialog(current = theme, onPick = {
            onTheme(it)
            showThemes = false
        }, onDismiss = { showThemes = false })
    }
    if (showNewFile) {
        NameDialog(
            title = stringResource(R.string.editor_new_file),
            initial = "",
            confirmLabel = stringResource(R.string.editor_create),
            onDismiss = { showNewFile = false },
            onConfirm = {
                showNewFile = false
                createFile(it)
            },
        )
    }
    if (showNewFolder) {
        NameDialog(
            title = stringResource(R.string.editor_new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.editor_create),
            onDismiss = { showNewFolder = false },
            onConfirm = {
                showNewFolder = false
                createFolder(it)
            },
        )
    }
    renameTarget?.let { target ->
        NameDialog(
            title = stringResource(R.string.editor_rename),
            initial = target.name,
            confirmLabel = stringResource(R.string.editor_rename),
            onDismiss = { renameTarget = null },
            onConfirm = {
                renameTarget = null
                rename(target, it)
            },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.editor_delete)) },
            text = { Text(stringResource(R.string.editor_delete_confirm, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    delete(target)
                }) {
                    Text(stringResource(R.string.editor_delete), color = k.danger)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.editor_cancel)) } },
        )
    }
}

private const val MAX_EXPORT_BYTES = 32L * 1024 * 1024

/** Seçilen belgeleri [dir] içine kopyalar (var olanı ezmez); kopyalanan sayısını döndürür. */
private fun importUris(context: Context, dir: File, uris: List<Uri>): Int {
    var ok = 0
    for (uri in uris) {
        val raw = displayName(context, uri).orEmpty().substringAfterLast('/').trim()
        val dest = EditorWorkspace.uniqueFile(dir, if (EditorWorkspace.validName(raw)) raw else "dosya")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                AtomicFiles.write(dest) { out -> input.copyTo(out) }
                true
            } ?: false
        }.getOrDefault(false)
        if (copied) ok++
    }
    return ok
}

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment

@Composable
private fun EditorTab(name: String, selected: Boolean, dirty: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val k = KriptonTheme.colors
    val accent = cs.primary
    Row(
        Modifier
            .background(if (selected) k.nodeFill else Color.Transparent)
            .clickable(onClick = onClick)
            .drawBehind {
                if (selected) drawRect(accent, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx()))
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dirty) Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) cs.onBackground else k.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
    }
}

@Composable
private fun FilesPanel(
    root: File,
    dir: File,
    entries: List<File>,
    onDir: (File) -> Unit,
    onUp: () -> Unit,
    onOpen: (File) -> Unit,
    onNewFolder: () -> Unit,
    onImport: () -> Unit,
    onStarter: (String) -> Unit,
    onRename: (File) -> Unit,
    onDelete: (File) -> Unit,
    onExport: (File) -> Unit,
) {
    val k = KriptonTheme.colors
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                EditorWorkspace.display(root, dir),
                style = MaterialTheme.typography.labelLarge,
                color = k.muted,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (dir.path != root.path) TextButton(onClick = onUp) { Text(stringResource(R.string.editor_up)) }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionChip(stringResource(R.string.editor_new_folder), onNewFolder)
            ActionChip(stringResource(R.string.editor_import), onImport)
        }
        if (entries.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                Text(stringResource(R.string.editor_empty_title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.editor_empty_msg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = k.muted,
                    textAlign = TextAlign.Center,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in STARTERS) ActionChip(s) { onStarter(s) }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(top = 6.dp)) {
                items(entries, key = { it.path }) { f ->
                    FileRow(f, onClick = { if (f.isDirectory) onDir(f) else onOpen(f) }, onRename = { onRename(f) }, onDelete = { onDelete(f) }, onExport = { onExport(f) })
                }
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    val k = KriptonTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, k.nodeBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun FileRow(file: File, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onExport: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val k = KriptonTheme.colors
    var menu by remember { mutableStateOf(false) }
    val isDir = file.isDirectory
    val ext = file.name.substringAfterLast('.', "").take(3).uppercase()
    val subtitle = remember(file.path, file.lastModified()) {
        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(file.lastModified()))
        if (isDir) date else Formatter.formatShortFileSize(context, file.length()) + " · " + date
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(k.nodeFill).border(1.dp, k.nodeBorder, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isDir) "▸" else ext.ifEmpty { "·" },
                color = cs.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = k.muted, maxLines = 1)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = null, tint = k.muted) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.editor_rename)) }, onClick = {
                    menu = false
                    onRename()
                })
                if (!isDir) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.editor_export)) }, onClick = {
                        menu = false
                        onExport()
                    })
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.editor_delete), color = k.danger) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val valid = EditorWorkspace.validName(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.editor_name_hint)) },
                isError = text.isNotEmpty() && !valid,
                supportingText = { if (text.isNotEmpty() && !valid) Text(stringResource(R.string.editor_name_invalid)) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = valid) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
    )
}

@Composable
private fun ThemeDialog(current: EditorTheme, onPick: (EditorTheme) -> Unit, onDismiss: () -> Unit) {
    val k = KriptonTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_theme)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                for (t in EditorThemes.all) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(t) }.padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (c in t.swatch) Box(Modifier.size(14.dp).clip(CircleShape).background(c).border(1.dp, k.nodeBorder, CircleShape))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(t.label, style = MaterialTheme.typography.bodyLarge)
                            if (t.spec == null) {
                                Text(stringResource(R.string.editor_theme_auto), style = MaterialTheme.typography.labelSmall, color = k.muted)
                            }
                        }
                        if (t.id == current.id) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_close)) } },
    )
}
