package com.kriptoncod.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kriptoncod.app.KriptonCodApp
import com.kriptoncod.app.R
import com.kriptoncod.app.data.run.PoolStatus
import com.kriptoncod.app.data.run.UsageFormat
import com.kriptoncod.app.data.run.UsageReport
import com.kriptoncod.app.ui.components.KChip
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme
import com.kriptoncod.app.ui.usage.UsageViewModel

/** Maliyet ve kota panosu: hesap başına token, istek, tahmini maliyet ve günlük kota doluluğu. */
@Composable
fun UsageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as KriptonCodApp).container }
    val viewModel: UsageViewModel = viewModel(
        factory = remember(container) {
            viewModelFactory { initializer { UsageViewModel(container.aiAccountRepository, container.usageRepository) } }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val k = KriptonTheme.colors

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.usage_title), style = MaterialTheme.typography.titleLarge)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                UsageReport.Window.DAY to R.string.usage_window_day,
                UsageReport.Window.WEEK to R.string.usage_window_week,
                UsageReport.Window.MONTH to R.string.usage_window_month,
            ).forEach { (w, label) ->
                KChip(
                    label = stringResource(label),
                    selected = state.window == w,
                    onClick = { viewModel.selectWindow(w) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val report = state.report
        if (report == null) {
            Text(stringResource(R.string.usage_loading), style = MaterialTheme.typography.bodyMedium, color = k.muted)
            return@Column
        }

        SummaryCard(report)
        ChartCard(report)
        if (report.rows.isEmpty() || report.requests == 0) {
            Text(stringResource(R.string.usage_empty), style = MaterialTheme.typography.bodyMedium, color = k.muted)
        }
        report.rows.forEach { row -> AccountRowCard(row, state.pool[row.accountId]) }
        if (report.estimatedRequests > 0) {
            Text(stringResource(R.string.usage_estimated_note), style = MaterialTheme.typography.bodySmall, color = k.muted)
        }
    }
}

@Composable
private fun SummaryCard(report: UsageReport.Report) {
    val k = KriptonTheme.colors
    NodeCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val approx = if (report.estimatedRequests > 0) "≈ " else ""
            Text(
                stringResource(R.string.usage_total_tokens, approx + UsageFormat.tokens(report.totalTokens)),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(
                    R.string.usage_in_out,
                    UsageFormat.tokens(report.promptTokens),
                    UsageFormat.tokens(report.completionTokens),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(stringResource(R.string.usage_requests, report.requests), style = MaterialTheme.typography.bodySmall, color = k.muted)
            report.cost?.let {
                Text(stringResource(R.string.usage_cost, UsageFormat.cost(it)), style = MaterialTheme.typography.bodyMedium)
            }
            if (report.unpricedAccounts > 0) {
                Text(
                    stringResource(R.string.usage_cost_partial, report.unpricedAccounts),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )
            }
        }
    }
}

/** Saatlik (son 24 saat) ya da günlük token çubukları; en yüksek dilim tam boy kabul edilir. */
@Composable
private fun ChartCard(report: UsageReport.Report) {
    val k = KriptonTheme.colors
    val max = remember(report) { report.buckets.maxOfOrNull { it.tokens } ?: 0L }
    NodeCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (report.hourly) R.string.usage_chart_hourly else R.string.usage_chart_daily),
                style = MaterialTheme.typography.titleSmall,
            )
            if (max == 0L) {
                Text(stringResource(R.string.usage_chart_empty), style = MaterialTheme.typography.bodySmall, color = k.muted)
            } else {
                Row(Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.Bottom) {
                    report.buckets.forEach { b ->
                        val fraction = if (b.tokens == 0L) 0f else (b.tokens.toFloat() / max).coerceIn(0.03f, 1f)
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier
                                    .padding(horizontal = 1.dp)
                                    .fillMaxWidth()
                                    .fillMaxHeight(fraction)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.usage_chart_peak, UsageFormat.tokens(max)),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )
            }
        }
    }
}

@Composable
private fun AccountRowCard(row: UsageReport.AccountRow, pool: PoolStatus.Entry?) {
    val k = KriptonTheme.colors
    val context = LocalContext.current
    val timeFormat = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    fun at(ms: Long): String = timeFormat.format(java.util.Date(ms))

    NodeCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                row.account?.displayName ?: stringResource(R.string.usage_deleted_account),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.account?.modelId?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = k.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // Kota: yalnız günlük sınırı olan hesaplarda doluluk çubuğu.
            if (pool?.dailyLimit != null) {
                val limit = pool.dailyLimit
                LinearProgressIndicator(
                    progress = { (pool.used24h.toFloat() / limit).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                Text(
                    stringResource(R.string.usage_quota, pool.used24h, limit),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )
            }
            val resting: String? = when (pool?.state) {
                PoolStatus.State.RESTING_PROVIDER -> stringResource(R.string.pool_resting_provider, at(pool?.untilMs ?: 0L))
                PoolStatus.State.RESTING_QUOTA -> stringResource(R.string.pool_resting_quota, at(pool?.untilMs ?: 0L))
                PoolStatus.State.RESTING_BREAKER -> stringResource(R.string.pool_resting_breaker, at(pool?.untilMs ?: 0L))
                PoolStatus.State.ERROR -> stringResource(R.string.pool_state_error)
                else -> null
            }
            resting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = if (pool?.state == PoolStatus.State.ERROR) k.danger else k.muted)
            }

            val approx = if (row.estimatedRequests > 0) "≈ " else ""
            Text(
                stringResource(
                    R.string.usage_row_tokens,
                    approx + UsageFormat.tokens(row.totalTokens),
                    UsageFormat.tokens(row.promptTokens),
                    UsageFormat.tokens(row.completionTokens),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.usage_row_requests, row.requests, row.successes, row.rateLimited),
                style = MaterialTheme.typography.bodySmall,
                color = k.muted,
            )
            row.cost?.let {
                Text(stringResource(R.string.usage_cost, UsageFormat.cost(it)), style = MaterialTheme.typography.bodySmall, color = k.muted)
            }
            if (row.unknownRequests > 0 && row.requests > 0) {
                Text(
                    stringResource(R.string.usage_row_unknown, row.unknownRequests),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )
            }
        }
    }
}
