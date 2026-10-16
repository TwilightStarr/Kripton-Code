package com.kriptoncod.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.KriptonCodApp
import com.kriptoncod.app.R
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.theme.KriptonTheme
import kotlinx.coroutines.launch

/**
 * Web oturumu hesabının düzenleme ekranı: yalnız adı değiştirir (giriş yapmaya gerek yok).
 * Oturum düştüyse [onRelogin] ile ayrı olarak yeniden giriş açılır.
 */
@Composable
fun WebAccountEditScreen(accountId: Long, onClose: () -> Unit, onRelogin: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as KriptonCodApp).container.aiAccountRepository }
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf<AiAccount?>(null) }
    var name by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(accountId) {
        val a = repo.get(accountId)
        account = a
        name = a?.displayName.orEmpty()
        loaded = true
        if (a == null) onClose()
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.web_edit_title), style = MaterialTheme.typography.headlineMedium)
        if (loaded && account != null) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.web_label)) },
                singleLine = true,
            )
            Text(
                stringResource(R.string.web_edit_relogin_hint),
                style = MaterialTheme.typography.bodySmall,
                color = KriptonTheme.colors.muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KButton(
                    stringResource(R.string.action_save),
                    {
                        val current = account ?: return@KButton
                        val newName = name.trim()
                        if (newName.isEmpty()) return@KButton
                        scope.launch {
                            if (newName != current.displayName) repo.update(current.copy(displayName = newName))
                            onClose()
                        }
                    },
                    enabled = name.isNotBlank(),
                )
                KButton(stringResource(R.string.action_cancel), onClose, style = KButtonStyle.Outline)
            }
            KButton(
                stringResource(R.string.web_edit_relogin),
                onRelogin,
                style = KButtonStyle.Outline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
