package com.kriptoncod.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.KriptonCodApp
import com.kriptoncod.app.data.db.AccountUsage
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.adaptive.Adaptive
import com.kriptoncod.app.ui.adaptive.LocalWindowClass
import com.kriptoncod.app.ui.adaptive.WindowClass
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.KriptonCodLogo
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.navigation.Route
import com.kriptoncod.app.ui.theme.KriptonTheme
import com.kriptoncod.app.ui.theme.LocalAppBusy
import com.kriptoncod.app.ui.theme.isInProgress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ana ekran: logo + özet sayaçlar, sıradaki adımı gösteren hızlı başlangıç, son çalıştırmalar ve
 * "nasıl çalışır" kartı. Telefonda tek sütun, tablette iki sütun (boş alan kalmaz).
 */
@Composable
fun HomeScreen(
    onAddAi: () -> Unit,
    onOpenRoute: (Route) -> Unit,
    onOpenRun: (Long) -> Unit,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as KriptonCodApp).container }
    val accounts by container.aiAccountRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val sets by container.promptRepository.observeSets().collectAsStateWithLifecycle(initialValue = emptyList())
    val runs by container.runRepository.observeRuns().collectAsStateWithLifecycle(initialValue = emptyList())
    // Son 24 saatin hesap başına kullanımı; akış bir kez kurulur (pencere o an sabitlenir).
    val usageFlow = remember(container) { container.usageRepository.observeSummary() }
    val usage by usageFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val recent = remember(runs) { runs.sortedByDescending { it.createdAt }.take(3) }
    val activeRun = remember(runs) { runs.filter { it.status.isInProgress() }.maxByOrNull { it.createdAt } }

    // Sıradaki adım: hesap -> prompt seti -> ilk çalıştırma.
    val nextAction: Pair<Int, () -> Unit>? = when {
        accounts.isEmpty() -> R.string.home_action_add_ai to onAddAi
        sets.isEmpty() -> R.string.home_action_add_prompts to { onOpenRoute(Route.Prompts) }
        runs.isEmpty() -> R.string.home_action_first_run to { onOpenRoute(Route.Prompts) }
        else -> null
    }

    val wide = LocalWindowClass.current != WindowClass.Compact
    val pad = Adaptive.pagePadding(LocalWindowClass.current)
    val k = KriptonTheme.colors

    val hero: @Composable () -> Unit = {
        KriptonCodLogo(logoSize = if (wide) 160.dp else 120.dp, busy = LocalAppBusy.current)
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = k.muted,
            textAlign = TextAlign.Center,
        )
    }
    val banner: @Composable () -> Unit = {
        activeRun?.let { r ->
            NodeCard(modifier = Modifier.fillMaxWidth(), accent = NodeAccent.Green, onClick = { onOpenRun(r.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_active_run), style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${stringResource(R.string.run_title)} #${r.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = k.muted,
                        )
                    }
                    HistoryStatusBadge(status = r.status)
                }
            }
        }
    }
    val stats: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(accounts.size, stringResource(R.string.home_stat_ais), { onOpenRoute(Route.Ais) }, Modifier.weight(1f))
            StatCard(sets.size, stringResource(R.string.home_stat_sets), { onOpenRoute(Route.Prompts) }, Modifier.weight(1f))
            StatCard(runs.size, stringResource(R.string.home_stat_runs), { onOpenRoute(Route.History) }, Modifier.weight(1f))
        }
    }
    val usageCard: @Composable () -> Unit = {
        if (accounts.isNotEmpty()) UsageCard(accounts, usage)
    }
    val guide: @Composable () -> Unit = {
        if (nextAction != null) {
            NodeCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.home_quick_title), style = MaterialTheme.typography.titleMedium)
                    StepRow(1, stringResource(R.string.home_step1_title), stringResource(R.string.home_step1_hint), accounts.isNotEmpty())
                    StepRow(2, stringResource(R.string.home_step2_title), stringResource(R.string.home_step2_hint), sets.isNotEmpty())
                    StepRow(3, stringResource(R.string.home_step3_title), stringResource(R.string.home_step3_hint), runs.isNotEmpty())
                    KButton(
                        text = stringResource(nextAction.first),
                        onClick = nextAction.second,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (recent.isNotEmpty()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.home_recent_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    KButton(
                        text = stringResource(R.string.home_recent_all),
                        onClick = { onOpenRoute(Route.History) },
                        style = KButtonStyle.Outline,
                    )
                }
                recent.forEach { r ->
                    NodeCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenRun(r.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("${stringResource(R.string.run_title)} #${r.id}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(r.createdAt)) +
                                        (r.repo?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = k.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            HistoryStatusBadge(status = r.status)
                        }
                    }
                }
            }
        }
        NodeCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.home_how_title), style = MaterialTheme.typography.titleMedium)
                StepRow(1, stringResource(R.string.home_how1_title), stringResource(R.string.home_how1_hint), false)
                StepRow(2, stringResource(R.string.home_how2_title), stringResource(R.string.home_how2_hint), false)
                StepRow(3, stringResource(R.string.home_how3_title), stringResource(R.string.home_how3_hint), false)
                StepRow(4, stringResource(R.string.home_how4_title), stringResource(R.string.home_how4_hint), false)
            }
        }
    }

    if (wide) {
        // Tablet: solda logo + sayaçlar, sağda yönlendirme kartları; iki sütun ekranı dengeli doldurur.
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Row(
                modifier = Modifier.widthIn(max = 1200.dp).fillMaxWidth().padding(pad),
                horizontalArrangement = Arrangement.spacedBy(pad),
            ) {
                Column(
                    Modifier.weight(1f).padding(top = pad),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    hero()
                    banner()
                    stats()
                    usageCard()
                }
                Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { guide() }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            hero()
            banner()
            stats()
            usageCard()
            guide()
        }
    }
}

