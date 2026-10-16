package com.kriptoncod.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.KChip
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val CI_ATTEMPT_OPTIONS = listOf(1, 2, 3, 5, 10)

/**
 * GitHub kişisel erişim belirteci (PAT) yönetimi.
 * Token yalnızca KeyVault'a yazılır; ekranda maskeli gösterilir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GitHubSettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val k = KriptonTheme.colors

    var token by remember { mutableStateOf("") }
    var hasToken by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    var ciAttempts by remember { mutableIntStateOf(3) }

    LaunchedEffect(Unit) {
        hasToken = container.githubTokenStore.has()
        ciAttempts = container.themeStore.ciMaxAttemptsFlow.first()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Text(
                text = stringResource(R.string.github_settings_title),
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        NodeCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.github_token_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.github_token_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )

                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        statusMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.github_token_label)) },
                    placeholder = {
                        Text(if (hasToken) "••••••••••••" else "ghp_xxxxxxxxxxxx")
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = k.nodeBorder,
                    ),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KButton(
                        text = stringResource(R.string.action_save),
                        onClick = {
                            scope.launch {
                                try {
                                    container.githubTokenStore.save(token)
                                    hasToken = true
                                    token = ""
                                    statusMessage = "Token kaydedildi."
                                    isError = false
                                } catch (e: IllegalArgumentException) {
                                    statusMessage = e.message
                                    isError = true
                                }
                            }
                        },
                        enabled = token.isNotBlank(),
                    )
                    if (hasToken) {
                        KButton(
                            text = stringResource(R.string.github_token_clear),
                            onClick = {
                                scope.launch {
                                    container.githubTokenStore.clear()
                                    hasToken = false
                                    statusMessage = "Token silindi."
                                    isError = false
                                }
                            },
                            style = KButtonStyle.Danger,
                        )
                    }
                }

                statusMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        NodeCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.github_ci_attempts_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.github_ci_attempts_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CI_ATTEMPT_OPTIONS.forEach { n ->
                        KChip(
                            label = n.toString(),
                            selected = n == ciAttempts,
                            onClick = {
                                ciAttempts = n
                                scope.launch { container.themeStore.setCiMaxAttempts(n) }
                            },
                        )
                    }
                }
            }
        }

        NodeCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.github_settings_info_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.github_settings_info_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
