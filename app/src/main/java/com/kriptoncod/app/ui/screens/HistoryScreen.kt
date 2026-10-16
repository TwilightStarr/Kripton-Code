package com.kriptoncod.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.KriptonCodApp
import com.kriptoncod.app.R
import com.kriptoncod.app.data.db.RunRecord
import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.ui.components.EmptyState
import com.kriptoncod.app.ui.components.KBadge
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(onOpenRun: (Long) -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as KriptonCodApp).container
    val runs by container.runRepository.observeRuns()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        if (runs.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.List,
                title = stringResource(R.string.history_empty_title),
                message = stringResource(R.string.history_empty_message),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(runs.sortedByDescending { it.createdAt }, key = { it.id }) { run ->
                    RunHistoryRow(run = run, onClick = { onOpenRun(run.id) })
                }
            }
        }
    }
}

@Composable
private fun RunHistoryRow(run: RunRecord, onClick: () -> Unit) {
    val k = KriptonTheme.colors
    NodeCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Run #${run.id}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                        .format(Date(run.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )
                run.repo?.let { repo ->
                    Text(
                        text = repo,
                        style = MaterialTheme.typography.bodySmall,
                        color = k.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HistoryStatusBadge(status = run.status)
        }
    }
}

@Composable
private fun HistoryStatusBadge(status: RunStatus) {
    val (label, accent) = when (status) {
        RunStatus.PREPARING -> "Hazırlanıyor" to NodeAccent.Blue
        RunStatus.RUNNING -> "Çalışıyor" to NodeAccent.Blue
        RunStatus.PUSHING -> "Gönderiliyor" to NodeAccent.Blue
        RunStatus.CI_WAITING -> "CI Bekleniyor" to NodeAccent.Blue
        RunStatus.FIXING -> "Düzeltiliyor" to NodeAccent.Blue
        RunStatus.DONE -> "Tamamlandı" to NodeAccent.Green
        RunStatus.FAILED -> "Başarısız" to NodeAccent.Red
        RunStatus.CANCELLED -> "İptal Edildi" to NodeAccent.Red
    }
    KBadge(text = label, accent = accent)
}
