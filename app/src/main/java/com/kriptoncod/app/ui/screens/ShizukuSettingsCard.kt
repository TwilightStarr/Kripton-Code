package com.kriptoncod.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.KriptonCodApp
import com.kriptoncod.app.R
import com.kriptoncod.app.data.shizuku.PrivMode
import com.kriptoncod.app.data.shizuku.ShizukuFiles
import com.kriptoncod.app.data.shizuku.ShizukuStatus
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme
import kotlinx.coroutines.launch

private const val SHIZUKU_DOWNLOAD = "https://shizuku.rikka.app/download/"

/**
 * Ayarlar: "Shizuku ile sistem dosyaları". Varsayılan KAPALI. Açılınca Masaüstü > Dosyalar penceresine
 * "Sistem" ve "Uygulama verileri" kaynakları eklenir. Salt okunurdur; hiçbir dosya kendiliğinden gönderilmez.
 */
@Composable
fun ShizukuSettingsCard() {
    val k = KriptonTheme.colors
    val context = LocalContext.current
    val store = remember { (context.applicationContext as KriptonCodApp).container.themeStore }
    val scope = rememberCoroutineScope()
    val enabled by store.shizukuFilesFlow.collectAsStateWithLifecycle(initialValue = false)
    val state by ShizukuFiles.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { ShizukuFiles.refresh() }

    NodeCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.settings_shizuku), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.settings_shizuku_hint), style = MaterialTheme.typography.bodySmall, color = k.muted)
                }
                Switch(checked = enabled, onCheckedChange = { on -> scope.launch { store.setShizukuFiles(on) } })
            }
            if (enabled) {
                Text(
                    stringResource(
                        when (state.status) {
                            ShizukuStatus.NOT_INSTALLED -> R.string.shizuku_status_not_installed
                            ShizukuStatus.NOT_RUNNING -> R.string.shizuku_status_not_running
                            ShizukuStatus.NEEDS_PERMISSION -> R.string.shizuku_status_needs_permission
                            ShizukuStatus.READY -> when (state.mode) {
                                PrivMode.ROOT -> R.string.shizuku_status_ready_root
                                PrivMode.ADB -> R.string.shizuku_status_ready_adb
                                else -> R.string.shizuku_status_ready_other
                            }
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                when (state.status) {
                    ShizukuStatus.NOT_INSTALLED -> KButton(
                        text = stringResource(R.string.shizuku_action_download),
                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD))) } },
                        style = KButtonStyle.Outline,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ShizukuStatus.NOT_RUNNING -> KButton(
                        text = stringResource(R.string.shizuku_action_open),
                        onClick = { ShizukuFiles.launchShizukuApp(context) },
                        style = KButtonStyle.Outline,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ShizukuStatus.NEEDS_PERMISSION -> KButton(
                        text = stringResource(R.string.shizuku_action_grant),
                        onClick = { if (!ShizukuFiles.requestPermission()) ShizukuFiles.launchShizukuApp(context) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ShizukuStatus.READY -> Unit
                }
                Text(stringResource(R.string.settings_shizuku_policy), style = MaterialTheme.typography.labelSmall, color = k.muted)
            }
        }
    }
}
