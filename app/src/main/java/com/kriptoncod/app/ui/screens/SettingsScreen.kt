package com.kriptoncod.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.KriptonCodApp
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R
import com.kriptoncod.app.domain.ThemePreference
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.KChip
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    themePreference: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    /** DataStore'dan okunan mevcut OAuth istemci kimliği; boşsa "". */
    googleClientId: String,
    /** Kullanıcı değer değiştirdiğinde çağrılır; DataStore'a yazılır. */
    onGoogleClientIdChange: (String) -> Unit,
    onOpenGitHub: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val k = KriptonTheme.colors
    val context = LocalContext.current
    val store = remember { (context.applicationContext as KriptonCodApp).container.themeStore }
    val notify by store.notifyFlow.collectAsStateWithLifecycle(initialValue = true)
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        // Görünüm kartı
        NodeCard(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.bodyMedium,
                color = k.muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemePreference.entries.forEach { option ->
                    KChip(
                        label = stringResource(option.labelRes()),
                        selected = option == themePreference,
                        onClick = { onThemeChange(option) },
                    )
                }
            }
        }

        // Animasyonlar: logo animasyonu, yükleme göstergeleri ve ekran geçişleri tek anahtarla kapanır.
        val animations by store.animationsFlow.collectAsStateWithLifecycle(initialValue = true)
        NodeCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.settings_animations), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.settings_animations_hint), style = MaterialTheme.typography.bodySmall, color = k.muted)
                }
                Switch(
                    checked = animations,
                    onCheckedChange = { on -> scope.launch { store.setAnimations(on) } },
                )
            }
        }

        // Bildirimler: çalışma kapalıyken de arka planda sürer; olaylar için bildirim istenirse gönderilir.
        NodeCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.settings_notify), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.settings_notify_hint), style = MaterialTheme.typography.bodySmall, color = k.muted)
                }
                Switch(
                    checked = notify,
                    onCheckedChange = { on ->
                        scope.launch { store.setNotify(on) }
                        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
        }

        // GitHub ve izinler
        NodeCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_github), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_github_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )
                KButton(
                    text = stringResource(R.string.action_open),
                    onClick = onOpenGitHub,
                    style = KButtonStyle.Outline,
                )
            }
        }
        NodeCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_permissions), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_permissions_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )
                KButton(
                    text = stringResource(R.string.action_open),
                    onClick = onOpenPermissions,
                    style = KButtonStyle.Outline,
                )
            }
        }

        // Google OAuth ayarları — yalnızca Gemini için
        NodeCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.settings_google_auth),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.settings_google_auth_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )
                OutlinedTextField(
                    value = googleClientId,
                    onValueChange = onGoogleClientIdChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_google_client_id_label)) },
                    placeholder = { Text(stringResource(R.string.settings_google_client_id_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = k.nodeBorder,
                    ),
                )
            }
        }
    }
}

private fun ThemePreference.labelRes(): Int = when (this) {
    ThemePreference.GRAPHITE -> R.string.theme_graphite
    ThemePreference.BLACK -> R.string.theme_black
    ThemePreference.SAGE -> R.string.theme_sage
}
