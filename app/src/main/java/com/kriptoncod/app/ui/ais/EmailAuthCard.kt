package com.kriptoncod.app.ui.ais

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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

/**
 * Claude ve ChatGPT için "E-posta ile bağlan" kartı.
 * Yalnız [supportsEmailAuth] == true olan sağlayıcılarda gösterilir.
 * GoogleAuthCard ile paralel yapı.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmailAuthCard(
    accountId: Long?,
    factory: EmailAuthViewModel.Factory,
    modifier: Modifier = Modifier,
) {
    val vm: EmailAuthViewModel = viewModel(
        key = "email-auth-$accountId",
        factory = factory,
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val k = KriptonTheme.colors

    // Form alanları yalnız Disconnected / NeedsReauth / Error durumunda gösterilir
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val showForm = state is EmailAuthCardState.Disconnected
        || state is EmailAuthCardState.NeedsReauth
        || state is EmailAuthCardState.Error

    NodeCard(
        modifier = modifier.fillMaxWidth(),
        accent = when (state) {
            is EmailAuthCardState.Connected  -> NodeAccent.Green
            is EmailAuthCardState.Error,
            is EmailAuthCardState.NeedsReauth -> NodeAccent.Red
            else -> NodeAccent.Blue
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Başlık + durum simgesi
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (state) {
                    is EmailAuthCardState.Connected ->
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = k.arrow)
                    is EmailAuthCardState.Error,
                    is EmailAuthCardState.NeedsReauth ->
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = k.danger)
                    is EmailAuthCardState.Loading ->
                        KriptonLoader(size = 28.dp)
                    else -> Unit
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.email_auth_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val subtitle = when (state) {
                        is EmailAuthCardState.Connected    -> stringResource(R.string.email_auth_connected)
                        is EmailAuthCardState.NeedsReauth  -> stringResource(R.string.email_auth_needs_reauth)
                        is EmailAuthCardState.Error        -> stringResource(R.string.email_auth_sign_in_failed)
                        is EmailAuthCardState.Disconnected -> stringResource(R.string.email_auth_hint)
                        is EmailAuthCardState.Loading      -> stringResource(R.string.email_auth_loading)
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = k.muted)
                }
            }

            // Giriş formu
            if (showForm) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.email_auth_email_label)) },
                    singleLine = true,
                    enabled = state !is EmailAuthCardState.Loading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = k.nodeBorder,
                    ),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.email_auth_password_label)) },
                    singleLine = true,
                    enabled = state !is EmailAuthCardState.Loading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = k.nodeBorder,
                    ),
                )
            }

            // Eylem düğmeleri
            if (state !is EmailAuthCardState.Loading) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (state) {
                        is EmailAuthCardState.Disconnected,
                        is EmailAuthCardState.NeedsReauth,
                        is EmailAuthCardState.Error ->
                            KButton(
                                text = stringResource(R.string.email_auth_connect),
                                onClick = { vm.signIn(email, password) },
                                enabled = email.isNotBlank() && password.isNotBlank(),
                            )
                        is EmailAuthCardState.Connected ->
                            KButton(
                                text = stringResource(R.string.email_auth_disconnect),
                                onClick = {
                                    vm.signOut()
                                    email = ""
                                    password = ""
                                },
                                style = KButtonStyle.Outline,
                            )
                        else -> Unit
                    }
                }
            }
        }
    }
}
