package com.kriptoncod.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kriptoncod.app.R
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.providers.ProviderCatalog
import com.kriptoncod.app.data.providers.UpstreamProvider
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.ui.ais.AisUiState
import com.kriptoncod.app.ui.ais.AisViewModel
import com.kriptoncod.app.ui.ais.ListError
import com.kriptoncod.app.ui.ais.rememberAisViewModelFactory
import com.kriptoncod.app.ui.components.EmptyState
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme

@Composable
fun AisScreen(onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val viewModel: AisViewModel = viewModel(factory = rememberAisViewModelFactory())
    val state by viewModel.state.collectAsStateWithLifecycle()
    AisContent(
        state = state,
        onAdd = onAdd,
        onEdit = onEdit,
        onToggle = viewModel::setEnabled,
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onCancelDelete = viewModel::cancelDelete,
        onRetry = viewModel::retry,
        onDismissError = viewModel::dismissError,
    )
}

@Composable
private fun AisContent(
    state: AisUiState,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onRequestDelete: (Long) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.ais_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                KButton(text = stringResource(R.string.ais_add), onClick = onAdd)
            }
        }
        state.error?.let { error ->
            item(key = "error") { ErrorCard(error, onRetry, onDismissError) }
        }
        when {
            state.loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            state.accounts.isEmpty() && state.error == null -> item(key = "empty") {
                EmptyState(
                    icon = Icons.Filled.Face,
                    title = stringResource(R.string.ais_empty_title),
                    message = stringResource(R.string.ais_empty_message),
                    action = { KButton(text = stringResource(R.string.ais_add), onClick = onAdd) },
                )
            }
            else -> items(state.accounts, key = { it.id }) { account ->
                AccountCard(account, onToggle, onEdit, onRequestDelete)
            }
        }
    }

    state.pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = onCancelDelete,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.delete_dialog_title)) },
            text = { Text(stringResource(R.string.delete_dialog_message, target.displayName)) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(stringResource(R.string.action_delete), color = KriptonTheme.colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ErrorCard(error: ListError, onRetry: () -> Unit, onDismiss: () -> Unit) {
    NodeCard(modifier = Modifier.fillMaxWidth(), accent = NodeAccent.Red) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = KriptonTheme.colors.danger)
            Text(stringResource(error.messageRes), style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KButton(stringResource(R.string.action_retry), onRetry)
            KButton(stringResource(R.string.action_cancel), onDismiss, style = KButtonStyle.Outline)
        }
    }
}

@Composable
private fun AccountCard(
    account: AiAccount,
    onToggle: (Long, Boolean) -> Unit,
    onEdit: (Long) -> Unit,
    onRequestDelete: (Long) -> Unit,
) {
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme
    val isProxy = account.authType == AuthType.PROXY
    val providerName = if (isProxy) {
        val upstream = UpstreamProvider.fromId(account.providerId)
            ?.let { stringResource(it.labelRes()) } ?: account.providerId
        stringResource(R.string.account_proxy_line, upstream)
    } else {
        ProviderCatalog.byId(account.providerId)?.let { stringResource(it.nameRes) } ?: account.providerId
    }
    val toggleLabel = stringResource(R.string.account_toggle_description, account.displayName)
    val editLabel = stringResource(R.string.account_edit_description, account.displayName)
    val deleteLabel = stringResource(R.string.account_delete_description, account.displayName)

    val (statusText, statusColor) = when {
        !account.enabled -> stringResource(R.string.status_disabled) to k.muted
        account.status == AccountStatus.ERROR -> stringResource(R.string.status_error) to k.danger
        account.status == AccountStatus.WAITING -> stringResource(R.string.status_waiting) to scheme.primary
        else -> stringResource(R.string.status_ready) to k.arrow
    }

    NodeCard(
        modifier = Modifier.fillMaxWidth().alpha(if (account.enabled) 1f else 0.7f),
        accent = if (account.status == AccountStatus.ERROR && account.enabled) NodeAccent.Red else NodeAccent.Blue,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(account.displayName, style = MaterialTheme.typography.titleMedium)
                Text(providerName, style = MaterialTheme.typography.bodyMedium, color = k.muted)
                Text(account.modelId, style = MaterialTheme.typography.bodySmall, color = k.muted)
                if (isProxy && !account.baseUrl.isNullOrBlank()) {
                    Text(account.baseUrl, style = MaterialTheme.typography.bodySmall, color = k.muted)
                }
            }
            Switch(
                checked = account.enabled,
                onCheckedChange = { onToggle(account.id, it) },
                modifier = Modifier.semantics { contentDescription = toggleLabel },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = scheme.onPrimary,
                    checkedTrackColor = scheme.primary,
                    checkedBorderColor = scheme.primary,
                    uncheckedThumbColor = k.muted,
                    uncheckedTrackColor = scheme.surface,
                    uncheckedBorderColor = k.nodeBorder,
                ),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).background(statusColor, CircleShape))
                Text(statusText, style = MaterialTheme.typography.labelLarge, color = statusColor)
            }
            IconButton(onClick = { onEdit(account.id) }) {
                Icon(Icons.Filled.Edit, contentDescription = editLabel, tint = scheme.primary)
            }
            IconButton(onClick = { onRequestDelete(account.id) }) {
                Icon(Icons.Filled.Delete, contentDescription = deleteLabel, tint = k.danger)
            }
        }
    }
}
