package com.kriptoncod.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.R
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.db.Job
import com.kriptoncod.app.data.run.CodeBlockParser
import com.kriptoncod.app.data.run.PlaceholderDetector
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bir düğümün çıktısından çıkarılan kısa sağlık özeti (ağır ayrıştırma ana iş parçacığında yapılmaz). */
internal data class NodeHealth(
    val chars: Int,
    val files: Int,
    val placeholders: Int,
    val unclosed: Boolean,
    val pathless: Int,
) {
    val hasWarning: Boolean get() = placeholders > 0 || unclosed || pathless > 0
}

internal fun analyzeOutput(text: String): NodeHealth {
    val a = CodeBlockParser.analyze(text)
    val pathed = a.blocks.mapNotNull { b -> b.path?.let { CodeBlockParser.Block(it, b.content) } }
    return NodeHealth(
        chars = text.length,
        files = pathed.map { it.path }.distinct().size,
        placeholders = PlaceholderDetector.find(pathed).size,
        unclosed = a.unclosedFence,
        pathless = a.blocks.count { it.path == null },
    )
}

/**
 * Akış penceresi: her adımı sırayla gösterir — hangi AI, hangi düğüm, durum, deneme sayısı, çıktı boyutu ve
 * uyarılar (yer tutucu, kesik çıktı, yolsuz blok). Satıra dokunmak düğümü seçer.
 */
@Composable
internal fun RunFlowPanel(
    jobs: List<Job>,
    accounts: List<AiAccount>,
    container: AppContainer,
    selectedId: Long?,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val k = KriptonTheme.colors
    val names = accounts.associate { it.id to it.displayName }
    val ordered = jobs.sortedBy { it.id }
    if (ordered.isEmpty()) {
        Text(stringResource(R.string.flow_empty), style = MaterialTheme.typography.bodyMedium, color = k.muted, modifier = modifier.padding(12.dp))
        return
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ordered, key = { it.id }) { job ->
            FlowItem(job, names[job.accountId], container, job.id == selectedId, onPick)
        }
    }
}

@Composable
private fun FlowItem(job: Job, accountName: String?, container: AppContainer, selected: Boolean, onPick: (Long) -> Unit) {
    val k = KriptonTheme.colors
    var health by remember(job.id) { mutableStateOf<NodeHealth?>(null) }
    LaunchedEffect(job.id, job.state, job.updatedAt) {
        health = if (job.state == JobState.DONE) {
            val text = container.runRepository.readOutput(job.id)
            text?.let { withContext(Dispatchers.Default) { analyzeOutput(it) } }
        } else {
            null
        }
    }
    val h = health
    val accent = when {
        job.state == JobState.FAILED -> NodeAccent.Red
        h?.hasWarning == true -> NodeAccent.Red
        job.state == JobState.DONE -> NodeAccent.Green
        else -> NodeAccent.Blue
    }
    NodeCard(modifier = Modifier.fillMaxWidth(), accent = accent, onClick = { onPick(job.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${if (selected) "▶ " else ""}${kindLabel(job.kind)}${job.title?.let { ": $it" } ?: ""}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    listOfNotNull(
                        accountName ?: stringResource(R.string.run_node_no_account),
                        stringResource(R.string.run_node_attempts, job.attempts),
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(job.updatedAt)),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )
            }
            JobStateBadge(state = job.state)
        }
        if (h != null) {
            Text(
                stringResource(R.string.flow_health, h.chars, h.files),
                style = MaterialTheme.typography.bodySmall,
                color = k.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (h.placeholders > 0) FlowWarn(stringResource(R.string.flow_warn_placeholders, h.placeholders))
            if (h.unclosed) FlowWarn(stringResource(R.string.flow_warn_unclosed))
            if (h.pathless > 0) FlowWarn(stringResource(R.string.flow_warn_pathless, h.pathless))
        }
    }
}

@Composable
private fun FlowWarn(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = KriptonTheme.colors.danger, modifier = Modifier.padding(top = 2.dp))
}

/** Zip'in dosya listesi (yol ve boyut); zip yokken açıklama gösterir. */
@Composable
internal fun RunFilesPanel(runId: Long, zipPath: String?, container: AppContainer, modifier: Modifier = Modifier) {
    val k = KriptonTheme.colors
    var entries by remember(runId, zipPath) { mutableStateOf<List<Pair<String, Int>>?>(null) }
    LaunchedEffect(runId, zipPath) {
        entries = if (zipPath == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                container.runRepository.readZipFiles(runId).map { it.first to it.second.size }.sortedBy { it.first }
            }
        }
    }
    val list = entries
    if (list == null) {
        Text(stringResource(R.string.flow_files_wait), style = MaterialTheme.typography.bodyMedium, color = k.muted, modifier = modifier.padding(12.dp))
        return
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(list, key = { it.first }) { (path, size) ->
            val internal = path.startsWith("_kripton/")
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(
                    path,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (internal) k.muted else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(formatSize(size), style = MaterialTheme.typography.bodySmall, color = k.muted)
            }
        }
    }
}

private fun formatSize(bytes: Int): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}
