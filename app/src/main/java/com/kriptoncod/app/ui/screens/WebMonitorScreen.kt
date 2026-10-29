package com.kriptoncod.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.R
import com.kriptoncod.app.data.web.WebMonitorEntry
import com.kriptoncod.app.data.web.WebMonitorHandle
import com.kriptoncod.app.data.web.WebPhase
import com.kriptoncod.app.data.web.WebReply
import com.kriptoncod.app.data.web.WebTurn
import com.kriptoncod.app.ui.components.EmptyState
import com.kriptoncod.app.ui.components.KBadge
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.KChip
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** WebView'ın sabit düzen boyutu (WebViewChatDriver ile aynı olmalı). */
private const val PAGE_W = 1280f
private const val PAGE_H = 2000f

/**
 * "Canlı web": akış ya da seri modda açık olan web sohbetlerinin sayfasını canlı gösterir ve her oturumun
 * son prompt/yanıtını listeler. Kullanıcı istediği an girip promptun sayfaya girdiğini, AI'ın çıktı verdiğini
 * ya da takıldığını görebilir. Sayfa çalışmayı etkilemesin diye varsayılan olarak yalnız izlenir (dokunma kapalı).
 */
@Composable
fun WebMonitorScreen(container: AppContainer, onBack: () -> Unit) {
    val entries by container.webMonitor.entries.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val selected = entries.firstOrNull { it.id == selectedId } ?: entries.firstOrNull()

    // Göreli süreler (ör. "12 sn önce") her saniye tazelenir; yalnız bu ekran açıkken.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }

    // Tam ekran: yalnız canlı sayfa gösterilir; üstte küçük bir çubuk (kapat · yakınlaştırma · dokunma) kalır.
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val showFull = fullscreen && selected != null && selected.isLive
    // Oturum kapanınca ya da seçili oturum kalmayınca tam ekrandan çık.
    LaunchedEffect(selected?.id, selected?.isLive) {
        if (selected == null || !selected.isLive) fullscreen = false
    }
    BackHandler(enabled = showFull) { fullscreen = false }
    ImmersiveMode(enabled = showFull)

    val pad = if (showFull) Modifier.padding(0.dp) else Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    Column(Modifier.fillMaxSize().then(pad), verticalArrangement = Arrangement.spacedBy(if (showFull) 0.dp else 8.dp)) {
        if (!showFull) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(stringResource(R.string.web_monitor_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                val liveCount = entries.count { it.isLive }
                if (liveCount > 0) KBadge(text = stringResource(R.string.web_monitor_live_count, liveCount), accent = NodeAccent.Green)
            }
        }

        if (selected == null) {
            EmptyState(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.web_monitor_empty_title),
                message = stringResource(R.string.web_monitor_empty_msg),
            )
            return@Column
        }

        if (!showFull) {
            // Oturum seçici
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.forEach { e ->
                    KChip(
                        label = e.provider.displayName + " · " + e.accountName.take(18) + if (e.isLive) "" else " ✕",
                        selected = e.id == selected.id,
                        onClick = { selectedId = e.id },
                    )
                }
            }
            if (entries.any { !it.isLive }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    KButton(
                        text = stringResource(R.string.web_monitor_clear),
                        onClick = { container.webMonitor.clearClosed() },
                        style = KButtonStyle.Outline,
                    )
                }
            }

            StatusCard(selected, nowMs)

            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.web_monitor_tab_page)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.web_monitor_tab_turns)) })
            }
        }

        // Bu kutu iki kipte de aynı yerde durur; böylece sayfa (ve WebView) tam ekrana geçerken yeniden kurulmaz.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0 || showFull) {
                if (selected.isLive) {
                    LivePage(
                        handle = container.webMonitor.handle(selected.id),
                        fullscreen = showFull,
                        onFullscreenChange = { fullscreen = it },
                    )
                } else {
                    Text(stringResource(R.string.web_monitor_page_gone), style = MaterialTheme.typography.bodyMedium, color = KriptonTheme.colors.muted)
                }
            } else {
                TurnList(selected, nowMs)
            }
        }
    }
}