/** Hesap başına son 24 saat: istek sayısı, hata oranı, kota sınırı sayısı ve anlık bekleme durumu. */
@Composable
private fun UsageCard(accounts: List<AiAccount>, usage: List<AccountUsage>) {
    val k = KriptonTheme.colors
    val byId = remember(usage) { usage.associateBy { it.accountId } }
    val rows = remember(accounts, byId) { accounts.sortedByDescending { byId[it.id]?.requests ?: 0 }.take(5) }
    NodeCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(stringResource(R.string.home_usage_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.home_usage_subtitle), style = MaterialTheme.typography.bodySmall, color = k.muted)
            }
            if (usage.isEmpty()) {
                Text(stringResource(R.string.home_usage_empty), style = MaterialTheme.typography.bodySmall, color = k.muted)
            }
            rows.forEach { account ->
                val u = byId[account.id]
                val now = System.currentTimeMillis()
                val waitMin = account.waitUntil?.takeIf { account.status == AccountStatus.WAITING && it > now }
                    ?.let { ((it - now) / 60_000L + 1).toInt() }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            account.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (u == null || u.requests == 0) {
                                stringResource(R.string.home_usage_idle)
                            } else {
                                stringResource(
                                    R.string.home_usage_line,
                                    u.requests,
                                    (u.errors + u.rateLimited) * 100 / u.requests,
                                    u.rateLimited,
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = k.muted,
                        )
                    }
                    when {
                        waitMin != null -> Text(
                            stringResource(R.string.home_usage_waiting, waitMin),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        account.status == AccountStatus.ERROR -> Text(
                            stringResource(R.string.home_usage_error),
                            style = MaterialTheme.typography.labelMedium,
                            color = k.danger,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    NodeCard(modifier = modifier, onClick = onClick) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = KriptonTheme.colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Numaralı (veya tamamlanmışsa tik işaretli) adım satırı. */
@Composable
private fun StepRow(index: Int, title: String, hint: String, done: Boolean) {
    val k = KriptonTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (done) k.arrowFill else Color.Transparent)
                .border(1.5.dp, if (done) k.arrow else k.nodeBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = k.arrow, modifier = Modifier.size(16.dp))
            } else {
                Text(index.toString(), style = MaterialTheme.typography.labelLarge, color = k.muted)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = k.muted)
        }
    }
}
