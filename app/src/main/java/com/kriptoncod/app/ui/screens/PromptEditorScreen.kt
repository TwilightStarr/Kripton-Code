package com.kriptoncod.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.prompts.PromptEditorUiState
import com.kriptoncod.app.ui.prompts.PromptEditorViewModel
import com.kriptoncod.app.ui.prompts.rememberPromptEditorViewModelFactory

@Composable
fun PromptEditorScreen(
    setId: Long,
    promptId: Long?,
    onClose: () -> Unit,
) {
    val viewModel: PromptEditorViewModel =
        viewModel(factory = rememberPromptEditorViewModelFactory(setId, promptId))
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Kayıt başarılıysa geri dön
    LaunchedEffect(state.saved) {
        if (state.saved) onClose()
    }

    PromptEditorContent(
        state = state,
        isEdit = promptId != null,
        onBack = onClose,
        onTitleChange = viewModel::onTitleChange,
        onBodyChange = viewModel::onBodyChange,
        onSave = viewModel::save,
    )
}

@Composable
private fun PromptEditorContent(
    state: PromptEditorUiState,
    isEdit: Boolean,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                stringResource(if (isEdit) R.string.prompts_edit_prompt_title else R.string.prompts_new_prompt_title),
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        if (state.loading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            return@Column
        }

        NodeCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    label = { Text(stringResource(R.string.prompts_prompt_title_label)) },
                    placeholder = { Text(stringResource(R.string.prompts_prompt_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.body,
                    onValueChange = onBodyChange,
                    label = { Text(stringResource(R.string.prompts_prompt_body_label)) },
                    placeholder = { Text(stringResource(R.string.prompts_prompt_body_hint)) },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        KButton(
            text = stringResource(if (state.saving) R.string.action_saving else R.string.action_save),
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
