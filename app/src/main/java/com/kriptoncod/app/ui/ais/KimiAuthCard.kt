package com.kriptoncod.app.ui.ais

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.components.KriptonLoader
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme

@Composable
fun KimiAuthCard(
    accountId: Long?,
    factory: KimiAuthViewModel.Factory,
) {
    val vm: KimiAuthViewModel = viewModel(key = "kimi-auth-$accountId", factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    NodeCard(modifier = Modifier.fillMaxWidth(), accent = NodeAccent.Blue) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.kimi_auth_title),
                style = MaterialTheme.typography.titleMedium,
            )
            when (val s = state) {
                is KimiAuthCardState.Connected -> {
                    Text(
                        stringResource(R.string.kimi_auth_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    KButton(
                        text = stringResource(R.string.kimi_auth_disconnect),
                        onClick = vm::signOut,
                        style = KButtonStyle.Outline,
                    )
                }
                is KimiAuthCardState.Loading -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KriptonLoader(size = 28.dp)
                        Text(
                            stringResource(R.string.kimi_auth_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = KriptonTheme.colors.muted,
                        )
                    }
                }
                else -> {
                    if (s is KimiAuthCardState.NeedsReauth) {
                        Text(
                            stringResource(R.string.kimi_auth_needs_reauth),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (s is KimiAuthCardState.Error) {
                        Text(
                            stringResource(s.messageRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        stringResource(R.string.kimi_auth_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = KriptonTheme.colors.muted,
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.kimi_auth_email_label)) },
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.kimi_auth_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    KButton(
                        text = stringResource(R.string.kimi_auth_connect),
                        onClick = { vm.signIn(email, password) },
                        enabled = email.isNotBlank() && password.isNotBlank() &&
                            s !is KimiAuthCardState.Loading,
                    )
                    if (s is KimiAuthCardState.NeedsReauth) {
                        KButton(
                            text = stringResource(R.string.kimi_auth_connect),
                            onClick = vm::silentRefresh,
                            style = KButtonStyle.Outline,
                        )
                    }
                }
            }
        }
    }
}
