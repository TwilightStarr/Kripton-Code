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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.kriptoncod.app.data.web.WebMonitorEntry
import com.kriptoncod.app.data.web.WebMonitorHandle
import com.kriptoncod.app.data.web.WebProvider
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.ui.adaptive.LocalWindowClass
import com.kriptoncod.app.ui.adaptive.WindowClass
import com.kriptoncod.app.ui.components.EmptyState
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.KriptonLoader
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme

/** Elle gezilebilen web sağlayıcıları (Gemini API anahtarıyla çalışır, web sayfası yok). */
internal fun AiAccount.webProvider(): WebProvider? = if (authType != AuthType.WEB_SESSION) null else WebProvider.fromId(providerId)?.takeIf { it != WebProvider.GEMINI }

internal fun WebProvider.tint(): Color = when (this) {
    // Tek renkli tema: sağlayıcılar gri-beyaz tonlarla ayrışır.
    WebProvider.CLAUDE -> Color(0xFFF0F0F2)

    WebProvider.CHATGPT -> Color(0xFFBDBDC2)

    WebProvider.KIMI -> Color(0xFF98989E)

    WebProvider.GEMINI -> Color(0xFFD6D6DA)
}

/** Yuvarlak, sağlayıcı renkli baş harf simgesi (sohbet listesi avatarı). */
@Composable
internal fun Avatar(provider: WebProvider, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(provider.tint()), contentAlignment = Alignment.Center) {
        Text(
            provider.displayName.take(1).uppercase(),
            color = Color(0xFF0B0B0C),
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

/**
 * "AI sohbetleri": WhatsApp benzeri liste. Her web hesabı bir satır; dokununca o AI'ın web arayüzü açılır.
 * Telefonda liste ve sohbet ayrı ekrandır; tablette (≥600dp) solda liste, sağda seçilen AI'ın web arayüzü yan yana durur.
 */
@Composable
fun WebChatsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onOpenMonitor: () -> Unit = {},
    /** Tablette önceden seçili sohbet (ör. telefondan tablet düzenine geçince). */
    initialSelected: Long? = null,
    /** Tablette birden çok web arayüzünü aynı anda açan çalışma alanına geçiş; null ise düğme görünmez. */
    onOpenWorkspace: (() -> Unit)? = null,
) {
    val accounts by container.aiAccountRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val entries by container.webMonitor.entries.collectAsStateWithLifecycle()
    val windowClass = LocalWindowClass.current
    val wide = windowClass != WindowClass.Compact
    var selectedId by rememberSaveable { mutableStateOf(initialSelected) }

    val rows = remember(accounts, entries) {
        accounts.filter { it.webProvider() != null }
            .sortedWith(
                compareByDescending<AiAccount> { a -> entries.any { it.accountId == a.id && it.isLive } }
                    .thenBy { it.displayName.lowercase() },
            )
    }
    // Seçili hesap silindiyse seçimi bırak.
    LaunchedEffect(rows, selectedId) {
        if (selectedId != null && rows.none { it.id == selectedId }) selectedId = null
    }

    if (!wide) {
        ChatList(rows, entries, selectedId = null, showBack = true, onBack = onBack, onPick = onOpenChat, modifier = Modifier.fillMaxSize())
        return
    }

    val k = KriptonTheme.colors
    Row(Modifier.fillMaxSize()) {
        ChatList(
            rows,
            entries,
            selectedId = selectedId,
            showBack = false,
            onBack = onBack,
            onPick = { selectedId = it },
            onOpenWorkspace = onOpenWorkspace,
            modifier = Modifier.width(if (windowClass == WindowClass.Expanded) 380.dp else 320.dp).fillMaxHeight(),
        )
        VerticalDivider(thickness = 1.5.dp, color = k.nodeBorder)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val id = selectedId
            if (id == null) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.Email, contentDescription = null, tint = k.muted, modifier = Modifier.size(56.dp))
                    Text(
                        stringResource(R.string.web_chats_pick),
                        style = MaterialTheme.typography.titleMedium,
                        color = k.muted,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            } else {
                // key: başka AI seçilince önceki sayfa kapanıp (çerezler kaydedilip) yenisi açılır.
                key(id) {
                    WebChatScreen(
                        accountId = id,
                        container = container,
                        onBack = { selectedId = null },
                        onOpenMonitor = onOpenMonitor,
                        showBack = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatList(
    rows: List<AiAccount>,
    entries: List<WebMonitorEntry>,
    selectedId: Long?,
    showBack: Boolean,
    onBack: () -> Unit,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenWorkspace: (() -> Unit)? = null,
) {
    val k = KriptonTheme.colors
    Column(modifier) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            } else {
                Box(Modifier.size(16.dp))
            }
            Text(stringResource(R.string.web_chats_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (onOpenWorkspace != null) {
                androidx.compose.material3.TextButton(onClick = onOpenWorkspace) { Text(stringResource(R.string.ws_open_button)) }
            }
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
                val selected = a.id == selectedId
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (selected) k.nodeFill else Color.Transparent)
                        .clickable { onPick(a.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
fun WebChatScreen(accountId: Long, container: AppContainer, onBack: () -> Unit, onOpenMonitor: () -> Unit, showBack: Boolean = true) {
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
    BackHandler(enabled = showBack) { onBack() }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            } else {
                Box(Modifier.size(12.dp))
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
internal fun Notice(message: String, actionText: String?, onAction: (() -> Unit)?) {
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

/**
 * WebView'ı ekranı dolduracak boyutta barındırır; uygulama arka plana geçince sayfayı geri alır, dönünce yeniden koyar.
 *
 * [installFileChooser]: tek sayfalı ekranda true. Çok sayfalı çalışma alanında seçici EKRAN başına bir kez kurulur
 * (her sayfa kendi seçicisini kursaydı biri kapanırken ötekilerin seçicisini de silerdi), bu yüzden orada false.
 * [onFocus]: kullanıcı sayfaya dokununca ya da sayfadaki bir alana (klavye) odaklanınca çağrılır.
 */
@Composable
internal fun ChatPage(handle: WebMonitorHandle?, installFileChooser: Boolean = true, onFocus: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val host = remember { ChatHost(context) }
    if (installFileChooser) WebFileChooserEffect()
    val currentHandle by rememberUpdatedState(handle)
    val currentOnFocus by rememberUpdatedState(onFocus)
    host.onFocused = { currentOnFocus() }
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

    /** İçindeki WebView odak aldığında (dokunma ya da klavye ile yazma) çağrılır. */
    var onFocused: (() -> Unit)? = null

    private val focusListener = android.view.ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
        if (newFocus != null && newFocus !== this && isInside(newFocus)) onFocused?.invoke()
    }

    init {
        // Klavyenin bu sayfaya yazabilmesi için dokununca odak alabilmeli.
        isFocusable = true
        isFocusableInTouchMode = true
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
    }

    private fun isInside(v: android.view.View): Boolean {
        var p: android.view.ViewParent? = v.parent
        while (p != null) {
            if (p === this) return true
            p = p.parent
        }
        return false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)
    }

    override fun onDetachedFromWindow() {
        runCatching { viewTreeObserver.removeOnGlobalFocusChangeListener(focusListener) }
        super.onDetachedFromWindow()
    }

    fun show(handle: WebMonitorHandle?) {
        if (handle === mounted) return
        mounted?.unmount(this)
        mounted = handle
        if (handle == null) removeAllViews() else handle.mountResponsive(this)
    }
}
