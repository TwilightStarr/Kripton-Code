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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.kriptoncod.app.data.files.ArchiveCancelled
import com.kriptoncod.app.data.files.ArchiveFormat
import com.kriptoncod.app.data.files.ArchiveProgress
import com.kriptoncod.app.data.files.ArchiveTool
import com.kriptoncod.app.launcher.ui.LocalInAppMessage
import androidx.compose.material3.LinearProgressIndicator
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.data.shizuku.PrivFail
import com.kriptoncod.app.data.shizuku.PrivListing
import com.kriptoncod.app.data.shizuku.PrivMode
import com.kriptoncod.app.data.shizuku.ShizukuFiles
import com.kriptoncod.app.data.shizuku.ShizukuState
import com.kriptoncod.app.data.shizuku.ShizukuStatus
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
    /** Ayarlar'da "Shizuku ile sistem dosyaları" açık mı (varsayılan kapalı: pencere eskisi gibi yalnız depolamayı gösterir). */
    shizukuEnabled: Boolean = false,
    /** Penceredeki ipucu yazısı (Ayarlar'dan açılır; varsayılan kapalı) ve ilk görünüm varsayılanları. */
    showHints: Boolean = false,
    defaultHidden: Boolean = false,
    defaultExt: Boolean = true,
    defaultGrid: Boolean = false,
) {
    val context = LocalContext.current
    val access = rememberFilesAccess()
    val storageRoot = remember { DesktopPlatform.storageRoot() }
    val systemRoot = remember { File("/") }
    val homeLabel = stringResource(R.string.desktop_files_home)
    val systemLabel = stringResource(R.string.desktop_files_system_root)
    var sourceKey by rememberSaveable { mutableStateOf(FileSource.STORAGE.name) }
    val source = if (shizukuEnabled) FileSource.valueOf(sourceKey) else FileSource.STORAGE
    val root = if (source == FileSource.SYSTEM) systemRoot else storageRoot
    var dirPath by rememberSaveable { mutableStateOf(storageRoot.path) }
    var hiddenOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var extOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var gridOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val showHidden = hiddenOverride ?: defaultHidden
    val showExt = extOverride ?: defaultExt
    val gridView = gridOverride ?: defaultGrid
    var tick by remember { mutableIntStateOf(0) }
    val dir = File(dirPath)
    val sz by ShizukuFiles.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (shizukuEnabled) ShizukuFiles.refresh() }

    // Ayar kapanınca sistem yolunda kalma: depolamaya dön.
    LaunchedEffect(shizukuEnabled) {
        if (!shizukuEnabled && !FileBrowse.isInside(File(dirPath), storageRoot)) dirPath = storageRoot.path
    }

    fun pickSource(next: FileSource) {
        sourceKey = next.name
        dirPath = when (next) {
            FileSource.STORAGE -> storageRoot.path
            FileSource.SYSTEM -> if (sz.mode == PrivMode.ROOT) "/data" else storageRoot.path
            FileSource.APPS -> dirPath
        }
    }

    val listing by produceState<FilesListing?>(null, dirPath, showHidden, tick, access.granted, source, sz.status, sz.uid) {
        value = null
        value = withContext(Dispatchers.IO) {
            when (source) {
                FileSource.STORAGE ->
                    FilesListing.Entries(if (access.granted) FileBrowse.list(File(dirPath), showHidden) else emptyList())

                FileSource.SYSTEM -> when (val r = ShizukuFiles.list(dirPath, showHidden)) {
                    is PrivListing.Ok -> FilesListing.Entries(r.entries.map { FileBrowse.fromPriv(File(dirPath), it) })
                    // Shizuku yok / izin yok / erişim reddedildi: normal erişimle okunabiliyorsa onu göster (zarif geri düşüş).
                    is PrivListing.Fail -> FileBrowse.list(File(dirPath), showHidden).takeIf { it.isNotEmpty() }
                        ?.let { FilesListing.Entries(it) } ?: FilesListing.Failed(r.reason)
                }

                FileSource.APPS -> {
                    val own = context.packageName
                    val pkgs = ShizukuFiles.packages().ifEmpty { listOf(own) }
                    FilesListing.Packages(if (own in pkgs) listOf(own) + (pkgs - own) else pkgs)
                }
            }
        }
    }

    fun appPath(pkg: String): String =
        if (pkg == context.packageName) context.applicationInfo.dataDir else ShizukuFiles.appDataPath(pkg, storageRoot.path)

    val scope = rememberCoroutineScope()
    var viewing by remember { mutableStateOf<Pair<File, FileKind>?>(null) }
    var openWith by remember { mutableStateOf<File?>(null) }
    var renaming by remember { mutableStateOf<File?>(null) }
    var deleting by remember { mutableStateOf<File?>(null) }
    var props by remember { mutableStateOf<FileEntry?>(null) }
    var browsing by remember { mutableStateOf<File?>(null) }
    var job by remember { mutableStateOf<ArchiveJob?>(null) }
    var clip by remember { mutableStateOf<Pair<File, Boolean>?>(null) } // (öğe, taşınacak mı)
    val privCache = remember { File(context.cacheDir, "privview") }
    DisposableEffect(Unit) { onDispose { runCatching { privCache.deleteRecursively() } } }

    val message = LocalInAppMessage.current
    fun say(text: String) = message(text)

    /** Dosya doğrudan okunabiliyorsa kendisi; değilse (Shizuku) önbelleğe kopyası. Yoksa null. */
    suspend fun readable(f: File): File? = withContext(Dispatchers.IO) {
        if (f.canRead()) {
            f
        } else {
            val size = ShizukuFiles.sizeOf(f.path)
            if (size == null || size > MAX_VIEW_COPY) {
                null
            } else {
                runCatching {
                    privCache.mkdirs()
                    val dest = File(privCache, f.name)
                    ShizukuFiles.openRead(f.path)?.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                    dest.takeIf { it.isFile }
                }.getOrNull()
            }
        }
    }

    /** Arşiv işi (açma/sıkıştırma): arka planda çalışır, alttaki çubukta ilerler, iptal edilebilir. Aynı anda tek iş. */
    fun runJob(label: String, work: (ArchiveProgress, () -> Boolean) -> Result<Any>) {
        if (job != null) return say(context.getString(R.string.l_ar_busy))
        val cancel = AtomicBoolean(false)
        job = ArchiveJob(label, 0f, cancel)
        scope.launch {
            var lastPercent = -1
            val result = withContext(Dispatchers.IO) {
                work(ArchiveProgress { d, t ->
                    val p = if (t > 0) (d.toFloat() / t).coerceIn(0f, 1f) else -1f
                    val pct = (p * 100).toInt()
                    if (pct != lastPercent) {
                        lastPercent = pct
                        job = job?.copy(progress = p)
                    }
                }, { cancel.get() })
            }
            job = null
            result.onSuccess { say(context.getString(R.string.l_ar_done, label)) }
                .onFailure { say(if (it is ArchiveCancelled) context.getString(R.string.l_ar_cancelled) else it.message ?: context.getString(R.string.desktop_files_op_failed)) }
            tick++
        }
    }

    fun extractTo(archive: File, target: File, only: Set<String>?) {
        runJob(context.getString(R.string.l_ar_extracting, archive.name)) { prog, cancelled -> ArchiveTool.extract(archive, target, only, prog, cancelled) }
    }

    fun compress(src: File, format: ArchiveFormat) {
        val ext = if (format == ArchiveFormat.ZIP) ".zip" else ".tar.gz"
        runJob(context.getString(R.string.l_ar_compressing, src.name)) { prog, cancelled ->
            ArchiveTool.compress(listOf(src), File(dir, src.name + ext), format, 6, prog, cancelled)
        }
    }

    fun previewEntry(archive: File, e: com.kriptoncod.app.data.files.ArchiveEntry) {
        scope.launch {
            val out = File(privCache, "ar-" + System.currentTimeMillis() + "-" + e.name)
            val r = withContext(Dispatchers.IO) { ArchiveTool.extractEntry(archive, e.path, out) }
            r.onSuccess { f ->
                val kind = FileOps.kindOf(f.name)
                if (kind == FileKind.OTHER || kind == FileKind.ARCHIVE) openWith = f else viewing = f to kind
                browsing = null
            }.onFailure { say(it.message ?: context.getString(R.string.desktop_files_op_failed)) }
        }
    }

    fun openFile(e: FileEntry) {
        scope.launch {
            val f = readable(e.file) ?: return@launch say(context.getString(R.string.desktop_files_unreadable))
            val kind = FileOps.kindOf(f.name)
            when {
                kind == FileKind.ARCHIVE && ArchiveTool.isSupported(f.name) -> browsing = f
                kind == FileKind.TEXT || kind == FileKind.MARKDOWN || kind == FileKind.PDF || kind == FileKind.IMAGE -> viewing = f to kind
                else -> {
                    val pkg = FileOpen.remembered(context, f)
                    if (pkg == null || !FileOpen.launch(context, f, pkg)) openWith = f
                }
            }
        }
    }

    fun act(a: FileAction, e: FileEntry) {
        when (a) {
            FileAction.OPEN -> if (e.isDir) dirPath = e.file.path else openFile(e)
            FileAction.OPEN_WITH -> scope.launch { readable(e.file)?.let { openWith = it } ?: say(context.getString(R.string.desktop_files_unreadable)) }
            FileAction.COPY -> { clip = e.file to false; say(context.getString(R.string.desktop_files_copied, e.name)) }
            FileAction.MOVE -> { clip = e.file to true; say(context.getString(R.string.desktop_files_cut, e.name)) }
            FileAction.RENAME -> renaming = e.file
            FileAction.DELETE -> deleting = e.file
            FileAction.PROPS -> props = e
            FileAction.BROWSE_ARCHIVE -> browsing = e.file
            FileAction.EXTRACT_HERE -> extractTo(e.file, dir, null)
            FileAction.EXTRACT_FOLDER -> extractTo(e.file, FileOps.uniqueTarget(dir, ArchiveTool.baseName(e.name)), null)
            FileAction.COMPRESS_ZIP -> compress(e.file, ArchiveFormat.ZIP)
            FileAction.COMPRESS_TGZ -> compress(e.file, ArchiveFormat.TAR_GZ)
        }
    }

    fun paste() {
        val (f, move) = clip ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) { if (move) FileOps.move(f, dir) else FileOps.copy(f, dir) }
            r.onFailure { say(it.message ?: context.getString(R.string.desktop_files_op_failed)) }
            if (r.isSuccess) {
                if (move) clip = null
                tick++
            }
        }
    }

    val shownViewing = viewing
    if (shownViewing != null) {
        FileViewer(
            file = shownViewing.first,
            kind = shownViewing.second,
            onClose = { viewing = null },
            onOpenExternal = { openWith = shownViewing.first },
        )
    } else {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (shizukuEnabled) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = source == FileSource.STORAGE,
                        onClick = { pickSource(FileSource.STORAGE) },
                        label = { Text(stringResource(R.string.desktop_files_src_storage), maxLines = 1) },
                    )
                    FilterChip(
                        selected = source == FileSource.SYSTEM,
                        onClick = { pickSource(FileSource.SYSTEM) },
                        label = { Text(stringResource(R.string.desktop_files_src_system), maxLines = 1) },
                    )
                    FilterChip(
                        selected = source == FileSource.APPS,
                        onClick = { pickSource(FileSource.APPS) },
                        label = { Text(stringResource(R.string.desktop_files_src_apps), maxLines = 1) },
                    )
                }
            }
            if (source != FileSource.APPS) {
                Toolbar(
                    dir = dir,
                    root = root,
                    homeLabel = if (source == FileSource.SYSTEM) systemLabel else homeLabel,
                    showHidden = showHidden,
                    showExt = showExt,
                    gridView = gridView,
                    quickDirs = if (source == FileSource.SYSTEM) {
                        buildList {
                            if (sz.mode == PrivMode.ROOT) add("/data/data" to File("/data/data"))
                            add("Android/data" to File(storageRoot, "Android/data"))
                            add("/data/local/tmp" to File("/data/local/tmp"))
                        }
                    } else {
                        listOf("Download", "Documents", "Pictures", "DCIM").map { it to File(storageRoot, it) }
                    },
                    onNavigate = { dirPath = it.path },
                    onToggleHidden = { hiddenOverride = !showHidden },
                    onToggleExt = { extOverride = !showExt },
                    onToggleGrid = { gridOverride = !gridView },
                    onRefresh = { tick++ },
                )
            }
            HorizontalDivider(color = KriptonTheme.colors.nodeBorder.copy(alpha = 0.5f))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val l = listing
                when {
                    source == FileSource.STORAGE && !access.granted -> AccessCard(onGrant = access.request)

                    l == null -> Unit

                    l is FilesListing.Packages -> LazyColumn(Modifier.fillMaxSize()) {
                        items(l.names, key = { it }) { pkg ->
                            Column(
                                Modifier.fillMaxWidth().clickable {
                                    sourceKey = FileSource.SYSTEM.name
                                    dirPath = appPath(pkg)
                                }.padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(pkg, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            HorizontalDivider(color = KriptonTheme.colors.nodeBorder.copy(alpha = 0.25f))
                        }
                    }

                    l is FilesListing.Failed -> PrivProblem(l.reason, sz, onRetry = { tick++ })

                    l is FilesListing.Entries && l.list.isEmpty() -> Text(
                        stringResource(R.string.desktop_files_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = KriptonTheme.colors.muted,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    l is FilesListing.Entries && gridView -> LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.fillMaxSize()) {
                        gridItems(l.list, key = { it.file.path }) { e ->
                            FileItem(e, true, showExt, drag, aiTargets, areaOrigin, { dirPath = e.file.path }, onSend, onDragDrop, source == FileSource.STORAGE) { a -> act(a, e) }
                        }
                    }

                    l is FilesListing.Entries -> LazyColumn(Modifier.fillMaxSize()) {
                        items(l.list, key = { it.file.path }) { e ->
                            FileItem(e, false, showExt, drag, aiTargets, areaOrigin, { dirPath = e.file.path }, onSend, onDragDrop, source == FileSource.STORAGE) { a -> act(a, e) }
                        }
                    }
                }
            }
            job?.let { j ->
                HorizontalDivider(color = KriptonTheme.colors.nodeBorder.copy(alpha = 0.5f))
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(j.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        TextButton(onClick = { j.cancel.set(true) }) { Text(stringResource(R.string.desktop_files_cancel), maxLines = 1) }
                    }
                    if (j.progress < 0f) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { j.progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            val pending = clip
            if (pending != null && source == FileSource.STORAGE) {
                HorizontalDivider(color = KriptonTheme.colors.nodeBorder.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (pending.second) R.string.desktop_files_clip_move else R.string.desktop_files_clip_copy, pending.first.name),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { paste() }) { Text(stringResource(R.string.desktop_files_paste), maxLines = 1) }
                    TextButton(onClick = { clip = null }) { Text(stringResource(R.string.desktop_files_cancel), maxLines = 1) }
                }
            }
            if (showHints && (access.granted || source != FileSource.STORAGE)) {
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

    openWith?.let { f -> OpenWithDialog(f, onDismiss = { openWith = null }, onFailed = { say(context.getString(R.string.desktop_files_no_app)) }) }
    renaming?.let { f ->
        RenameDialog(f, onDismiss = { renaming = null }, onConfirm = { n ->
            renaming = null
            scope.launch {
                val r = withContext(Dispatchers.IO) { FileOps.rename(f, n) }
                r.onFailure { say(it.message ?: context.getString(R.string.desktop_files_op_failed)) }
                tick++
            }
        })
    }
    deleting?.let { f ->
        DeleteDialog(f, onDismiss = { deleting = null }, onConfirm = {
            deleting = null
            scope.launch {
                val ok = withContext(Dispatchers.IO) { FileOps.delete(f) }
                if (!ok) say(context.getString(R.string.desktop_files_op_failed))
                if (clip?.first == f) clip = null
                tick++
            }
        })
    }
    props?.let { e -> PropsDialog(e, onDismiss = { props = null }) }
    browsing?.let { a ->
        ArchiveBrowserDialog(
            archive = a,
            onExtractSelected = { sel -> browsing = null; extractTo(a, FileOps.uniqueTarget(dir, ArchiveTool.baseName(a.name)), sel) },
            onExtractAll = { browsing = null; extractTo(a, FileOps.uniqueTarget(dir, ArchiveTool.baseName(a.name)), null) },
            onPreview = { e -> previewEntry(a, e) },
            onDismiss = { browsing = null },
        )
    }
}

/** Dosyalar penceresinin kaynağı: normal depolama, Shizuku ile sistem (/), ya da uygulama veri klasörleri. */
private const val MAX_VIEW_COPY = 200L * 1024 * 1024

private enum class FileSource { STORAGE, SYSTEM, APPS }

private sealed interface FilesListing {
    data class Entries(val list: List<FileEntry>) : FilesListing
    data class Packages(val names: List<String>) : FilesListing
    data class Failed(val reason: PrivFail) : FilesListing
}

/** Shizuku listeleyemediğinde: neyin eksik olduğunu ve ne yapılacağını söyler. */
@Composable
private fun PrivProblem(reason: PrivFail, sz: ShizukuState, onRetry: () -> Unit) {
    val context = LocalContext.current
    val body = when {
        sz.status == ShizukuStatus.NOT_INSTALLED -> R.string.shizuku_status_not_installed
        sz.status == ShizukuStatus.NOT_RUNNING -> R.string.shizuku_status_not_running
        sz.status == ShizukuStatus.NEEDS_PERMISSION -> R.string.shizuku_status_needs_permission
        reason == PrivFail.DENIED -> if (sz.mode == PrivMode.ROOT) R.string.desktop_files_denied_root else R.string.desktop_files_denied_adb
        reason == PrivFail.NOT_FOUND -> R.string.desktop_files_not_found
        reason == PrivFail.NOT_DIR -> R.string.desktop_files_not_dir
        else -> R.string.desktop_files_priv_error
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
        NodeCard(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
            when (sz.status) {
                ShizukuStatus.NOT_RUNNING -> KButton(
                    text = stringResource(R.string.shizuku_action_open),
                    onClick = { ShizukuFiles.launchShizukuApp(context) },
                    modifier = Modifier.fillMaxWidth(),
                )

                ShizukuStatus.NEEDS_PERMISSION -> KButton(
                    text = stringResource(R.string.shizuku_action_grant),
                    onClick = { if (!ShizukuFiles.requestPermission()) ShizukuFiles.launchShizukuApp(context) },
                    modifier = Modifier.fillMaxWidth(),
                )

                else -> KButton(text = stringResource(R.string.desktop_files_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Toolbar(
    dir: File,
    root: File,
    homeLabel: String,
    showHidden: Boolean,
    showExt: Boolean,
    gridView: Boolean,
    quickDirs: List<Pair<String, File>>,
    onNavigate: (File) -> Unit,
    onToggleHidden: () -> Unit,
    onToggleExt: () -> Unit,
    onToggleGrid: () -> Unit,
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
            // Sistem kaynağında klasörler uygulama tarafından görülemeyebilir (örn. /data/data); o yüzden varlık denetlenmez.
            quickDirs.filter { (_, f) -> f.path.startsWith("/data") || f.isDirectory }.forEach { (label, f) ->
                FilterChip(
                    selected = FileBrowse.isInside(dir, f),
                    onClick = { onNavigate(f) },
                    label = { Text(label, maxLines = 1) },
                )
            }
            FilterChip(
                selected = showHidden,
                onClick = onToggleHidden,
                label = { Text(stringResource(R.string.desktop_files_hidden), maxLines = 1) },
            )
            FilterChip(
                selected = showExt,
                onClick = onToggleExt,
                label = { Text(stringResource(R.string.desktop_files_show_ext), maxLines = 1) },
            )
            FilterChip(
                selected = gridView,
                onClick = onToggleGrid,
                label = { Text(stringResource(R.string.desktop_files_grid), maxLines = 1) },
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

/** Uzantıya göre rozet rengi. */
private fun badgeColor(ext: String): Color = when (ext) {
    "kt", "kts", "java" -> Color(0xFF8E5BD9)
    "pdf" -> Color(0xFFD64545)
    "zip", "rar", "7z", "tar", "gz" -> Color(0xFFD9A400)
    "png", "jpg", "jpeg", "gif", "webp", "svg" -> Color(0xFF2E9E6B)
    "mp3", "wav", "mp4", "mkv" -> Color(0xFFE0762E)
    "doc", "docx" -> Color(0xFF3B6FD6)
    "xls", "xlsx", "csv" -> Color(0xFF2E8B57)
    "ppt", "pptx" -> Color(0xFFD2622A)
    "json", "xml", "yml", "yaml", "toml" -> Color(0xFF3A9AB8)
    "txt", "md", "log" -> Color(0xFF7A869A)
    "apk" -> Color(0xFF4CAF50)
    else -> Color(0xFF6B7280)
}

/** Dosya simgesi + köşede uzantı rozeti; klasörde dolu, renkli zemin. */
@Composable
private fun EntryIcon(entry: FileEntry, size: androidx.compose.ui.unit.Dp) {
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.size(size + 8.dp), contentAlignment = Alignment.Center) {
        if (entry.isDir) {
            Box(
                Modifier.size(size + 8.dp).clip(RoundedCornerShape(10.dp)).background(scheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { FolderGlyph(tint = scheme.primary, size = size) }
        } else {
            FileGlyph(tint = k.muted, size = size)
            val ext = FileBrowse.extension(entry.name)
            val text = FileBrowse.badge(entry.name)
            if (text.isNotEmpty()) {
                Text(
                    text,
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor(ext))
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun FileItem(
    entry: FileEntry,
    grid: Boolean,
    showExt: Boolean,
    drag: FileDrag,
    aiTargets: List<AiTarget>,
    areaOrigin: () -> Offset,
    onOpenDir: () -> Unit,
    onSend: (File, Long) -> Unit,
    onDragDrop: (File) -> Unit,
    /** Kopyala/taşı/yeniden adlandır/sil yalnız normal depolamada (Shizuku kaynağı salt okunur). */
    modifiable: Boolean,
    onAction: (FileAction) -> Unit,
) {
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme
    var rowRoot by remember { mutableStateOf(Offset.Zero) }
    val originNow by rememberUpdatedState(areaOrigin)
    val dropNow by rememberUpdatedState(onDragDrop)
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val dragging = drag.file == entry.file
    var menu by remember { mutableStateOf(false) }
    val name = FileBrowse.displayName(entry, showExt)

    val base = Modifier
        .fillMaxWidth()
        .background(if (dragging) k.nodeFill else scheme.background)
        .onGloballyPositioned { rowRoot = it.positionInRoot() }
        .then(
            if (entry.isDir) {
                Modifier.clickable(onClick = onOpenDir)
            } else {
                // Dosya: dokununca aç; basılı tutup sürükle -> AI penceresine bırak.
                Modifier.clickable { onAction(FileAction.OPEN) }.pointerInput(entry.file.path) {
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

    val sendMenu: @Composable () -> Unit = {
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.desktop_files_send_menu), modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                @Composable
                fun entryItem(label: Int, action: FileAction) {
                    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { menu = false; onAction(action) })
                }
                entryItem(R.string.desktop_files_open, FileAction.OPEN)
                if (!entry.isDir) entryItem(R.string.desktop_files_open_with, FileAction.OPEN_WITH)
                if (!entry.isDir) {
                    HorizontalDivider()
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
                HorizontalDivider()
                if (modifiable && !entry.isDir && ArchiveTool.isSupported(entry.name)) {
                    entryItem(R.string.l_ar_browse, FileAction.BROWSE_ARCHIVE)
                    entryItem(R.string.l_ar_extract_here, FileAction.EXTRACT_HERE)
                    entryItem(R.string.l_ar_extract_folder, FileAction.EXTRACT_FOLDER)
                    HorizontalDivider()
                }
                if (modifiable) {
                    entryItem(R.string.l_ar_zip, FileAction.COMPRESS_ZIP)
                    entryItem(R.string.l_ar_tgz, FileAction.COMPRESS_TGZ)
                    HorizontalDivider()
                    entryItem(R.string.desktop_files_copy, FileAction.COPY)
                    entryItem(R.string.desktop_files_move, FileAction.MOVE)
                    entryItem(R.string.desktop_files_rename, FileAction.RENAME)
                    entryItem(R.string.desktop_files_delete, FileAction.DELETE)
                }
                entryItem(R.string.desktop_files_props, FileAction.PROPS)
            }
        }
    }

    if (grid) {
        Column(
            base.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                EntryIcon(entry, 40.dp)
                Box(Modifier.align(Alignment.TopEnd)) { sendMenu() }
            }
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!entry.isDir) {
                Text(FileBrowse.humanSize(entry.size), style = MaterialTheme.typography.labelSmall, color = k.muted, maxLines = 1)
            }
        }
    } else {
        Row(
            base.heightIn(min = 56.dp).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EntryIcon(entry, 28.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, softWrap = true, overflow = TextOverflow.Ellipsis)
                if (!entry.isDir) {
                    Text(
                        FileBrowse.humanSize(entry.size) + "  ·  " + dateFormat.format(Date(entry.modified)),
                        style = MaterialTheme.typography.labelSmall,
                        color = k.muted,
                        maxLines = 1,
                    )
                }
            }
            sendMenu()
        }
    }
    if (!grid) HorizontalDivider(color = k.nodeBorder.copy(alpha = 0.25f))
}

/** Çalışan arşiv işi (alttaki ilerleme çubuğu). [progress] -1: belirsiz. */
private data class ArchiveJob(val label: String, val progress: Float, val cancel: AtomicBoolean)
