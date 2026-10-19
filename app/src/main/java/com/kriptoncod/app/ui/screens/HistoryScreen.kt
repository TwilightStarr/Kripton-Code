package com.kriptoncod.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.kriptoncod.app.ui.adaptive.Adaptive
import com.kriptoncod.app.ui.adaptive.LocalWindowClass
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
import com.kriptoncod.app.ui.labelRes
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme
import com.kriptoncod.app.ui.theme.appear
import com.kriptoncod.app.ui.theme.isInProgress
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
        modifier = Modifier.fillMaxSize().padding(Adaptive.pagePadding(LocalWindowClass.current)),
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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(Adaptive.CardMinWidth),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
    NodeCard(modifier = Modifier.fillMaxWidth().appear(), live = run.status.isInProgress(), onClick = onClick) {
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
internal fun HistoryStatusBadge(status: RunStatus) {
    KBadge(text = stringResource(status.labelRes()), accent = runStatusAccent(status), live = status.isInProgress())
}
