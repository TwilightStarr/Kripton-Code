package com.kriptoncod.app.ui.screens

import androidx.compose.ui.platform.LocalContext
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.data.github.GitHubTokenGuide
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
    val context = LocalContext.current
    val k = KriptonTheme.colors

    var token by remember { mutableStateOf("") }
    var hasToken by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    var ciAttempts by remember { mutableIntStateOf(3) }
    val classicWarning = stringResource(R.string.github_classic_warning)
    val savedText = stringResource(R.string.github_token_saved)

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

        // Rehber: token'ı elle girmeden ÖNCE güvenli (dar izinli) token'ın nasıl alınacağı.
        NodeCard(modifier = Modifier.fillMaxWidth(), accent = NodeAccent.Green) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.github_guide_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.github_guide_intro), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.github_guide_step1), style = MaterialTheme.typography.bodySmall, color = k.muted)
                Text(stringResource(R.string.github_guide_step2), style = MaterialTheme.typography.bodySmall, color = k.muted)
                Text(stringResource(R.string.github_guide_step3), style = MaterialTheme.typography.bodySmall, color = k.muted)
                Text(stringResource(R.string.github_guide_step4), style = MaterialTheme.typography.bodySmall, color = k.muted)
                Text(stringResource(R.string.github_guide_perms_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                Text(stringResource(R.string.github_guide_perm_contents), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.github_guide_perm_actions), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.github_guide_perm_meta), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.github_guide_optional), style = MaterialTheme.typography.bodySmall, color = k.muted)
                Text(stringResource(R.string.github_guide_avoid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                KButton(
                    text = stringResource(R.string.github_guide_open),
                    onClick = { openUrl(context, GitHubTokenGuide.createUrl(withWorkflows = false)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                KButton(
                    text = stringResource(R.string.github_guide_open_workflows),
                    onClick = { openUrl(context, GitHubTokenGuide.createUrl(withWorkflows = true)) },
                    style = KButtonStyle.Outline,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                        Text(if (hasToken) "••••••••••••" else "github_pat_xxxxxxxx")
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
                                    val classic = GitHubTokenGuide.isClassic(token)
                                    container.githubTokenStore.save(token)
                                    hasToken = true
                                    token = ""
                                    statusMessage = if (classic) "$savedText $classicWarning" else savedText
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

/** Tarayıcıda (ya da GitHub uygulamasında) bağlantıyı açar; açacak uygulama yoksa sessizce vazgeçer. */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
