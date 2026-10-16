package com.kriptoncod.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R
import com.kriptoncod.app.data.github.GitHubRepo
import com.kriptoncod.app.data.providers.ApiResult
import com.kriptoncod.app.data.vault.KeyVaultException
import com.kriptoncod.app.ui.ais.rememberContainer
import com.kriptoncod.app.ui.components.KChip
import com.kriptoncod.app.ui.theme.KriptonTheme
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

private sealed interface RepoState {
    data object Loading : RepoState
    data object NoToken : RepoState
    data object Failed : RepoState
    data class Loaded(val repos: List<GitHubRepo>) : RepoState
}

/**
 * Bir prompt setini çalıştırmadan önce hedefi seçtirir: yalnızca zip ya da bir GitHub reposu.
 * Başarıyla başlarsa [onStarted] yeni run kimliğiyle çağrılır.
 */
@Composable
fun RunLaunchDialog(
    setId: Long,
    hasAccounts: Boolean,
    onDismiss: () -> Unit,
    onStarted: (Long) -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val k = KriptonTheme.colors

    var repoState by remember { mutableStateOf<RepoState>(RepoState.Loading) }
    var selected by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repoState = try {
            val token = container.githubTokenStore.get()
            if (token == null) {
                RepoState.NoToken
            } else when (val r = container.githubService.listRepos(token)) {
                is ApiResult.Ok -> RepoState.Loaded(r.value)
                is ApiResult.Fail -> RepoState.Failed
            }
        } catch (e: KeyVaultException) {
            RepoState.NoToken
        }
    }

    AlertDialog(
        onDismissRequest = { if (!starting) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.run_launch_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!hasAccounts) {
                    Text(
                        stringResource(R.string.run_launch_no_accounts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = k.muted,
                    )
                } else {
                    Text(stringResource(R.string.run_launch_repo_label), style = MaterialTheme.typography.labelLarge)
                    when (val state = repoState) {
                        RepoState.Loading -> {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(R.string.run_launch_repo_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = k.muted,
                            )
                        }
                        RepoState.NoToken -> Text(
                            stringResource(R.string.run_launch_no_token),
                            style = MaterialTheme.typography.bodySmall,
                            color = k.muted,
                        )
                        RepoState.Failed -> Text(
                            stringResource(R.string.run_launch_repo_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        is RepoState.Loaded -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                KChip(
                                    label = stringResource(R.string.run_launch_repo_none),
                                    selected = selected == null,
                                    onClick = { selected = null },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                state.repos.forEach { repo ->
                                    KChip(
                                        label = repo.fullName,
                                        selected = selected == repo.fullName,
                                        onClick = { selected = repo.fullName },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            if (selected != null) {
                                Text(
                                    stringResource(R.string.run_launch_branch_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = k.muted,
                                )
                            }
                        }
                    }
                    if (failed) {
                        Text(
                            stringResource(R.string.run_launch_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = hasAccounts && !starting,
                onClick = {
                    starting = true
                    failed = false
                    scope.launch {
                        try {
                            onStarted(container.runLauncher.launch(setId, selected))
                        } catch (c: CancellationException) {
                            throw c
                        } catch (_: Exception) {
                            failed = true
                            starting = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.run_launch)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !starting) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
