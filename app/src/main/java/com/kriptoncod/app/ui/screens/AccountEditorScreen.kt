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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kriptoncod.app.R
import com.kriptoncod.app.data.providers.Provider
import com.kriptoncod.app.data.providers.ProviderCatalog
import com.kriptoncod.app.ui.ais.AccountEditorViewModel
import com.kriptoncod.app.ui.ais.ApiKeyOnlyCard
import com.kriptoncod.app.ui.ais.EditorError
import com.kriptoncod.app.ui.ais.EditorUiState
import com.kriptoncod.app.ui.ais.EmailAuthCard
import com.kriptoncod.app.ui.ais.GoogleAuthCard
import com.kriptoncod.app.ui.ais.GoogleAuthViewModel
import com.kriptoncod.app.ui.ais.ModelOption
import com.kriptoncod.app.ui.ais.Pricing
import com.kriptoncod.app.ui.ais.rememberEditorViewModelFactory
import com.kriptoncod.app.ui.ais.rememberEmailAuthViewModelFactory
import com.kriptoncod.app.ui.ais.rememberGoogleAuthViewModelFactory
import com.kriptoncod.app.ui.components.KBadge
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme

private const val INITIAL_MODEL_COUNT = 30

@Composable
fun AccountEditorScreen(accountId: Long?, onClose: () -> Unit) {
    val viewModel: AccountEditorViewModel = viewModel(
        key = "account-editor-$accountId",
        factory = rememberEditorViewModelFactory(accountId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onClose() }

    val clipboard = LocalClipboardManager.current
    AccountEditorContent(
        state = state,
        accountId = accountId,
        onBack = onClose,
        onSelectProvider = viewModel::selectProvider,
        onKeyChange = viewModel::onKeyChanged,
        onPaste = {
            val text = clipboard.getText()?.text
            if (text.isNullOrBlank()) viewModel.onPasteEmpty() else viewModel.onKeyChanged(text)
        },
        onVerify = viewModel::verify,
        onSelectModel = viewModel::selectModel,
        onSave = viewModel::save,
        onRetry = viewModel::retry,
        onDismissError = viewModel::dismissError,
    )
}

@Composable
private fun AccountEditorContent(
    state: EditorUiState,
    accountId: Long?,
    onBack: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onPaste: () -> Unit,
    onVerify: () -> Unit,
    onSelectModel: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
) {
    val provider = state.providerId?.let { ProviderCatalog.byId(it) }
    var showAllModels by rememberSaveable { mutableStateOf(false) }
    val visibleModels = if (showAllModels) state.models else state.models.take(INITIAL_MODEL_COUNT)

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                stringResource(if (state.isEdit) R.string.ais_edit_title else R.string.ais_add_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading) {
                item { Centered { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) } }
            }

            if (state.isEdit) {
                if (provider != null) {
                    item { ProviderCard(provider, selected = true, onClick = null, subtitle = state.displayName) }
                }
            } else {
                item { SectionTitle(R.string.step_provider) }
                items(AccountEditorViewModel.SELECTABLE_PROVIDERS, key = { it.id }) { p ->
                    ProviderCard(p, selected = p.id == state.providerId, onClick = { onSelectProvider(p.id) })
                }
            }

            if (provider != null && !state.loading) {
                item { SectionTitle(R.string.step_auth) }

                when {
                    provider.supportsGoogleAuth -> {
                        // Gemini: Google OAuth kartı
                        item(key = "google-auth-card") {
                            val googleFactory = rememberGoogleAuthViewModelFactory(accountId)
                            GoogleAuthCard(accountId = accountId, factory = googleFactory)
                        }
                        // Ek olarak API key alternatifi
                        item { SectionTitle(R.string.step_key_alternative) }
                        item { KeySection(state, provider, onKeyChange, onPaste, onVerify) }
                    }
                    provider.supportsEmailAuth -> {
                        // Claude / ChatGPT: e-posta/şifre giriş kartı
                        item(key = "email-auth-card") {
                            val emailFactory = rememberEmailAuthViewModelFactory(
                                accountId = accountId,
                                providerId = provider.id,
                            )
                            EmailAuthCard(accountId = accountId, factory = emailFactory)
                        }
                        // Ek olarak API key alternatifi
                        item { SectionTitle(R.string.step_key_alternative) }
                        item { KeySection(state, provider, onKeyChange, onPaste, onVerify) }
                    }
                    else -> {
                        // Diğer sağlayıcılar: yalnız API anahtarı
                        item(key = "api-key-only-info") {
                            ApiKeyOnlyCard(keyUrl = provider.keyUrl)
                        }
                        item { SectionTitle(R.string.step_key) }
                        item { KeySection(state, provider, onKeyChange, onPaste, onVerify) }
                    }
                }
            }

            state.error?.let { error ->
                item(key = "error") { ErrorCard(error, onRetry, onDismissError) }
            }

            if (state.models.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionTitle(R.string.step_model)
                        Text(
                            stringResource(R.string.models_found, state.models.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = KriptonTheme.colors.muted,
                        )
                    }
                }
                items(visibleModels, key = { it.id }) { model ->
                    ModelRow(model, selected = model.id == state.selectedModelId, onClick = { onSelectModel(model.id) })
                }
                if (!showAllModels && state.models.size > INITIAL_MODEL_COUNT) {
                    item {
                        KButton(
                            text = stringResource(R.string.models_show_more, state.models.size - INITIAL_MODEL_COUNT),
                            onClick = { showAllModels = true },
                            style = KButtonStyle.Outline,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.5.dp, color = KriptonTheme.colors.nodeBorder)
        KButton(
            text = stringResource(if (state.saving) R.string.action_saving else R.string.action_save),
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Composable
private fun SectionTitle(@androidx.annotation.StringRes res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderCard(provider: Provider, selected: Boolean, onClick: (() -> Unit)?, subtitle: String? = null) {
    val k = KriptonTheme.colors
    NodeCard(
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
        accent = if (selected) NodeAccent.Green else NodeAccent.Blue,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(provider.nameRes), style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = k.muted)
            }
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = k.arrow)
        }
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            KBadge(
                text = stringResource(if (provider.hasFreeTier) R.string.provider_free_tier else R.string.provider_paid_only),
                accent = if (provider.hasFreeTier) NodeAccent.Green else NodeAccent.Blue,
            )
            if (provider.supportsGoogleAuth) {
                KBadge(text = stringResource(R.string.provider_google_auth), accent = NodeAccent.Blue)
            }
            if (provider.supportsEmailAuth) {
                KBadge(text = stringResource(R.string.provider_email_auth), accent = NodeAccent.Blue)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeySection(
    state: EditorUiState,
    provider: Provider,
    onKeyChange: (String) -> Unit,
    onPaste: () -> Unit,
    onVerify: () -> Unit,
) {
    val k = KriptonTheme.colors
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.apiKey.value,
            onValueChange = onKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.key_label)) },
            singleLine = true,
            enabled = !state.saving && !state.loading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = k.nodeBorder,
            ),
        )
        Text(
            stringResource(if (state.isEdit) R.string.key_edit_hint else R.string.key_stored_securely),
            style = MaterialTheme.typography.bodySmall,
            color = k.muted,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton(
                text = stringResource(R.string.key_paste),
                onClick = onPaste,
                style = KButtonStyle.Outline,
                enabled = !state.saving && !state.loading,
            )
            KButton(
                text = stringResource(if (state.verifying) R.string.action_verifying else R.string.action_verify),
                onClick = onVerify,
                enabled = state.canVerify,
            )
            provider.keyUrl?.let { url ->
                KButton(
                    text = stringResource(R.string.provider_get_key),
                    onClick = { runCatching { uriHandler.openUri(url) } },
                    style = KButtonStyle.Outline,
                )
            }
        }
        if (state.verifying) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.padding(2.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                Text(stringResource(R.string.action_verifying), style = MaterialTheme.typography.bodyMedium, color = k.muted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ErrorCard(error: EditorError, onRetry: () -> Unit, onDismiss: () -> Unit) {
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
            if (error.retry != null) KButton(stringResource(R.string.action_retry), onRetry)
            KButton(stringResource(R.string.action_cancel), onDismiss, style = KButtonStyle.Outline)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelRow(model: ModelOption, selected: Boolean, onClick: () -> Unit) {
    val k = KriptonTheme.colors
    NodeCard(
        modifier = Modifier.fillMaxWidth().semantics {
            role = Role.RadioButton
            this.selected = selected
        },
        accent = if (selected) NodeAccent.Green else NodeAccent.Blue,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = k.arrow, unselectedColor = k.muted),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(model.id, style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (model.recommended) {
                        KBadge(stringResource(R.string.model_recommended), accent = NodeAccent.Green)
                    }
                    when (model.pricing) {
                        Pricing.FREE      -> KBadge(stringResource(R.string.model_free), accent = NodeAccent.Green)
                        Pricing.FREE_TIER -> KBadge(stringResource(R.string.model_free_tier))
                        Pricing.PAID      -> KBadge(stringResource(R.string.model_paid))
                    }
                }
            }
        }
    }
}
