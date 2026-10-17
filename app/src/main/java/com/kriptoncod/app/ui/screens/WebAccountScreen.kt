package com.kriptoncod.app.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kriptoncod.app.R
import com.kriptoncod.app.data.web.TokenImport
import com.kriptoncod.app.data.web.WebProvider
import androidx.compose.ui.platform.LocalUriHandler
import com.kriptoncod.app.data.web.WebUserAgent
import com.kriptoncod.app.ui.ais.WebAccountState
import com.kriptoncod.app.ui.ais.WebAccountViewModel
import com.kriptoncod.app.ui.ais.WebStep
import com.kriptoncod.app.ui.ais.rememberWebAccountViewModelFactory
import com.kriptoncod.app.ui.components.KriptonLoader
import com.kriptoncod.app.ui.components.KButton
import com.kriptoncod.app.ui.components.KButtonStyle
import com.kriptoncod.app.ui.components.NodeAccent
import com.kriptoncod.app.ui.components.NodeCard
import com.kriptoncod.app.ui.theme.KriptonTheme

/** E-posta ile (web oturumu) hesap ekleme veya [accountId] verilirse yeniden giriş ekranı. */
@Composable
fun WebAccountScreen(accountId: Long?, onClose: () -> Unit, onUseApiKey: () -> Unit = {}) {
    val vm: WebAccountViewModel = viewModel(
        key = "web-account-$accountId",
        factory = rememberWebAccountViewModelFactory(accountId),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.step) { if (state.step == WebStep.DONE) onClose() }

    // Tarayıcı adımlarında (giriş/anahtar) alanın neredeyse tamamı WebView'a kalsın: başlık ve düğmeler tek üst satırda.
    val browser = state.step == WebStep.LOGIN || state.step == WebStep.KEY
    Column(
        Modifier.fillMaxSize().padding(if (browser) 8.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (browser) 4.dp else 12.dp),
    ) {
        if (!browser) {
            Text(
                stringResource(if (state.relogin) R.string.web_relogin_title else R.string.web_title),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        state.error?.let { err ->
            NodeCard(modifier = Modifier.fillMaxWidth(), accent = NodeAccent.Red) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = KriptonTheme.colors.danger)
                    Text(stringResource(err), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        when (state.step) {
            WebStep.PICK -> PickStep(state, vm, onClose, onUseApiKey)
            WebStep.TOKEN -> TokenStep(state, vm)
            WebStep.WARNING -> WarningStep(state, vm, onClose)
            WebStep.LOGIN, WebStep.KEY -> BrowserStep(state, vm, onClose)
            WebStep.SAVING -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                KriptonLoader()
                Text(stringResource(R.string.web_saving), style = MaterialTheme.typography.bodyMedium)
            }
            WebStep.DONE -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickStep(state: WebAccountState, vm: WebAccountViewModel, onClose: () -> Unit, onUseApiKey: () -> Unit) {
    val k = KriptonTheme.colors
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.web_pick_hint), style = MaterialTheme.typography.bodyMedium, color = k.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WebProvider.entries.forEach { p ->
                KButton(
                    text = stringResource(p.labelRes()),
                    onClick = { vm.selectProvider(p) },
                    style = if (state.provider == p) KButtonStyle.Primary else KButtonStyle.Outline,
                )
            }
        }
        state.provider?.let { p ->
            Text(stringResource(p.hintRes()), style = MaterialTheme.typography.bodySmall, color = k.muted)
            if (p == WebProvider.KIMI) {
                // Kimi'nin giriş sayfasında e-posta yok; en güvenilir yol API anahtarı, yedek yol token aktarma.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KButton(stringResource(R.string.web_use_api_key), onUseApiKey)
                }
            }
            OutlinedTextField(
                value = state.label,
                onValueChange = vm::onLabel,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.web_label)) },
                singleLine = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton(stringResource(R.string.web_continue), vm::onContinue, enabled = state.provider != null)
            KButton(stringResource(R.string.action_cancel), onClose, style = KButtonStyle.Outline)
        }
    }
}

/** Token aktarma: tarayıcıda girişten sonra kopyalanan oturum kayıtları yapıştırılır. */
@Composable
private fun TokenStep(state: WebAccountState, vm: WebAccountViewModel) {
    val k = KriptonTheme.colors
    val clipboard = LocalClipboardManager.current
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.web_token_hint), style = MaterialTheme.typography.bodyMedium, color = k.muted)
        KButton(
            stringResource(R.string.web_token_copy),
            { clipboard.setText(AnnotatedString(TokenImport.BOOKMARKLET)) },
            style = KButtonStyle.Outline,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.tokenText,
            onValueChange = vm::onTokenText,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.web_token_label)) },
            minLines = 3,
            maxLines = 6,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton(stringResource(R.string.web_key_save), vm::saveToken, enabled = state.tokenText.isNotBlank())
            KButton(stringResource(R.string.action_back), vm::backToPick, style = KButtonStyle.Outline)
        }
    }
}

