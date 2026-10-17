package com.kriptoncod.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.R
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.db.Job
import com.kriptoncod.app.domain.GraphNodeUi
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.ui.components.KBadge
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.RunGraph
import com.kriptoncod.app.ui.theme.KriptonTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** İptal edilebilir durumlar. */
private val CANCELLABLE = setOf(
    RunStatus.PREPARING, RunStatus.RUNNING, RunStatus.PUSHING, RunStatus.CI_WAITING, RunStatus.FIXING,
    RunStatus.PAUSED,
)

/**
 * Bir run'ın düğüm grafiği: katalog çıkarma → paralel çalışan yapay zekalar → katalog birleştirme →
 * nihai birleştirme. Düğüme dokununca çıktısı canlı görülür ve indirilebilir. Duraklat/devam/iptal buradadır.
 */
@Composable
fun RunScreen(
    runId: Long,
    container: AppContainer,
    onBack: () -> Unit,
) {
    val k = KriptonTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val run by container.runRepository.observeRun(runId)
        .collectAsStateWithLifecycle(initialValue = null)
    val jobs by container.runRepository.observeJobs(runId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val accounts by container.aiAccountRepository.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }

    val currentRun = run
    val status = currentRun?.status

    // Zip ve düğüm çıktıları uygulamanın özel klasöründedir; kullanıcının seçtiği yere kopyalanır.
    val saveZip = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val zipPath = run?.zipPath
        if (uri != null && zipPath != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val src = container.outputStore.resolve(zipPath)
                        val out = context.contentResolver.openOutputStream(uri) ?: return@withContext false
                        out.use { o -> src.inputStream().use { it.copyTo(o) } }
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        false
                    }
                }
                Toast.makeText(context, if (ok) R.string.run_save_zip_done else R.string.run_save_zip_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    var pendingNodeText by remember { mutableStateOf<String?>(null) }
    val saveNode = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        val text = pendingNodeText
        if (uri != null && text != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val out = context.contentResolver.openOutputStream(uri) ?: return@withContext false
                        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        false
                    }
                }
                Toast.makeText(context, if (ok) R.string.node_save_done else R.string.node_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Grafik girdisi: düğüm boyutu hesabın göreli gücünden gelir.
    val power = accounts.associate { it.id to it.powerRank }
    val maxPower = (accounts.maxOfOrNull { it.powerRank } ?: 1).coerceAtLeast(1)
    val workNumbers = jobs.filter { it.kind == JobKind.WORK }.mapIndexed { i, j -> j.id to i + 1 }.toMap()
    val nodes = jobs.map { j ->
        val weight = j.accountId?.let { power[it] }?.let { it.toFloat() / maxPower }
            ?: if (j.kind == JobKind.PLAN || j.kind == JobKind.FINAL_MERGE) 1f else 0.3f
        val label = when (j.kind) {
            JobKind.PLAN -> "K"
            JobKind.WORK -> workNumbers[j.id].toString()
            JobKind.CATALOG_MERGE -> "B"
            JobKind.FINAL_MERGE -> "N"
        }
        GraphNodeUi(j.id, j.kind, j.catalog, j.state, weight, label)
    }
    val selected = jobs.firstOrNull { it.id == selectedId }
    val anyWaiting = jobs.any { it.state == JobState.WAITING }

    // Ayrıntı paneli açıkken geri tuşu önce paneli kapatır.
    BackHandler(enabled = selected != null) { selectedId = null }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Kompakt başlık: grafiğe en çok yeri bırakmak için başlık, run bilgisi ve durum tek satırda.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = currentRun?.let { "${stringResource(R.string.run_title)} #${it.id}" }
                        ?: stringResource(R.string.run_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                currentRun?.let { r ->
                    Text(
                        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(r.createdAt)) +
                            (r.repo?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = k.muted,
                    )
                }
            }
            currentRun?.let { RunStatusBadge(status = it.status) }
            if (status != null && status in CANCELLABLE && status != RunStatus.PAUSED) {
                CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
            }
        }
        if (anyWaiting && status == RunStatus.RUNNING) {
            Text(
                stringResource(R.string.run_waiting_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))

        // Denetim düğmeleri
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            when (status) {
                RunStatus.RUNNING, RunStatus.PREPARING -> KButton(
                    text = stringResource(R.string.run_pause),
                    onClick = { scope.launch { container.runScheduler.pause(runId) } },
                    style = KButtonStyle.Outline,
                    modifier = Modifier.weight(1f),
                )
                RunStatus.PAUSED -> KButton(
                    text = stringResource(R.string.run_resume),
                    onClick = { scope.launch { container.runScheduler.unpause(runId) } },
                    modifier = Modifier.weight(1f),
                )
                else -> Unit
            }
            if (status in CANCELLABLE) {
                KButton(
                    text = stringResource(R.string.run_cancel),
                    onClick = { scope.launch { container.runScheduler.cancel(runId) } },
                    style = KButtonStyle.Danger,
                    modifier = Modifier.weight(1f),
                )
            }
            if (currentRun?.zipPath != null) {
                KButton(
                    text = stringResource(R.string.run_save_zip),
                    onClick = { saveZip.launch("KriptonCod-run-$runId.zip") },
                    style = KButtonStyle.Outline,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // Telefon: sekmeli tek bölme (Grafik | Akış | Dosyalar). Tablet/yatay: üç bölme yan yana
        // (grafik | seçili düğümün çıktısı | akış günlüğü ve dosya listesi).
        val wide = LocalConfiguration.current.screenWidthDp >= WIDE_DP
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var sideTab by rememberSaveable { mutableIntStateOf(0) }
        val onToggle: (Long) -> Unit = { selectedId = if (selectedId == it) null else it }
        val onPick: (Long) -> Unit = {
            selectedId = it
            if (!wide) tab = 0 // akıştan bir satır seçilince çıktısı grafik sekmesinde açılır
        }
        val saveSelected: (String) -> Unit = { text ->
            pendingNodeText = text
            saveNode.launch("node-${selectedId ?: 0}.md")
        }

        if (wide) {
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GraphPane(
                    nodes = nodes, selected = selected, selectedId = selectedId, onToggle = onToggle,
                    accounts = accounts, container = container, onSave = saveSelected, onClose = { selectedId = null },
                    overlayDetail = false, modifier = Modifier.weight(1.1f).fillMaxHeight(),
                )
                val detailShape = RoundedCornerShape(16.dp)
                Surface(
                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                    shape = detailShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.5.dp, k.nodeBorder),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    if (selected != null) {
                        NodeDetail(
                            job = selected,
                            accountName = accounts.firstOrNull { it.id == selected.accountId }?.displayName,
                            container = container,
                            onSave = saveSelected,
                            onClose = { selectedId = null },
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
                        )
                    } else {
                        Text(
                            stringResource(R.string.run_node_hint_wide),
                            style = MaterialTheme.typography.bodyMedium,
                            color = k.muted,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                Column(Modifier.weight(0.9f).fillMaxHeight()) {
                    TabRow(selectedTabIndex = sideTab) {
                        Tab(selected = sideTab == 0, onClick = { sideTab = 0 }, text = { Text(stringResource(R.string.run_tab_flow)) })
                        Tab(selected = sideTab == 1, onClick = { sideTab = 1 }, text = { Text(stringResource(R.string.run_tab_files)) })
                    }
                    Spacer(Modifier.height(6.dp))
                    if (sideTab == 0) {
                        RunFlowPanel(jobs, accounts, container, selectedId, onPick, Modifier.fillMaxSize())
                    } else {
                        RunFilesPanel(runId, currentRun?.zipPath, container, Modifier.fillMaxSize())
                    }
                }
            }
        } else {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.run_tab_graph)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.run_tab_flow)) })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.run_tab_files)) })
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> GraphPane(
                        nodes = nodes, selected = selected, selectedId = selectedId, onToggle = onToggle,
                        accounts = accounts, container = container, onSave = saveSelected, onClose = { selectedId = null },
                        overlayDetail = true, modifier = Modifier.fillMaxSize(),
                    )
                    1 -> RunFlowPanel(jobs, accounts, container, selectedId, onPick, Modifier.fillMaxSize())
                    else -> RunFilesPanel(runId, currentRun?.zipPath, container, Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** Bu genişlik (dp) ve üstü tablet/yatay sayılır: üç bölmeli yerleşim kullanılır. */
private const val WIDE_DP = 700

/**
 * Düğüm grafiği. [overlayDetail] true (telefon): seçili düğümün çıktısı grafiğin üzerine alttan açılır;
 * false (tablet): çıktı ayrı bölmede gösterilir, grafik küçülmez.
 */
@Composable
private fun GraphPane(
    nodes: List<GraphNodeUi>,
    selected: Job?,
    selectedId: Long?,
    onToggle: (Long) -> Unit,
    accounts: List<AiAccount>,
    container: AppContainer,
    onSave: (String) -> Unit,
    onClose: () -> Unit,
    overlayDetail: Boolean,
    modifier: Modifier = Modifier,
) {
    val k = KriptonTheme.colors
    val graphShape = RoundedCornerShape(16.dp)
    Column(modifier) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(graphShape)
                .background(k.nodeFill)
                .border(1.5.dp, k.nodeBorder, graphShape),
        ) {
            val panelHeight = maxHeight * 0.5f
            val showPanel = overlayDetail && selected != null
            if (nodes.isEmpty()) {
                Text(
                    stringResource(R.string.run_graph_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = k.muted,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                RunGraph(
                    nodes = nodes,
                    selectedId = selectedId,
                    onSelect = onToggle,
                    modifier = Modifier.fillMaxSize(),
                    bottomInset = if (showPanel) panelHeight else 0.dp,
                )
            }
            if (showPanel && selected != null) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(panelHeight),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.5.dp, k.nodeBorder),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    NodeDetail(
                        job = selected,
                        accountName = accounts.firstOrNull { it.id == selected.accountId }?.displayName,
                        container = container,
                        onSave = onSave,
                        onClose = onClose,
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
                    )
                }
            }
        }
        if (selected == null && nodes.isNotEmpty()) {
            Text(
                stringResource(R.string.run_node_hint),
                style = MaterialTheme.typography.bodySmall,
                color = k.muted,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun NodeDetail(
    job: Job,
    accountName: String?,
    container: AppContainer,
    onSave: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val k = KriptonTheme.colors
    var output by remember(job.id) { mutableStateOf<String?>(null) }
    // Düğümün durumu/zamanı değişince çıktı yeniden okunur: iş biter bitmez burada görünür.
    LaunchedEffect(job.id, job.state, job.updatedAt) {
        output = container.runRepository.readOutput(job.id)
    }

    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${kindLabel(job.kind)}${job.title?.let { ": $it" } ?: ""}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    listOfNotNull(
                        accountName ?: stringResource(R.string.run_node_no_account),
                        stringResource(R.string.run_node_attempts, job.attempts),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )
            }
            JobStateBadge(state = job.state)
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.run_node_close))
            }
        }
        Spacer(Modifier.height(4.dp))
        val text = output
        // Çıktı sınırsız uzun olabilir: tek bir dev Text yerine satır gruplarıyla tembel çizilir (kesilmez).
        val chunks = remember(text) { text?.let { chunkLines(it) }.orEmpty() }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (text == null) {
                Text(
                    stringResource(R.string.run_node_no_output),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )
            } else {
                SelectionContainer {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(chunks.size) { i ->
                            Text(
                                chunks[i],
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
        if (text != null) {
            Spacer(Modifier.height(8.dp))
            KButton(
                text = stringResource(R.string.node_save),
                onClick = { onSave(text) },
                style = KButtonStyle.Outline,
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
            )
        }
    }
}

/** Metni ~120 satırlık parçalara böler; hiçbir karakter atılmaz. */
private fun chunkLines(text: String, linesPerChunk: Int = 120): List<String> {
    val out = ArrayList<String>()
    val sb = StringBuilder()
    var n = 0
    text.lineSequence().forEach { line ->
        if (n > 0) sb.append('\n')
        sb.append(line)
        if (++n >= linesPerChunk) { out += sb.toString(); sb.setLength(0); n = 0 }
    }
    if (n > 0) out += sb.toString()
    return out
}

internal fun kindLabel(kind: JobKind): String = when (kind) {
    JobKind.PLAN -> "Katalog çıkarma"
    JobKind.WORK -> "Prompt"
    JobKind.CATALOG_MERGE -> "Katalog birleştirme"
    JobKind.FINAL_MERGE -> "Nihai birleştirme"
}

@Composable
private fun RunStatusBadge(status: RunStatus) {
    val (label, accent) = when (status) {
        RunStatus.PREPARING -> "Hazırlanıyor" to NodeAccent.Blue
        RunStatus.RUNNING -> "Çalışıyor" to NodeAccent.Blue
        RunStatus.PUSHING -> "Gönderiliyor" to NodeAccent.Blue
        RunStatus.CI_WAITING -> "CI Bekleniyor" to NodeAccent.Blue
        RunStatus.FIXING -> "Düzeltiliyor" to NodeAccent.Blue
        RunStatus.PAUSED -> "Duraklatıldı" to NodeAccent.Blue
        RunStatus.DONE -> "Tamamlandı" to NodeAccent.Green
        RunStatus.FAILED -> "Başarısız" to NodeAccent.Red
        RunStatus.CANCELLED -> "İptal Edildi" to NodeAccent.Red
    }
    KBadge(text = label, accent = accent)
}

@Composable
internal fun JobStateBadge(state: JobState) {
    val (label, accent) = when (state) {
        JobState.PENDING -> "Bekliyor" to NodeAccent.Blue
        JobState.RUNNING -> "Çalışıyor" to NodeAccent.Blue
        JobState.WAITING -> "Kota bekleniyor" to NodeAccent.Blue
        JobState.DONE -> "Tamam" to NodeAccent.Green
        JobState.FAILED -> "Hata" to NodeAccent.Red
    }
    KBadge(text = label, accent = accent)
}