/** Run ve seri mod ekranlarındaki "Canlı web" düğmesi; açık oturum sayısını gösterir. */
@Composable
fun WebMonitorButton(container: AppContainer, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val entries by container.webMonitor.entries.collectAsStateWithLifecycle()
    val live = entries.count { it.isLive }
    KButton(
        text = if (live > 0) stringResource(R.string.web_monitor_button_count, live) else stringResource(R.string.web_monitor_button),
        onClick = onClick,
        style = KButtonStyle.Outline,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------------------------------------------
// Durum kartı

@Composable
private fun StatusCard(e: WebMonitorEntry, nowMs: Long) {
    val k = KriptonTheme.colors
    val stalled = e.looksStalled(nowMs)
    val accent = when {
        e.phase == WebPhase.PROBLEM || stalled -> NodeAccent.Red
        e.phase == WebPhase.GENERATING || e.phase == WebPhase.REPLIED -> NodeAccent.Green
        else -> NodeAccent.Blue
    }
    NodeCard(modifier = Modifier.fillMaxWidth(), accent = accent) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    e.provider.displayName + " · " + e.accountName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                KBadge(text = phaseText(e.phase), accent = accent)
            }
            Text(
                stringResource(R.string.web_monitor_opened, durationText(nowMs - e.openedAtMs)),
                style = MaterialTheme.typography.bodySmall,
                color = k.muted,
            )
            val last = e.lastTurn
            if (e.needsLogin || last?.status?.startsWith(WebReply.Status.NEEDS_LOGIN.name) == true) {
                Text(stringResource(R.string.web_monitor_login_page), style = MaterialTheme.typography.bodySmall, color = k.danger)
            }
            if (last == null) {
                Text(stringResource(R.string.web_monitor_no_prompt), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    if (last.peek) {
                        stringResource(R.string.web_monitor_peek)
                    } else {
                        stringResource(R.string.web_monitor_last_prompt, last.promptChars, durationText(nowMs - last.sentAtMs))
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (e.awaitingReply) {
                    Text(
                        when {
                            e.liveChars == 0 -> stringResource(R.string.web_monitor_output_none)
                            else -> stringResource(R.string.web_monitor_output, e.liveChars)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (stalled) {
                        Text(
                            stringResource(R.string.web_monitor_stalled, durationText(e.idleMs(nowMs))),
                            style = MaterialTheme.typography.bodySmall,
                            color = k.danger,
                        )
                    } else if (e.phase == WebPhase.GENERATING) {
                        Text(stringResource(R.string.web_monitor_growing), style = MaterialTheme.typography.bodySmall, color = k.arrow)
                    }
                }
            }
        }
    }
}

@Composable
private fun phaseText(p: WebPhase): String = stringResource(
    when (p) {
        WebPhase.OPENING -> R.string.web_phase_opening
        WebPhase.READY -> R.string.web_phase_ready
        WebPhase.SENDING -> R.string.web_phase_sending
        WebPhase.GENERATING -> R.string.web_phase_generating
        WebPhase.REPLIED -> R.string.web_phase_replied
        WebPhase.PROBLEM -> R.string.web_phase_problem
        WebPhase.CLOSED -> R.string.web_phase_closed
    },
)

@Composable
private fun durationText(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s < 60) stringResource(R.string.web_monitor_sec, s.toInt()) else stringResource(R.string.web_monitor_min, (s / 60).toInt(), (s % 60).toInt())
}

// ---------------------------------------------------------------------------------------------------------------
// Canlı sayfa

/**
 * Gizli WebView'ı bu ekrana taşır. WebView 1280x2000 sabit düzeninde kalır (otomasyon bozulmasın) ve yalnız ölçeklenerek
 * gösterilir; ekran kapanınca ya da uygulama arka plana geçince WebView yeniden ekran dışına alınır.
 */
@Composable
private fun LivePage(handle: WebMonitorHandle?, fullscreen: Boolean, onFullscreenChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    // AndroidView'ın factory'si bir kez çalışır; bu yüzden kap sabit, gösterilen oturum update'te değişir.
    val host = remember { MonitorHost(context) }
    WebFileChooserEffect()
    var zoom by rememberSaveable { mutableIntStateOf(0) } // 0 sığdır, 1 = 0,5x, 2 = 1x
    var interactive by rememberSaveable { mutableStateOf(false) }
    var started by remember { mutableStateOf(true) }
    var url by remember { mutableStateOf<String?>(null) }

    // Arka plandaki WebView'ın zamanlayıcıları kısılmasın: uygulama görünmezken sayfa ekrandan alınır.
    // Arka planda kare çizimi durduğu için bu iş update'e bırakılmaz; olay geldiği anda doğrudan yapılır.
    val currentHandle by rememberUpdatedState(handle)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        started = false
        host.show(null)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        started = true
        host.show(currentHandle)
    }
    DisposableEffect(host) { onDispose { host.show(null) } }
    LaunchedEffect(handle) {
        while (true) {
            url = handle?.currentUrl()
            delay(2_000)
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (fullscreen) 0.dp else 6.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (fullscreen) {
                // Küçük kapat düğmesi: tam ekrandan çıkar.
                IconButton(onClick = { onFullscreenChange(false) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.web_monitor_fullscreen_close))
                }
            } else {
                KChip(stringResource(R.string.web_monitor_fullscreen), false, { onFullscreenChange(true) })
            }
            KChip(stringResource(R.string.web_monitor_zoom_fit), zoom == 0, { zoom = 0 })
            KChip(stringResource(R.string.web_monitor_zoom_50), zoom == 1, { zoom = 1 })
            KChip(stringResource(R.string.web_monitor_zoom_100), zoom == 2, { zoom = 2 })
            KChip(
                stringResource(if (interactive) R.string.web_monitor_touch_on else R.string.web_monitor_touch_off),
                interactive,
                { interactive = !interactive },
            )
        }
        if (interactive && !fullscreen) {
            Text(stringResource(R.string.web_monitor_touch_warn), style = MaterialTheme.typography.bodySmall, color = KriptonTheme.colors.danger)
        }
        if (!fullscreen) {
            url?.let {
                Text(
                    stringResource(R.string.web_monitor_url, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = KriptonTheme.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val scale = when (zoom) {
                0 -> if (widthPx > 0f) widthPx / PAGE_W else 1f
                1 -> 0.5f
                else -> 1f
            }
            val w = with(density) { (PAGE_W * scale).toDp() }
            val h = with(density) { (PAGE_H * scale).toDp() }
            Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
                AndroidView(
                    factory = { host },
                    modifier = Modifier.size(w, h),
                    update = { v ->
                        v.scaleFactor = scale
                        v.interactive = interactive
                        v.show(if (started) handle else null)
                    },
                )
            }
        }
    }
}

/** Tam ekranda sistem çubuklarını (durum/gezinme) gizler; çıkınca geri getirir. */
@Composable
private fun ImmersiveMode(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = context.findActivity()?.window
        val controller = window?.let { w -> runCatching { WindowCompat.getInsetsController(w, w.decorView) }.getOrNull() }
        if (enabled && controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { if (enabled) controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * WebView'ı tutan kap. Çocuk 1280x2000 sabit boyutludur; kap yalnız ölçek uygular. Dokunma kapalıyken olaylar
 * WebView'a hiç ulaşmaz (üstteki kaydırma çalışır, sayfa etkilenmez).
 */
private class MonitorHost(context: Context) : FrameLayout(context) {
    var interactive: Boolean = false
    var scaleFactor: Float = 1f
        set(value) {
            field = value
            applyScale()
        }
    private var mounted: WebMonitorHandle? = null

    init {
        clipChildren = true
    }

    fun show(handle: WebMonitorHandle?) {
        if (handle !== mounted) {
            mounted?.unmount(this)
            mounted = handle
            if (handle == null) removeAllViews() else handle.mount(this)
        }
        applyScale()
    }

    private fun applyScale() {
        val c = getChildAt(0) ?: return
        c.pivotX = 0f
        c.pivotY = 0f
        c.scaleX = scaleFactor
        c.scaleY = scaleFactor
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = if (interactive) super.dispatchTouchEvent(ev) else false
}

// ---------------------------------------------------------------------------------------------------------------
// Prompt ve yanıt

@Composable
private fun TurnList(e: WebMonitorEntry, nowMs: Long) {
    val k = KriptonTheme.colors
    if (e.turns.isEmpty()) {
        Text(stringResource(R.string.web_monitor_no_prompt), style = MaterialTheme.typography.bodyMedium, color = k.muted)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        items(e.turns.reversed(), key = { it.number }) { t ->
            TurnCard(t, liveTail = if (!t.isDone && e.isLive) e.liveTail else "", liveChars = if (!t.isDone && e.isLive) e.liveChars else 0)
        }
        item {
            Text(stringResource(R.string.web_monitor_privacy), style = MaterialTheme.typography.bodySmall, color = k.muted)
        }
    }
}

@Composable
private fun TurnCard(t: WebTurn, liveTail: String, liveChars: Int) {
    val k = KriptonTheme.colors
    var expanded by rememberSaveable(t.number) { mutableStateOf(false) }
    val time = remember(t.sentAtMs) { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(t.sentAtMs)) }
    val problem = t.isDone && t.status != WebReply.Status.OK.name
    NodeCard(modifier = Modifier.fillMaxWidth(), accent = if (problem) NodeAccent.Red else NodeAccent.Blue) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.web_monitor_turn_title, t.number, time), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (t.isDone) {
                    KBadge(
                        text = if (problem) stringResource(R.string.web_monitor_status_problem, t.status.orEmpty()) else stringResource(R.string.web_monitor_status_ok),
                        accent = if (problem) NodeAccent.Red else NodeAccent.Green,
                    )
                }
            }
            if (t.peek) {
                Text(stringResource(R.string.web_monitor_peek), style = MaterialTheme.typography.bodySmall, color = k.muted)
            } else {
                Text(stringResource(R.string.web_monitor_prompt_label, t.promptChars), style = MaterialTheme.typography.labelMedium, color = k.muted)
                SelectionContainer {
                    Text(
                        t.prompt,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 8,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = !expanded },
                    )
                }
                if (t.promptChars > t.prompt.length) {
                    Text(stringResource(R.string.web_monitor_prompt_cut, t.prompt.length), style = MaterialTheme.typography.bodySmall, color = k.muted)
                }
                Text(
                    stringResource(if (expanded) R.string.web_monitor_show_less else R.string.web_monitor_show_all),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }
            if (t.isDone) {
                Text(stringResource(R.string.web_monitor_reply_label, t.replyChars), style = MaterialTheme.typography.labelMedium, color = k.muted)
                t.replyPreview?.let {
                    SelectionContainer { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 14, overflow = TextOverflow.Ellipsis) }
                }
                if (t.fileCount > 0) Text(stringResource(R.string.web_monitor_reply_files, t.fileCount), style = MaterialTheme.typography.bodySmall, color = k.muted)
            } else {
                Text(stringResource(R.string.web_monitor_reply_waiting), style = MaterialTheme.typography.bodySmall, color = k.muted)
                if (liveChars > 0) {
                    Text(stringResource(R.string.web_monitor_output, liveChars), style = MaterialTheme.typography.labelMedium, color = k.muted)
                    if (liveTail.isNotEmpty()) {
                        Text("…" + liveTail, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
