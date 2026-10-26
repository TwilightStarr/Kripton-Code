package com.kriptoncod.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.R
import com.kriptoncod.app.data.github.GitHubRepo
import com.kriptoncod.app.data.providers.ApiResult
import com.kriptoncod.app.data.serial.SerialPhase
import com.kriptoncod.app.data.serial.SerialState
import com.kriptoncod.app.data.serial.SerialStatus
import com.kriptoncod.app.data.vault.KeyVaultException
import com.kriptoncod.app.data.web.OutputStyle
import com.kriptoncod.app.data.web.WebProvider
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.ui.ais.rememberContainer
import com.kriptoncod.app.ui.components.KBadge
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.KChip
import com.kriptoncod.app.ui.components.KriptonLoader
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

@Composable
private fun statusText(s: SerialStatus): String = when (s) {
    SerialStatus.RUNNING -> stringResource(R.string.serial_status_running)
    SerialStatus.PAUSED -> stringResource(R.string.serial_status_paused)
    SerialStatus.WAITING_USER -> stringResource(R.string.serial_status_waiting)
    SerialStatus.DONE -> stringResource(R.string.serial_status_done)
    SerialStatus.FAILED -> stringResource(R.string.serial_status_failed)
    SerialStatus.CANCELLED -> stringResource(R.string.serial_status_cancelled)
}

private fun accentOf(s: SerialStatus): NodeAccent = when (s) {
    SerialStatus.DONE -> NodeAccent.Green
    SerialStatus.FAILED, SerialStatus.CANCELLED, SerialStatus.WAITING_USER -> NodeAccent.Red
    else -> NodeAccent.Blue
}

@Composable
private fun phaseText(p: SerialPhase): String = when (p) {
    SerialPhase.PROMPT -> stringResource(R.string.serial_phase_prompt)
    SerialPhase.COMMIT -> stringResource(R.string.serial_phase_commit)
    SerialPhase.CI -> stringResource(R.string.serial_phase_ci)
    SerialPhase.FIX -> stringResource(R.string.serial_phase_fix)
}

