package com.kriptoncod.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.R
import com.kriptoncod.app.data.settings.ModeState
import com.kriptoncod.app.domain.ModeCodec
import com.kriptoncod.app.domain.ModeProfile
import com.kriptoncod.app.ui.ais.rememberContainer
import com.kriptoncod.app.ui.components.KBadge
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.KChip
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Sayısal bir ayar: başlık, ipucu, izinli aralık, birim ve profile okuma/yazma. Ekran bu listeden çizilir. */
private class NumSetting(
    @param:StringRes val title: Int,
    @param:StringRes val hint: Int,
    val range: IntRange,
    @param:StringRes val unit: Int?,
    val get: (ModeProfile) -> Int,
    val set: (ModeProfile, Int) -> ModeProfile,
)

private class SettingGroup(@param:StringRes val title: Int, @param:StringRes val hint: Int, val items: List<NumSetting>)

private val L = ModeProfile.Limits

private val GROUPS: List<SettingGroup> = listOf(
    SettingGroup(
        R.string.mode_group_run,
        R.string.mode_group_run_hint,
        listOf(
            NumSetting(R.string.mode_parallel, R.string.mode_parallel_hint, L.PARALLEL, null, { it.maxParallel }, { p, v -> p.copy(maxParallel = v) }),
            NumSetting(R.string.mode_attempts, R.string.mode_attempts_hint, L.ATTEMPTS, null, { it.maxAttempts }, { p, v -> p.copy(maxAttempts = v) }),
            NumSetting(R.string.mode_retry_base, R.string.mode_retry_base_hint, L.RETRY_BASE_SEC, R.string.mode_unit_sec, { it.retryBaseSec }, { p, v -> p.copy(retryBaseSec = v) }),
            NumSetting(R.string.mode_retry_max, R.string.mode_retry_max_hint, L.RETRY_MAX_MIN, R.string.mode_unit_min, { it.maxRetryDelayMin }, { p, v -> p.copy(maxRetryDelayMin = v) }),
            NumSetting(R.string.mode_no_account, R.string.mode_no_account_hint, L.NO_ACCOUNT_MIN, R.string.mode_unit_min, { it.noAccountWaitMin }, { p, v -> p.copy(noAccountWaitMin = v) }),
        ),
    ),
    SettingGroup(
        R.string.mode_group_pool,
        R.string.mode_group_pool_hint,
        listOf(
            NumSetting(R.string.mode_breaker_streak, R.string.mode_breaker_streak_hint, L.BREAKER_STREAK, null, { it.breakerStreak }, { p, v -> p.copy(breakerStreak = v) }),
            NumSetting(R.string.mode_breaker_cooldown, R.string.mode_breaker_cooldown_hint, L.BREAKER_COOLDOWN_MIN, R.string.mode_unit_min, { it.breakerCooldownMin }, { p, v -> p.copy(breakerCooldownMin = v) }),
            NumSetting(R.string.mode_unhealthy, R.string.mode_unhealthy_hint, L.UNHEALTHY_PCT, R.string.mode_unit_pct, { it.unhealthyBelowPct }, { p, v -> p.copy(unhealthyBelowPct = v) }),
            NumSetting(R.string.mode_health_window, R.string.mode_health_window_hint, L.HEALTH_WINDOW_MIN, R.string.mode_unit_min, { it.healthWindowMin }, { p, v -> p.copy(healthWindowMin = v) }),
        ),
    ),
    SettingGroup(
        R.string.mode_group_serial,
        R.string.mode_group_serial_hint,
        listOf(
            NumSetting(R.string.mode_fix_rounds, R.string.mode_fix_rounds_hint, L.FIX_ROUNDS, null, { it.serialMaxFixRounds }, { p, v -> p.copy(serialMaxFixRounds = v) }),
            NumSetting(R.string.mode_warn_rounds, R.string.mode_warn_rounds_hint, L.WARN_ROUNDS, null, { it.serialMaxWarnRounds }, { p, v -> p.copy(serialMaxWarnRounds = v) }),
            NumSetting(R.string.mode_continues, R.string.mode_continues_hint, L.CONTINUES, null, { it.serialMaxContinues }, { p, v -> p.copy(serialMaxContinues = v) }),
            NumSetting(R.string.mode_unmarked, R.string.mode_unmarked_hint, L.UNMARKED_ASKS, null, { it.serialMaxUnmarkedAsks }, { p, v -> p.copy(serialMaxUnmarkedAsks = v) }),
            NumSetting(R.string.mode_git_failures, R.string.mode_git_failures_hint, L.GIT_FAILURES, null, { it.serialMaxGitFailures }, { p, v -> p.copy(serialMaxGitFailures = v) }),
            NumSetting(R.string.mode_unauthorized, R.string.mode_unauthorized_hint, L.UNAUTHORIZED, null, { it.serialMaxUnauthorized }, { p, v -> p.copy(serialMaxUnauthorized = v) }),
            NumSetting(R.string.mode_dom_failures, R.string.mode_dom_failures_hint, L.DOM_FAILURES, null, { it.serialMaxDomFailures }, { p, v -> p.copy(serialMaxDomFailures = v) }),
        ),
    ),
)

