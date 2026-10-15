package com.kriptoncod.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kriptoncod.app.R
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KChip
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.prompts.DispatchViewModel

@Composable
fun DispatchScreen(
    setId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as com.kriptoncod.app.KriptonCodApp).container
    val vm: DispatchViewModel = viewModel(factory = DispatchViewModel.factory(setId, container))
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back))
            }
            Text("Dağıtım", style = MaterialTheme.typography.headlineMedium)
        }

        KButton(text = "Otomatik Düzenle", onClick = vm::autoArrange,
            modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))

        Text("AI Sıralaması", style = MaterialTheme.typography.titleSmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.summaries, key = { it.account.id }) { summary ->
                NodeCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("⋮⋮")
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(summary.account.displayName,
                                style = MaterialTheme.typography.titleSmall)
                            Text("${summary.promptCount} prompt · ~${summary.totalTokens} token",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        val statusColor = when (summary.account.status) {
                            AccountStatus.READY -> MaterialTheme.colorScheme.primary
                            AccountStatus.WAITING -> MaterialTheme.colorScheme.tertiary
                            AccountStatus.ERROR -> MaterialTheme.colorScheme.error
                        }
                        Text(when (summary.account.status) {
                            AccountStatus.READY -> "●"
                            AccountStatus.WAITING -> "◐"
                            AccountStatus.ERROR -> "○"
                        }, color = statusColor)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Promptlar", style = MaterialTheme.typography.titleSmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.prompts, key = { it.id }) { prompt ->
                val assignedAccountId = state.plan.assignments[prompt.id]
                NodeCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Prompt #${prompt.id}", style = MaterialTheme.typography.titleSmall)
                        Text("~${prompt.estimatedTokens} token",
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.accounts.forEach { account ->
                                KChip(
                                    label = account.displayName,
                                    selected = assignedAccountId == account.id,
                                    onClick = { vm.assignPrompt(prompt.id, account.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