/** Claude ve Gemini'de zorunlu: "hesabın kapatılabilir" uyarısı; onaylanmadan giriş açılmaz. */
@Composable
private fun WarningStep(state: WebAccountState, vm: WebAccountViewModel, onClose: () -> Unit) {
    val p = state.provider ?: return
    NodeCard(modifier = Modifier.fillMaxWidth(), accent = NodeAccent.Red) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = KriptonTheme.colors.danger)
                Text(stringResource(R.string.web_warning_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(if (p == WebProvider.GEMINI) R.string.web_warning_gemini else R.string.web_warning_claude),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.warningChecked, onCheckedChange = vm::onWarningChecked)
                Text(stringResource(R.string.web_warning_accept), style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KButton(stringResource(R.string.web_continue), vm::acceptWarning, enabled = state.warningChecked)
                KButton(stringResource(R.string.action_cancel), onClose, style = KButtonStyle.Outline)
            }
        }
    }
}

/**
 * LOGIN ve KEY adımlarında AYNI WebView yaşar (Gemini'de giriş sonrası AI Studio'ya geçilir).
 * Yerleşim: tek üst satır (başlık · yardım · "Girişi tamamladım" · kapat); açıklama yalnız (?) ile açılır;
 * kalan TÜM alan WebView'indir.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ColumnScope.BrowserStep(state: WebAccountState, vm: WebAccountViewModel, onClose: () -> Unit) {
    val p = state.provider ?: return
    val keyStep = state.step == WebStep.KEY
    var holder by remember { mutableStateOf<WebView?>(null) }
    var showHint by remember { mutableStateOf(false) }
    // Google, gömülü WebView'da girişe izin vermez ("disallowed_useragent"); ölü sayfaya gitmek yerine uyarı gösterilir.
    var googleBlocked by remember { mutableStateOf(false) }
    val k = KriptonTheme.colors

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(
                if (keyStep) R.string.web_key_title
                else if (state.relogin) R.string.web_relogin_title else R.string.web_title,
            ),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { showHint = !showHint }) {
            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.web_help), tint = k.muted)
        }
        TextButton(
            onClick = { if (keyStep) vm.saveGeminiKey() else vm.finishLogin(holder) },
            enabled = !keyStep || state.geminiKey.isNotBlank(),
        ) {
            Text(stringResource(if (keyStep) R.string.web_key_save else R.string.web_login_done))
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
        }
    }

    val uriHandler = LocalUriHandler.current
    if (keyStep) {
        // Google WebView'da girişi engeller: sayfa telefonun kendi tarayıcısında açılır, anahtar buraya yapıştırılır.
        Text(stringResource(R.string.web_key_hint), style = MaterialTheme.typography.bodySmall, color = k.muted)
        KButton(
            stringResource(R.string.web_key_open_browser),
            { runCatching { uriHandler.openUri(WebProvider.GEMINI.chatUrl) } },
            style = KButtonStyle.Outline,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.geminiKey,
            onValueChange = vm::onGeminiKey,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.web_key_label)) },
            singleLine = true,
        )
    } else {
        if (showHint) {
            Text(stringResource(R.string.web_login_hint), style = MaterialTheme.typography.bodySmall, color = k.muted)
            if (p == WebProvider.KIMI) {
                Text(stringResource(R.string.web_kimi_note), style = MaterialTheme.typography.bodySmall, color = k.muted)
            }
        }
        if (googleBlocked) {
            Text(
                stringResource(R.string.web_google_blocked),
                style = MaterialTheme.typography.bodySmall,
                color = k.danger,
            )
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Sohbet WebView'ıyla AYNI kullanıcı-aracısı: oturum/bot kontrolleri buna bağlıdır.
                    WebUserAgent.apply(settings)
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val scheme = request?.url?.scheme.orEmpty()
                            return scheme != "https" && scheme != "about"
                        }

                        // "Google ile devam et" doğrudan denenir. Google gömülü tarayıcıyı reddederse (kendi güvenlik
                        // kuralı) ölü sayfada bırakmak yerine açıklama gösterilir.
                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            val u = url.orEmpty()
                            if (u.contains("accounts.google.com") &&
                                (u.contains("disallowed_useragent") || u.contains("/rejected") || u.contains("oauth/error"))
                            ) {
                                googleBlocked = true
                            }
                        }
                    }
                    // Google/OAuth açılır pencereleri aynı pencerede açılsın (çoklu pencere desteklenmiyor).
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    // Dil: sağlayıcı sitesi cihaz/bölge diline göre (ör. Kimi'de Çince) açılabilir; İngilizce/Türkçe tercih edilir.
                    loadUrl(p.loginUrl, mapOf("Accept-Language" to "en-US,en;q=0.9,tr;q=0.8"))
                    holder = this
                }
            },
        )
    }
    DisposableEffect(Unit) {
        onDispose { holder?.let { runCatching { it.stopLoading(); it.destroy() } } }
    }
}

private fun WebProvider.labelRes(): Int = when (this) {
    WebProvider.CLAUDE -> R.string.web_provider_claude
    WebProvider.CHATGPT -> R.string.web_provider_chatgpt
    WebProvider.KIMI -> R.string.web_provider_kimi
    WebProvider.GEMINI -> R.string.web_provider_gemini
}

private fun WebProvider.hintRes(): Int = when (this) {
    WebProvider.CLAUDE -> R.string.web_hint_claude
    WebProvider.CHATGPT -> R.string.web_hint_chatgpt
    WebProvider.KIMI -> R.string.web_hint_kimi
    WebProvider.GEMINI -> R.string.web_hint_gemini
}