/** Mod Merkezi: hazır modları tek dokunuşla seç, kendi modunu kopyala/düzenle, dışa/içe aktar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModesScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val k = KriptonTheme.colors
    val state by container.modeStore.state.collectAsStateWithLifecycle(initialValue = ModeState(emptyList(), ModeProfile.ID_BALANCED))
    var editing by remember { mutableStateOf<ModeProfile?>(null) }
    var confirmDelete by remember { mutableStateOf<ModeProfile?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val toast: (Int) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    editing?.let { draft ->
        ModeEditor(
            initial = draft,
            isNew = state.custom.none { it.id == draft.id },
            onCancel = { editing = null },
            onSave = { saved ->
                scope.launch {
                    if (container.modeStore.save(saved) != null) {
                        editing = null
                        toast(R.string.mode_saved)
                    } else {
                        toast(R.string.mode_save_failed)
                    }
                }
            },
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.modes_title), style = MaterialTheme.typography.headlineMedium)
        }
        Text(stringResource(R.string.modes_intro), style = MaterialTheme.typography.bodyMedium, color = k.muted)

        state.all.forEach { mode ->
            val isActive = mode.id == state.active.id
            NodeCard(modifier = Modifier.fillMaxWidth(), accent = if (isActive) NodeAccent.Green else NodeAccent.Blue) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        mode.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isActive) {
                        KBadge(text = stringResource(R.string.mode_active), accent = NodeAccent.Green)
                    } else {
                        KBadge(text = stringResource(if (mode.builtIn) R.string.mode_builtin else R.string.mode_custom))
                    }
                }
                if (mode.description.isNotBlank()) {
                    Text(mode.description, style = MaterialTheme.typography.bodySmall, color = k.muted, modifier = Modifier.padding(top = 4.dp))
                }
                Text(
                    summaryLine(mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
                FlowRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!isActive) {
                        KButton(text = stringResource(R.string.mode_use), onClick = { scope.launch { container.modeStore.activate(mode.id) } })
                    }
                    if (mode.builtIn) {
                        KButton(
                            text = stringResource(R.string.mode_copy_edit),
                            style = KButtonStyle.Outline,
                            onClick = { editing = mode.copy(id = ModeProfile.newId(), name = copyName(context, mode.name), builtIn = false) },
                        )
                    } else {
                        KButton(text = stringResource(R.string.mode_edit), style = KButtonStyle.Outline, onClick = { editing = mode })
                        KButton(
                            text = stringResource(R.string.mode_duplicate),
                            style = KButtonStyle.Outline,
                            onClick = { scope.launch { container.modeStore.duplicate(mode, copyName(context, mode.name)) } },
                        )
                    }
                    KButton(
                        text = stringResource(R.string.mode_export),
                        style = KButtonStyle.Outline,
                        onClick = { shareText(context, ModeCodec.export(listOf(mode))) },
                    )
                    if (!mode.builtIn) {
                        KButton(text = stringResource(R.string.action_delete), style = KButtonStyle.Danger, onClick = { confirmDelete = mode })
                    }
                }
            }
        }

        NodeCard(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.mode_tools), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KButton(
                    text = stringResource(R.string.mode_new),
                    onClick = { editing = state.active.copy(id = ModeProfile.newId(), name = copyName(context, state.active.name), builtIn = false) },
                )
                KButton(
                    text = stringResource(R.string.mode_import),
                    style = KButtonStyle.Outline,
                    onClick = {
                        val text = readClipboard(context)
                        scope.launch {
                            val added = if (text.isNullOrBlank()) null else container.modeStore.importText(text)
                            Toast.makeText(
                                context,
                                if (added == null || added == 0) context.getString(R.string.mode_import_failed) else context.getString(R.string.mode_import_ok, added),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
                if (state.custom.isNotEmpty()) {
                    KButton(
                        text = stringResource(R.string.mode_export_all),
                        style = KButtonStyle.Outline,
                        onClick = { shareText(context, ModeCodec.export(state.custom)) },
                    )
                }
                KButton(text = stringResource(R.string.mode_reset), style = KButtonStyle.Danger, onClick = { confirmReset = true })
            }
            Text(stringResource(R.string.mode_import_hint), style = MaterialTheme.typography.bodySmall, color = k.muted, modifier = Modifier.padding(top = 8.dp))
        }
    }

    confirmDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.mode_delete_title)) },
            text = { Text(stringResource(R.string.mode_delete_text, m.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch { container.modeStore.delete(m.id) }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.mode_reset_title)) },
            text = { Text(stringResource(R.string.mode_reset_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch { container.modeStore.resetAll() }
                }) { Text(stringResource(R.string.mode_reset)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Mod düzenleyici: tüm ayarlar kaydırıcı/anahtarla; her değişiklik anında aralığa çekilir. */
