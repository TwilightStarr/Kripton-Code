package com.kriptoncod.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.widget.Toast
import com.kriptoncod.app.data.power.Oem
import com.kriptoncod.app.data.power.PowerSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.KriptonCodApp
import com.kriptoncod.app.data.crash.CrashLogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.kriptoncod.app.BuildConfig
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

        // Pil kısıtlaması: Xiaomi gibi cihazlarda arka plan işi sonlandırılmasın. Ekrandan dönünce durum yenilenir.
        var batteryOk by remember { mutableStateOf(PowerSettings.isIgnoringBatteryOptimizations(context)) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { batteryOk = PowerSettings.isIgnoringBatteryOptimizations(context) }
        val oem = remember { PowerSettings.detectOem(Build.MANUFACTURER, Build.BRAND) }
        val oemFailed = stringResource(R.string.settings_battery_oem_failed)
        NodeCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_battery_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_battery_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )
                Text(
                    stringResource(
                        if (batteryOk) R.string.settings_battery_status_ok else R.string.settings_battery_status_restricted,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (batteryOk) k.arrow else MaterialTheme.colorScheme.tertiary,
                )
                if (!batteryOk) {
                    KButton(
                        text = stringResource(R.string.settings_battery_request),
                        onClick = {
                            // Doğrudan iletişim kutusu; açılamazsa pil optimizasyonu listesine, o da olmazsa uygulama ayrıntılarına düş.
                            PowerSettings.openFirst(
                                context,
                                listOf(PowerSettings.requestIntent(context), PowerSettings.listIntent(), PowerSettings.detailsIntent(context)),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                KButton(
                    text = stringResource(R.string.settings_battery_open_list),
                    onClick = {
                        PowerSettings.openFirst(context, listOf(PowerSettings.listIntent(), PowerSettings.detailsIntent(context)))
                    },
                    style = KButtonStyle.Outline,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (oem != Oem.OTHER) {
                    Text(
                        stringResource(
                            if (oem == Oem.XIAOMI) R.string.settings_battery_oem_xiaomi else R.string.settings_battery_oem_generic,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = k.muted,
                    )
                    KButton(
                        text = stringResource(R.string.settings_battery_oem),
                        onClick = {
                            if (!PowerSettings.openOemSettings(context, oem)) {
                                Toast.makeText(context, oemFailed, Toast.LENGTH_LONG).show()
                            }
                        },
                        style = KButtonStyle.Outline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Kurulu sürüm: güncellemenin gerçekten yüklendiğini uygulamanın içinden doğrulamak için.
        NodeCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings_version_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Yerel çökme günlüğü (ACRA): kayıtlar yalnız cihazda; paylaşmak kullanıcının elinde.
        val crashStore = remember { CrashLogStore.default(context) }
        var crashCount by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) { crashCount = withContext(Dispatchers.IO) { crashStore.list().size } }
        val crashShareTitle = stringResource(R.string.settings_crash_share_title)
        NodeCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_crash_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_crash_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )
                Text(
                    if (crashCount == 0) {
                        stringResource(R.string.settings_crash_none)
                    } else {
                        stringResource(R.string.settings_crash_count, crashCount)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (crashCount > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        KButton(
                            text = stringResource(R.string.settings_crash_share),
                            onClick = {
                                scope.launch {
                                    val text = withContext(Dispatchers.IO) { crashStore.latestText() } ?: return@launch
                                    val send = Intent(Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, text)
                                    runCatching { context.startActivity(Intent.createChooser(send, crashShareTitle)) }
                                }
                            },
                            style = KButtonStyle.Outline,
                            modifier = Modifier.weight(1f),
                        )
                        KButton(
                            text = stringResource(R.string.settings_crash_clear),
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { crashStore.clear() }
                                    crashCount = 0
                                }
                            },
                            style = KButtonStyle.Danger,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
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
    ThemePreference.SYSTEM -> R.string.theme_system
    ThemePreference.DARK -> R.string.theme_dark
    ThemePreference.LIGHT -> R.string.theme_light
}
