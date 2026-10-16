package com.kriptoncod.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.R
import com.kriptoncod.app.data.db.Job
import com.kriptoncod.app.domain.GraphNodeUi
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.ui.components.KBadge
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.NodeCard
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(R.string.run_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            if (status != null && status in CANCELLABLE && status != RunStatus.PAUSED) {
                CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
            }
        }
        Spacer(Modifier.height(8.dp))

        currentRun?.let { r ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Run #${r.id}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(r.createdAt)) +
                            (r.repo?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = k.muted,
                    )
                }
                RunStatusBadge(status = r.status)
            }
        }
        if (anyWaiting && status == RunStatus.RUNNING) {
            Text(
                stringResource(R.string.run_waiting_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))

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
        Spacer(Modifier.height(12.dp))

        NodeCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (nodes.isEmpty()) {
                Text(stringResource(R.string.run_graph_empty), style = MaterialTheme.typography.bodyMedium, color = k.muted)
            } else {
                RunGraph(
                    nodes = nodes,
                    selectedId = selectedId,
                    onSelect = { selectedId = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        NodeCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (selected == null) {
                Text(stringResource(R.string.run_node_hint), style = MaterialTheme.typography.bodyMedium, color = k.muted)
            } else {
                NodeDetail(
                    job = selected,
                    accountName = accounts.firstOrNull { it.id == selected.accountId }?.displayName,
                    container = container,
                    onSave = { text ->
                        pendingNodeText = text
                        saveNode.launch("node-${selected.id}.md")
                    },
                )
            }
        }
    }
}

@Composable
private fun NodeDetail(
    job: Job,
    accountName: String?,
    container: AppContainer,
    onSave: (String) -> Unit,
) {
    val k = KriptonTheme.colors
    var output by remember(job.id) { mutableStateOf<String?>(null) }
    // Düğümün durumu/zamanı değişince çıktı yeniden okunur: iş biter bitmez burada görünür.
    LaunchedEffect(job.id, job.state, job.updatedAt) {
        output = container.runRepository.readOutput(job.id)
    }

    Column(Modifier.fillMaxSize()) {
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
        }
        Spacer(Modifier.height(8.dp))
        val text = output
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            SelectionContainer {
                Text(
                    text ?: stringResource(R.string.run_node_no_output),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (text == null) k.muted else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (text != null) {
            Spacer(Modifier.height(8.dp))
            KButton(
                text = stringResource(R.string.node_save),
                onClick = { onSave(text) },
                style = KButtonStyle.Outline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun kindLabel(kind: JobKind): String = when (kind) {
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
private fun JobStateBadge(state: JobState) {
    val (label, accent) = when (state) {
        JobState.PENDING -> "Bekliyor" to NodeAccent.Blue
        JobState.RUNNING -> "Çalışıyor" to NodeAccent.Blue
        JobState.WAITING -> "Kota bekleniyor" to NodeAccent.Blue
        JobState.DONE -> "Tamam" to NodeAccent.Green
        JobState.FAILED -> "Hata" to NodeAccent.Red
    }
    KBadge(text = label, accent = accent)
}
