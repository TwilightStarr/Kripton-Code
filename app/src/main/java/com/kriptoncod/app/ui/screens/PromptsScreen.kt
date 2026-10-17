package com.kriptoncod.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.kriptoncod.app.ui.adaptive.Adaptive
import com.kriptoncod.app.ui.adaptive.LocalWindowClass
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kriptoncod.app.domain.Catalog
import com.kriptoncod.app.domain.CatalogParser
import com.kriptoncod.app.ui.ais.rememberContainer
import java.util.zip.ZipInputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kriptoncod.app.R
import com.kriptoncod.app.data.db.PromptSet
import com.kriptoncod.app.ui.components.EmptyState
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.prompts.PromptsUiState
import com.kriptoncod.app.ui.prompts.PromptsViewModel
import com.kriptoncod.app.ui.prompts.rememberPromptsViewModelFactory
import com.kriptoncod.app.ui.theme.KriptonTheme

@Composable
fun PromptsScreen(onOpenSet: (Long) -> Unit, onRunStarted: (Long) -> Unit = {}) {
    val viewModel: PromptsViewModel = viewModel(factory = rememberPromptsViewModelFactory())
    val state by viewModel.state.collectAsStateWithLifecycle()
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by container.aiAccountRepository.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var zipCatalogs by remember { mutableStateOf<List<Catalog>?>(null) }
    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val parsed = withContext(Dispatchers.IO) {
                    try {
                        val entries = mutableListOf<Pair<String, ByteArray>>()
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            ZipInputStream(input).use { zin ->
                                while (true) {
                                    val e = zin.nextEntry ?: break
                                    if (e.isDirectory) continue
                                    val bytes = zin.readBytes()
                                    entries += e.name to bytes
                                }
                            }
                        }
                        CatalogParser.parseZip(entries)
                    } catch (c: CancellationException) {
                        throw c
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                if (parsed.any { it.prompts.isNotEmpty() }) {
                    zipCatalogs = parsed
                } else {
                    Toast.makeText(context, R.string.zip_run_invalid, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    zipCatalogs?.let { cats ->
        RunLaunchDialog(
            launch = { repo -> container.runLauncher.launchCatalogs(cats, repo) },
            hasAccounts = accounts.isNotEmpty(),
            onDismiss = { zipCatalogs = null },
            onStarted = { id -> zipCatalogs = null; onRunStarted(id) },
        )
    }
    PromptsContent(
        onRunFromZip = { pickZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
        state = state,
        onOpenSet = onOpenSet,
        onCreate = viewModel::createSet,
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onCancelDelete = viewModel::cancelDelete,
    )
}

@Composable
private fun PromptsContent(
    state: PromptsUiState,
    onRunFromZip: () -> Unit,
    onOpenSet: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onRequestDelete: (Long) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(Adaptive.CardMinWidth),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Adaptive.pagePadding(LocalWindowClass.current)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.prompts_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                KButton(
                    text = stringResource(R.string.prompts_add_set),
                    onClick = { showCreate = true },
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            KButton(
                text = stringResource(R.string.zip_run),
                onClick = onRunFromZip,
                style = KButtonStyle.Outline,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when {
            state.loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            state.sets.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    icon = Icons.Filled.Edit,
                    title = stringResource(R.string.prompts_empty_title),
                    message = stringResource(R.string.prompts_empty_message),
                    action = {
                        KButton(
                            text = stringResource(R.string.prompts_add_set),
                            onClick = { showCreate = true },
                        )
                    },
                )
            }
            else -> items(state.sets, key = { it.id }) { set ->
                SetCard(set = set, onOpen = { onOpenSet(set.id) }, onDelete = { onRequestDelete(set.id) })
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.prompts_create_set_title)) },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.prompts_set_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onCreate(name); showCreate = false },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    state.pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = onCancelDelete,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.prompts_delete_set_title)) },
            text = { Text(stringResource(R.string.prompts_delete_set_message, target.name)) },
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
private fun SetCard(set: PromptSet, onOpen: () -> Unit, onDelete: () -> Unit) {
    NodeCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(set.name, style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.prompts_delete_set_description, set.name),
                    tint = KriptonTheme.colors.danger,
                )
            }
        }
    }
}
