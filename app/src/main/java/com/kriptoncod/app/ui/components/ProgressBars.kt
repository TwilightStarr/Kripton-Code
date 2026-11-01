package com.kriptoncod.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R
import com.kriptoncod.app.data.run.RunProgress
import com.kriptoncod.app.data.serial.SerialProgress
import com.kriptoncod.app.ui.theme.KriptonTheme

/**
 * Run ilerleme çubuğu + tek satır açıklama. Sayılar [RunProgress]'ten gelir (bildirim ve widget ile aynı hesap):
 * planlama belirsiz çubuk, düğümler ağırlıklı, gönderme/CI son dilim. Başarısız düğüm ve tahmini süre varsa eklenir.
 */
@Composable
fun RunProgressBar(progress: RunProgress, modifier: Modifier = Modifier) {
    val k = KriptonTheme.colors
    val label = when {
        progress.indeterminate || (progress.total == 0 && progress.stage == RunProgress.Stage.PAUSED) ->
            stringResource(R.string.run_progress_planning)

        progress.stage == RunProgress.Stage.PUSHING -> stringResource(R.string.run_progress_pushing, progress.percent)

        progress.stage == RunProgress.Stage.CI -> stringResource(R.string.run_progress_ci, progress.percent)

        progress.stage == RunProgress.Stage.FIXING -> stringResource(R.string.run_progress_fixing, progress.percent)

        else -> {
            val parts = mutableListOf(stringResource(R.string.run_progress_nodes, progress.done, progress.total, progress.percent))
            if (progress.failed > 0) parts += stringResource(R.string.run_progress_failed, progress.failed)
            val eta = progress.etaMs
            if (eta != null && eta > 0L) {
                parts += stringResource(R.string.run_progress_eta, ((eta + 59_999L) / 60_000L).toInt().coerceAtLeast(1))
            }
            parts.joinToString(" · ")
        }
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (progress.indeterminate) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = k.muted)
    }
}

/** Seri mod ilerleme çubuğu: biten prompt sayısı + süren promptun aşaması (prompt → commit → CI → düzeltme). */
@Composable
fun SerialProgressBar(progress: SerialProgress, modifier: Modifier = Modifier) {
    val k = KriptonTheme.colors
    val label = if (progress.total > 0 && progress.completed >= progress.total) {
        stringResource(R.string.serial_progress_done, progress.total)
    } else {
        stringResource(
            R.string.serial_progress_detail,
            progress.completed,
            progress.total,
            progress.currentNumber ?: (progress.completed + 1),
            progress.percent,
        )
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
        Text(label, style = MaterialTheme.typography.bodySmall, color = k.muted)
    }
}
