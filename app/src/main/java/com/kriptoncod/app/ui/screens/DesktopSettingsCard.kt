package com.kriptoncod.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.KriptonCodApp
import com.kriptoncod.app.R
import com.kriptoncod.app.data.settings.DesktopPrefs
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme
import java.util.Locale
import kotlinx.coroutines.launch

/** Ayarlar > Masaüstü modu: çubuk, tam ekran, yazılar, animasyonlar ve Dosyalar varsayılanları; hepsi tek yerden. */
@Composable
fun DesktopSettingsCard() {
    val k = KriptonTheme.colors
    val context = LocalContext.current
    val store = remember { (context.applicationContext as KriptonCodApp).container.themeStore }
    val scope = rememberCoroutineScope()
    val prefs by store.desktopPrefsFlow.collectAsStateWithLifecycle(initialValue = DesktopPrefs())
    fun edit(change: (DesktopPrefs) -> DesktopPrefs) {
        scope.launch { store.editDesktopPrefs(change) }
    }

    NodeCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.desktop_prefs_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.desktop_prefs_hint), style = MaterialTheme.typography.bodySmall, color = k.muted)

            PrefRow(R.string.desktop_prefs_fullscreen, R.string.desktop_prefs_fullscreen_hint, prefs.fullscreen) { v -> edit { it.copy(fullscreen = v) } }
            PrefRow(R.string.desktop_prefs_fs_dock, R.string.desktop_prefs_fs_dock_hint, prefs.fullscreenHidesDock) { v -> edit { it.copy(fullscreenHidesDock = v) } }
            PrefRow(R.string.desktop_prefs_autohide, R.string.desktop_prefs_autohide_hint, prefs.dockAutoHide) { v -> edit { it.copy(dockAutoHide = v) } }
            HorizontalDivider(color = k.nodeBorder.copy(alpha = 0.4f))
            PrefRow(R.string.desktop_prefs_labels, R.string.desktop_prefs_labels_hint, prefs.dockLabels) { v -> edit { it.copy(dockLabels = v) } }
            PrefRow(R.string.desktop_prefs_clock, null, prefs.showClock) { v -> edit { it.copy(showClock = v) } }
            PrefRow(R.string.desktop_prefs_hints, R.string.desktop_prefs_hints_hint, prefs.showHints) { v -> edit { it.copy(showHints = v) } }
            HorizontalDivider(color = k.nodeBorder.copy(alpha = 0.4f))
            Text(stringResource(R.string.desktop_prefs_motion), style = MaterialTheme.typography.titleSmall)
            PrefRow(R.string.desktop_prefs_window_anim, R.string.desktop_prefs_window_anim_hint, prefs.windowAnimations) { v -> edit { it.copy(windowAnimations = v) } }
            PrefRow(R.string.desktop_prefs_system_scale, R.string.desktop_prefs_system_scale_hint, prefs.followSystemAnimatorScale) { v -> edit { it.copy(followSystemAnimatorScale = v) } }
            SpeedRow(prefs.motionSpeed, enabled = prefs.windowAnimations) { v -> edit { it.copy(motionSpeed = v) } }
            HorizontalDivider(color = k.nodeBorder.copy(alpha = 0.4f))
            Text(stringResource(R.string.desktop_prefs_files), style = MaterialTheme.typography.titleSmall)
            PrefRow(R.string.desktop_files_hidden, null, prefs.filesHidden) { v -> edit { it.copy(filesHidden = v) } }
            PrefRow(R.string.desktop_files_show_ext, null, prefs.filesExt) { v -> edit { it.copy(filesExt = v) } }
            PrefRow(R.string.desktop_files_grid, null, prefs.filesGrid) { v -> edit { it.copy(filesGrid = v) } }
            KButton(
                text = stringResource(R.string.desktop_prefs_reset),
                onClick = { edit { DesktopPrefs() } },
                style = KButtonStyle.Outline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PrefRow(title: Int, hint: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyMedium)
            if (hint != null) Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = KriptonTheme.colors.muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** "Geçiş hızı" kaydırıcısı (0,5×–2×, 0,25 adımlarla). Bırakılınca kaydedilir; sürüklerken yalnız değer yazısı güncellenir. */
@Composable
private fun SpeedRow(speed: Float, enabled: Boolean, onCommit: (Float) -> Unit) {
    val k = KriptonTheme.colors
    var value by remember(speed) { mutableFloatStateOf(speed) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.desktop_prefs_speed), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("%.2f".format(Locale.ROOT, value) + "×", style = MaterialTheme.typography.labelLarge, color = k.muted)
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onCommit(value) },
            valueRange = DesktopPrefs.MOTION_SPEED_MIN..DesktopPrefs.MOTION_SPEED_MAX,
            steps = 5,
            enabled = enabled,
        )
        Text(stringResource(R.string.desktop_prefs_speed_hint), style = MaterialTheme.typography.bodySmall, color = k.muted)
    }
}