@Composable
private fun ModeEditor(initial: ModeProfile, isNew: Boolean, onCancel: () -> Unit, onSave: (ModeProfile) -> Unit) {
    val k = KriptonTheme.colors
    var draft by remember(initial.id) { mutableStateOf(initial) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                stringResource(if (isNew) R.string.mode_editor_new else R.string.mode_editor_edit),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        NodeCard(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it.take(L.NAME_MAX)) },
                label = { Text(stringResource(R.string.mode_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.description,
                onValueChange = { draft = draft.copy(description = it.take(L.DESC_MAX)) },
                label = { Text(stringResource(R.string.mode_description)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        GROUPS.forEach { group ->
            NodeCard(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(group.title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(group.hint), style = MaterialTheme.typography.bodySmall, color = k.muted, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                group.items.forEach { s ->
                    NumberRow(s, draft.let(s.get)) { v -> draft = s.set(draft, v.coerceIn(s.range)) }
                }
            }
        }

        NodeCard(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.mode_group_auto), style = MaterialTheme.typography.titleMedium)
            ToggleRow(R.string.mode_wip, R.string.mode_wip_hint, draft.wipCheckpoint) { draft = draft.copy(wipCheckpoint = it) }
            ToggleRow(R.string.mode_tests, R.string.mode_tests_hint, draft.synthesizeTests) { draft = draft.copy(synthesizeTests = it) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            KButton(text = stringResource(R.string.action_save), onClick = { onSave(draft.copy(builtIn = false).coerced()) }, modifier = Modifier.weight(1f))
            KButton(text = stringResource(R.string.action_cancel), style = KButtonStyle.Outline, onClick = onCancel, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun NumberRow(s: NumSetting, value: Int, onChange: (Int) -> Unit) {
    val k = KriptonTheme.colors
    val unit = s.unit?.let { stringResource(it) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(s.title), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(if (unit == null) "$value" else "$value $unit", style = MaterialTheme.typography.titleSmall)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = s.range.first.toFloat()..s.range.last.toFloat(),
        )
        Text(stringResource(s.hint), style = MaterialTheme.typography.bodySmall, color = k.muted)
    }
}

@Composable
private fun ToggleRow(@StringRes title: Int, @StringRes hint: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    val k = KriptonTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = k.muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Tek dokunuşla mod seçici çipleri: Ana ekranda ve çalıştırma penceresinde kullanılır.
 * Seçim anında etkin moda yazılır; çalışan işler bir sonraki adımda yeni değerleri okur.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModePickerRow(modifier: Modifier = Modifier) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val state by container.modeStore.state.collectAsStateWithLifecycle(initialValue = ModeState(emptyList(), ModeProfile.ID_BALANCED))
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.all.forEach { m ->
            KChip(
                label = m.name,
                selected = m.id == state.active.id,
                onClick = { scope.launch { container.modeStore.activate(m.id) } },
            )
        }
    }
}

/** Ana ekran kartı: etkin mod + tek dokunuşla değiştirme + tüm modlara geçiş. */
@Composable
fun ModeHomeCard(onOpenModes: () -> Unit, modifier: Modifier = Modifier) {
    val container = rememberContainer()
    val state by container.modeStore.state.collectAsStateWithLifecycle(initialValue = ModeState(emptyList(), ModeProfile.ID_BALANCED))
    val k = KriptonTheme.colors
    NodeCard(modifier = modifier.fillMaxWidth(), onClick = onOpenModes) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.modes_home_title), style = MaterialTheme.typography.titleSmall)
                Text(state.active.name, style = MaterialTheme.typography.bodyMedium)
                Text(summaryLine(state.active), style = MaterialTheme.typography.bodySmall, color = k.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            KBadge(text = stringResource(R.string.mode_active), accent = NodeAccent.Green)
        }
        ModePickerRow(modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun summaryLine(m: ModeProfile): String = stringResource(
    R.string.mode_summary,
    m.maxParallel,
    m.maxAttempts,
    m.serialMaxFixRounds,
)

private fun copyName(context: Context, base: String): String = context.getString(R.string.mode_copy_name, base).take(ModeProfile.Limits.NAME_MAX)

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    // Panoya da koy: paylaşım uygulaması yoksa ya da "içe aktar" için tek hamlede hazır olsun.
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("kripton-modes", text))
    }
}

private fun readClipboard(context: Context): String? = runCatching {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}.getOrNull()
