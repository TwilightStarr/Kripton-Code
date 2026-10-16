package com.kriptoncod.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.R
import com.kriptoncod.app.data.db.Job
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.ui.components.KBadge
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tek bir run'ın canlı ilerlemesini gösterir.
 * İş listesi, durum rozeti ve iptal düğmesi içerir.
 */
@Composable
fun RunScreen(
    runId: Long,
    container: AppContainer,
    onBack: () -> Unit,
) {
    val k = KriptonTheme.colors

    val run by container.runRepository.observeRun(runId)
        .collectAsStateWithLifecycle(initialValue = null)
    val jobs by container.runRepository.observeJobs(runId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val currentRun = run
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Zip uygulamanın özel klasöründedir; kullanıcının seçtiği yere (Dosyalar, Drive…) kopyalanır.
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
                Toast.makeText(
                    context,
                    if (ok) R.string.run_save_zip_done else R.string.run_save_zip_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Text(
                text = stringResource(R.string.run_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            if (currentRun?.status == RunStatus.RUNNING ||
                currentRun?.status == RunStatus.PREPARING ||
                currentRun?.status == RunStatus.CI_WAITING ||
                currentRun?.status == RunStatus.FIXING
            ) {
                CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        currentRun?.let { r ->
            NodeCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Run #${r.id}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        RunStatusBadge(status = r.status)
                    }
                    Text(
                        text = "Oluşturulma: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(r.createdAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = k.muted,
                    )
                    r.repo?.let { repo ->
                        Text(
                            text = "Repo: $repo",
                            style = MaterialTheme.typography.bodySmall,
                            color = k.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (r.zipPath != null) {
                Spacer(modifier = Modifier.height(12.dp))
                KButton(
                    text = stringResource(R.string.run_save_zip),
                    onClick = { saveZip.launch("KriptonCod-run-${r.id}.zip") },
                    style = KButtonStyle.Outline,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.run_jobs_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(jobs, key = { it.id }) { job ->
                JobRow(job = job)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (currentRun?.status in CANCELLABLE) {
            KButton(
                text = stringResource(R.string.run_cancel),
                onClick = {
                    scope.launch { container.runScheduler.cancel(runId) }
                },
                style = KButtonStyle.Danger,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** İptal edilebilir durumlar: iş sürerken ve CI beklenirken. */
private val CANCELLABLE = setOf(
    RunStatus.PREPARING, RunStatus.RUNNING, RunStatus.PUSHING, RunStatus.CI_WAITING, RunStatus.FIXING,
)

@Composable
private fun RunStatusBadge(status: RunStatus) {
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

@Composable
private fun JobRow(job: Job) {
    val k = KriptonTheme.colors
    NodeCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "İş #${job.id} · Prompt #${job.promptId}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Deneme: ${job.attempts}",
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )
            }
            JobStateBadge(state = job.state)
        }
    }
}

@Composable
private fun JobStateBadge(state: JobState) {
    val (label, accent) = when (state) {
        JobState.PENDING -> "Bekliyor" to NodeAccent.Blue
        JobState.RUNNING -> "Çalışıyor" to NodeAccent.Blue
        JobState.WAITING -> "Beklemede" to NodeAccent.Blue
        JobState.DONE -> "Tamam" to NodeAccent.Green
        JobState.FAILED -> "Hata" to NodeAccent.Red
    }
    KBadge(text = label, accent = accent)
}