/** Prompt setini seri modda başlatmak için hesap ve repo seçtirir. Başarılıysa [onStarted] yeni kimlikle çağrılır. */
@Composable
fun SerialLaunchDialog(
    setId: Long,
    setName: String,
    onDismiss: () -> Unit,
    onStarted: (Long) -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val k = KriptonTheme.colors
    val accounts by container.aiAccountRepository.observeEnabled().collectAsStateWithLifecycle(initialValue = emptyList())
    val webAccounts = accounts.filter {
        it.authType == AuthType.WEB_SESSION && WebProvider.fromId(it.providerId)?.let { p -> p.output != OutputStyle.API_KEY } == true
    }
    var repos by remember { mutableStateOf<List<GitHubRepo>?>(null) }
    var noToken by remember { mutableStateOf(false) }
    var repoError by remember { mutableStateOf(false) }
    var repo by remember { mutableStateOf<String?>(null) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var starting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val token = container.githubTokenStore.get()
            if (token == null) {
                noToken = true
            } else {
                when (val r = container.githubService.listRepos(token)) {
                    is ApiResult.Ok -> repos = r.value
                    is ApiResult.Fail -> repoError = true
                }
            }
        } catch (e: KeyVaultException) {
            noToken = true
        }
    }
    val chosenAccount = accountId ?: webAccounts.firstOrNull()?.id

    AlertDialog(
        onDismissRequest = { if (!starting) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.serial_launch_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.serial_launch_info), style = MaterialTheme.typography.bodySmall, color = k.muted)
                Text(stringResource(R.string.serial_launch_account), style = MaterialTheme.typography.labelLarge)
                if (webAccounts.isEmpty()) {
                    Text(stringResource(R.string.serial_launch_no_accounts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                webAccounts.forEach { a ->
                    KChip(
                        label = a.displayName,
                        selected = chosenAccount == a.id,
                        onClick = { accountId = a.id },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(stringResource(R.string.serial_launch_repo), style = MaterialTheme.typography.labelLarge)
                when {
                    noToken -> Text(stringResource(R.string.serial_launch_no_token), style = MaterialTheme.typography.bodySmall, color = k.muted)

                    repoError -> Text(stringResource(R.string.serial_launch_repo_error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

                    repos == null -> KriptonLoader()

                    else -> repos.orEmpty().forEach { r ->
                        KChip(
                            label = r.fullName,
                            selected = repo == r.fullName,
                            onClick = { repo = r.fullName },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                error?.let {
                    Text(stringResource(R.string.serial_launch_failed, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !starting && repo != null && chosenAccount != null,
                onClick = {
                    val r = repo ?: return@TextButton
                    val a = chosenAccount ?: return@TextButton
                    starting = true
                    error = null
                    scope.launch {
                        try {
                            onStarted(container.serialLauncher.launch(setId, setName, r, a))
                        } catch (c: CancellationException) {
                            throw c
                        } catch (e: Exception) {
                            error = e.message ?: e.toString()
                            starting = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.serial_launch_start)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !starting) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Ana ekranda son seri çalışmaları gösterir; hiç yoksa hiçbir şey çizmez. */
@Composable
fun SerialRunsSection(container: AppContainer, onOpen: (Long) -> Unit) {
    val all by container.serialStore.observeAll().collectAsStateWithLifecycle()
    val recent = all.take(3)
    if (recent.isEmpty()) return
    val k = KriptonTheme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.serial_home_title), style = MaterialTheme.typography.titleMedium)
        recent.forEach { s ->
            NodeCard(modifier = Modifier.fillMaxWidth(), accent = accentOf(s.status), onClick = { onOpen(s.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.serial_progress, minOf(s.index + 1, s.total), s.total) + " · " + s.repo,
                            style = MaterialTheme.typography.bodySmall,
                            color = k.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    KBadge(text = statusText(s.status), accent = accentOf(s.status))
                }
            }
        }
    }
}

/** Bir seri çalışmanın canlı durumu, günlüğü ve denetim düğmeleri. */
@Composable
fun SerialRunScreen(id: Long, container: AppContainer, onBack: () -> Unit) {
    val all by container.serialStore.observeAll().collectAsStateWithLifecycle()
    val s: SerialState? = all.firstOrNull { it.id == id }
    val k = KriptonTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.serial_screen_title, id.toInt()), style = MaterialTheme.typography.headlineSmall)
        }
        if (s == null) return@Column
        NodeCard(modifier = Modifier.fillMaxWidth(), accent = accentOf(s.status)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    KBadge(text = statusText(s.status), accent = accentOf(s.status))
                }
                Text("${s.repo}@${s.branch}", style = MaterialTheme.typography.bodySmall, color = k.muted)
                Text(
                    stringResource(R.string.serial_progress, minOf(s.index + 1, s.total), s.total) + (s.current?.let { " · ${it.title}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!s.status.isFinished) Text(phaseText(s.phase), style = MaterialTheme.typography.bodySmall, color = k.muted)
                if (s.fixRound > 0) Text("CI: ${s.fixRound}", style = MaterialTheme.typography.bodySmall, color = k.muted)
                if (s.message.isNotBlank()) Text(s.message, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (s.status) {
                SerialStatus.RUNNING -> KButton(
                    text = stringResource(R.string.serial_pause),
                    onClick = { container.serialExecutor.pause(id) },
                    style = KButtonStyle.Outline,
                    modifier = Modifier.weight(1f),
                )

                SerialStatus.PAUSED, SerialStatus.WAITING_USER -> KButton(
                    text = stringResource(R.string.serial_resume),
                    onClick = {
                        container.serialExecutor.resume(id)
                        container.runScheduler.startSerial(id)
                    },
                    modifier = Modifier.weight(1f),
                )

                else -> Unit
            }
            if (!s.status.isFinished) {
                KButton(
                    text = stringResource(R.string.serial_cancel),
                    onClick = {
                        container.serialExecutor.cancel(id)
                        container.runScheduler.cancelSerial(id)
                    },
                    style = KButtonStyle.Danger,
                    modifier = Modifier.weight(1f),
                )
            } else {
                KButton(
                    text = stringResource(R.string.serial_delete),
                    onClick = {
                        container.serialStore.delete(id)
                        onBack()
                    },
                    style = KButtonStyle.Outline,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(stringResource(R.string.serial_log_title), style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            s.log.takeLast(120).reversed().forEach { line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = k.muted)
            }
        }
    }
}
