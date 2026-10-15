package com.kriptoncod.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kriptoncod.app.R
import com.kriptoncod.app.domain.AssignmentMode
import com.kriptoncod.app.ui.components.EmptyState
import com.kriptoncod.app.ui.components.KBadge
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.KChip
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.prompts.PromptRowUi
import com.kriptoncod.app.ui.prompts.PromptSetUiState
import com.kriptoncod.app.ui.prompts.PromptSetViewModel
import com.kriptoncod.app.ui.prompts.rememberPromptSetViewModelFactory
import com.kriptoncod.app.ui.theme.KriptonTheme

@Composable
fun PromptSetScreen(
    setId: Long,
    onBack: () -> Unit,
    onAddPrompt: () -> Unit,
    onEditPrompt: (Long) -> Unit,
) {
    val viewModel: PromptSetViewModel = viewModel(factory = rememberPromptSetViewModelFactory(setId))
    val state by viewModel.state.collectAsStateWithLifecycle()
    PromptSetContent(
        state = state,
        onBack = onBack,
        onAddPrompt = onAddPrompt,
        onEditPrompt = onEditPrompt,
        onMoveUp = viewModel::moveUp,
        onMoveDown = viewModel::moveDown,
        onAssign = viewModel::assign,
        onUnassign = viewModel::unassign,
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onCancelDelete = viewModel::cancelDelete,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptSetContent(
    state: PromptSetUiState,
    onBack: () -> Unit,
    onAddPrompt: () -> Unit,
    onEditPrompt: (Long) -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
    onAssign: (Long, Long, AssignmentMode) -> Unit,
    onUnassign: (Long) -> Unit,
    onRequestDelete: (Long) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
) {
    var assignTarget by remember { mutableStateOf<PromptRowUi?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        state.setName,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.prompts.isNotEmpty()) {
                        Text(
                            stringResource(R.string.prompts_assigned_count, state.assignedCount, state.prompts.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = KriptonTheme.colors.muted,
                        )
                    }
                }
                KButton(text = stringResource(R.string.prompts_add_prompt), onClick = onAddPrompt)
            }
        }

        when {
            state.loading -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            state.prompts.isEmpty() -> item {
                EmptyState(
                    icon = Icons.Filled.Edit,
                    title = stringResource(R.string.prompt_set_empty_title),
                    message = stringResource(R.string.prompt_set_empty_message),
                    action = { KButton(text = stringResource(R.string.prompts_add_prompt), onClick = onAddPrompt) },
                )
            }
            else -> items(state.prompts, key = { it.item.id }) { row ->
                PromptRowCard(
                    row = row,
                    isFirst = state.prompts.firstOrNull()?.item?.id == row.item.id,
                    isLast = state.prompts.lastOrNull()?.item?.id == row.item.id,
                    onEdit = { onEditPrompt(row.item.id) },
                    onMoveUp = { onMoveUp(row.item.id) },
                    onMoveDown = { onMoveDown(row.item.id) },
                    onAssign = { assignTarget = row },
                    onDelete = { onRequestDelete(row.item.id) },
                )
            }
        }
    }

    // Atama dialogu: hesap seç + Auto/Manuel mod
    assignTarget?.let { row ->
        val accounts = state.accounts
        var selectedAccount by remember(row.item.id) {
            mutableStateOf(row.assignment?.accountId)
        }
        var selectedMode by remember(row.item.id) {
            mutableStateOf(row.assignment?.mode ?: AssignmentMode.AUTO)
        }
        AlertDialog(
            onDismissRequest = { assignTarget = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.prompts_assign_title, row.item.title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (accounts.isEmpty()) {
                        Text(
                            stringResource(R.string.prompts_no_accounts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = KriptonTheme.colors.muted,
                        )
                    } else {
                        Text(stringResource(R.string.prompts_assign_account_label), style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            accounts.forEach { account ->
                                KChip(
                                    label = account.displayName,
                                    selected = selectedAccount == account.id,
                                    onClick = { selectedAccount = account.id },
                                )
                            }
                        }
                        Text(stringResource(R.string.prompts_assign_mode_label), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KChip(
                                label = stringResource(R.string.assignment_auto),
                                selected = selectedMode == AssignmentMode.AUTO,
                                onClick = { selectedMode = AssignmentMode.AUTO },
                            )
                            KChip(
                                label = stringResource(R.string.assignment_manual),
                                selected = selectedMode == AssignmentMode.MANUAL,
                                onClick = { selectedMode = AssignmentMode.MANUAL },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val accountId = selectedAccount
                        if (accountId != null) onAssign(row.item.id, accountId, selectedMode)
                        assignTarget = null
                    },
                    enabled = selectedAccount != null,
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                Row {
                    if (row.assignment != null) {
                        TextButton(onClick = { onUnassign(row.item.id); assignTarget = null }) {
                            Text(
                                stringResource(R.string.prompts_unassign),
                                color = KriptonTheme.colors.danger,
                            )
                        }
                    }
                    TextButton(onClick = { assignTarget = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            },
        )
    }

    state.pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = onCancelDelete,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.prompts_delete_prompt_title)) },
            text = { Text(stringResource(R.string.prompts_delete_prompt_message, target.title)) },
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

@Composable
private fun PromptRowCard(
    row: PromptRowUi,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onAssign: () -> Unit,
    onDelete: () -> Unit,
) {
    val assignment = row.assignment
    NodeCard(
        modifier = Modifier.fillMaxWidth(),
        accent = if (assignment != null) NodeAccent.Green else NodeAccent.Blue,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(row.item.title, style = MaterialTheme.typography.titleMedium)
                if (row.item.body.isNotBlank()) {
                    Text(
                        row.item.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = KriptonTheme.colors.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (assignment != null) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KBadge(
                            text = assignment.accountName,
                            accent = NodeAccent.Green,
                        )
                        KBadge(
                            text = stringResource(
                                if (assignment.mode == AssignmentMode.AUTO) R.string.assignment_auto
                                else R.string.assignment_manual
                            ),
                            accent = NodeAccent.Blue,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.prompts_move_up),
                        tint = if (isFirst) KriptonTheme.colors.muted.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.prompts_move_down),
                        tint = if (isLast) KriptonTheme.colors.muted.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KButton(
                text = stringResource(
                    if (assignment == null) R.string.prompts_assign else R.string.prompts_reassign
                ),
                onClick = onAssign,
                style = KButtonStyle.Outline,
            )
            KButton(text = stringResource(R.string.action_edit), onClick = onEdit, style = KButtonStyle.Outline)
            KButton(text = stringResource(R.string.action_delete), onClick = onDelete, style = KButtonStyle.Danger)
        }
    }
}
