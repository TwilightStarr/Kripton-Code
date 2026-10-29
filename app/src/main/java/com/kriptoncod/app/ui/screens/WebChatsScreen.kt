package com.kriptoncod.app.ui.screens

import android.content.Context
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.R
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.web.WebChatHub
import com.kriptoncod.app.data.web.WebMonitorHandle
import com.kriptoncod.app.data.web.WebProvider
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.ui.components.EmptyState
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.KriptonLoader
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme

/** Elle gezilebilen web sağlayıcıları (Gemini API anahtarıyla çalışır, web sayfası yok). */
private fun AiAccount.webProvider(): WebProvider? =
    if (authType != AuthType.WEB_SESSION) null else WebProvider.fromId(providerId)?.takeIf { it != WebProvider.GEMINI }

private fun WebProvider.tint(): Color = when (this) {
    WebProvider.CLAUDE -> Color(0xFFD97757)
    WebProvider.CHATGPT -> Color(0xFF10A37F)
    WebProvider.KIMI -> Color(0xFF4B7BEC)
    WebProvider.GEMINI -> Color(0xFF8E6CEF)
}

/** Yuvarlak, sağlayıcı renkli baş harf simgesi (sohbet listesi avatarı). */
@Composable
private fun Avatar(provider: WebProvider, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(provider.tint()), contentAlignment = Alignment.Center) {
        Text(
            provider.displayName.take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

/**
 * "AI sohbetleri": WhatsApp benzeri liste. Her web hesabı bir satır; dokununca o AI'ın web arayüzü açılır.
 */
@Composable
fun WebChatsScreen(container: AppContainer, onBack: () -> Unit, onOpenChat: (Long) -> Unit) {
    val accounts by container.aiAccountRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val entries by container.webMonitor.entries.collectAsStateWithLifecycle()
    val k = KriptonTheme.colors

    val rows = remember(accounts, entries) {
        accounts.filter { it.webProvider() != null }
            .sortedWith(
                compareByDescending<AiAccount> { a -> entries.any { it.accountId == a.id && it.isLive } }
                    .thenBy { it.displayName.lowercase() },
            )
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.web_chats_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        if (rows.isEmpty()) {
            Box(Modifier.padding(16.dp)) {
                EmptyState(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.web_chats_empty_title),
                    message = stringResource(R.string.web_chats_empty_msg),
                )
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.id }) { a ->
                val provider = a.webProvider() ?: return@items
                val live = entries.any { it.accountId == a.id && it.isLive }
                val (sub, subColor) = when {
                    live -> stringResource(R.string.web_chats_status_open) to k.arrow
                    a.status == AccountStatus.ERROR -> stringResource(R.string.web_chats_status_error) to k.danger
                    a.status == AccountStatus.WAITING -> stringResource(R.string.web_chats_status_waiting) to k.muted
                    else -> stringResource(R.string.web_chats_status_tap) to k.muted
                }
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenChat(a.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Avatar(provider, 52.dp)
                    Column(Modifier.weight(1f)) {
                        Text(a.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            provider.displayName + " · " + sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = subColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (live) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(k.arrow))
                    }
                }
                HorizontalDivider(Modifier.padding(start = 82.dp), color = k.nodeBorder.copy(alpha = 0.5f))
            }
        }
    }
}

/**
 * Seçilen AI'ın web arayüzü: ekranı dolduran, dokunulabilir sayfa. Ekrandan çıkınca oturum kapanır ve (giriş yapıldıysa)
 * çerezler kaydedilir; böylece düşmüş bir oturum burada giriş yapılarak onarılır.
 */
@Composable
fun WebChatScreen(accountId: Long, container: AppContainer, onBack: () -> Unit, onOpenMonitor: () -> Unit) {
    val hub = container.webChatHub
    var account by remember { mutableStateOf<AiAccount?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(accountId) {
        account = container.aiAccountRepository.get(accountId)
        loaded = true
    }
    val state by hub.state(accountId).collectAsStateWithLifecycle()
    val entries by container.webMonitor.entries.collectAsStateWithLifecycle()
    val acc = account
    val provider = acc?.webProvider()
    val k = KriptonTheme.colors

    LaunchedEffect(acc?.id) {
        if (acc != null && provider != null) hub.open(acc)
    }
    DisposableEffect(accountId) { onDispose { hub.release(accountId) } }
    BackHandler { onBack() }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            if (provider != null) {
                Avatar(provider, 38.dp)
                Box(Modifier.size(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(acc?.displayName.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val live = state is WebChatHub.State.Ready
                Text(
                    stringResource(
                        when (state) {
                            is WebChatHub.State.Ready -> R.string.web_chats_header_open
                            WebChatHub.State.Opening -> R.string.web_chats_header_opening
                            else -> R.string.web_chats_header_idle
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (live) k.arrow else k.muted,
                )
            }
            if (acc != null && provider != null && state !is WebChatHub.State.Opening) {
                androidx.compose.material3.TextButton(onClick = { hub.reopen(acc) }) { Text(stringResource(R.string.web_chats_reload)) }
            }
        }
        HorizontalDivider(color = k.nodeBorder.copy(alpha = 0.5f))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val s = state
            when {
                loaded && (acc == null || provider == null) -> Notice(stringResource(R.string.web_chats_no_account), null, null)

                s is WebChatHub.State.Failed -> Notice(
                    stringResource(R.string.web_chats_failed),
                    stringResource(R.string.web_chats_retry),
                ) { if (acc != null) hub.reopen(acc) }

                s is WebChatHub.State.Ready -> {
                    val entry = entries.firstOrNull { it.id == s.entryId && it.isLive }
                    when {
                        entry == null -> Notice(
                            stringResource(R.string.web_chats_closed),
                            stringResource(R.string.web_chats_retry),
                        ) { if (acc != null) hub.reopen(acc) }

                        s.borrowed -> Notice(
                            stringResource(R.string.web_chats_busy),
                            stringResource(R.string.web_chats_open_monitor),
                            onOpenMonitor,
                        )

                        else -> ChatPage(container.webMonitor.handle(entry.id))
                    }
                }

                else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    KriptonLoader()
                    Text(stringResource(R.string.web_chats_opening), style = MaterialTheme.typography.bodyMedium, color = k.muted, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun Notice(message: String, actionText: String?, onAction: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        NodeCard(modifier = Modifier.fillMaxWidth(), accent = NodeAccent.Blue) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = KriptonTheme.colors.muted)
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                }
                if (actionText != null && onAction != null) {
                    KButton(actionText, onAction, style = KButtonStyle.Outline)
                }
            }
        }
    }
}

/** WebView'ı ekranı dolduracak boyutta barındırır; uygulama arka plana geçince sayfayı geri alır, dönünce yeniden koyar. */
@Composable
private fun ChatPage(handle: WebMonitorHandle?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val host = remember { ChatHost(context) }
    val currentHandle by rememberUpdatedState(handle)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { host.show(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { host.show(currentHandle) }
    DisposableEffect(host) { onDispose { host.show(null) } }
    AndroidView(
        factory = { host },
        modifier = Modifier.fillMaxSize(),
        update = { it.show(handle) },
    )
}

private class ChatHost(context: Context) : FrameLayout(context) {
    private var mounted: WebMonitorHandle? = null

    fun show(handle: WebMonitorHandle?) {
        if (handle === mounted) return
        mounted?.unmount(this)
        mounted = handle
        if (handle == null) removeAllViews() else handle.mountResponsive(this)
    }
}
