package com.kriptoncod.app.ui.ais

import android.webkit.WebView
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kriptoncod.app.R
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.providers.ApiResult
import com.kriptoncod.app.data.providers.ModelService
import com.kriptoncod.app.data.providers.ProviderCatalog
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.data.web.WebCookies
import com.kriptoncod.app.data.web.WebProvider
import com.kriptoncod.app.data.web.WebScripts
import com.kriptoncod.app.data.web.WebSession
import com.kriptoncod.app.data.web.WebSessionStore
import com.kriptoncod.app.domain.AuthType
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WebStep { PICK, WARNING, LOGIN, KEY, SAVING, DONE }

data class WebAccountState(
    val step: WebStep = WebStep.PICK,
    val provider: WebProvider? = null,
    val label: String = "",
    val warningChecked: Boolean = false,
    val geminiKey: String = "",
    val relogin: Boolean = false,
    @StringRes val error: Int? = null,
)

/**
 * E-posta ile (web oturumu) hesap ekleme / yeniden giriş akışı:
 * PICK -> (WARNING: Claude/Gemini) -> LOGIN (WebView) -> [Gemini: KEY] -> SAVING -> DONE.
 * Şifre hiç okunmaz; yalnız giriş sonrası çerezler [WebSessionStore]'a (KeyVault) yazılır.
 */
class WebAccountViewModel(
    private val accountId: Long?,
    private val accounts: AiAccountRepository,
    private val sessions: WebSessionStore,
    private val models: ModelService,
) : ViewModel() {

    private val _state = MutableStateFlow(WebAccountState(relogin = accountId != null))
    val state: StateFlow<WebAccountState> = _state.asStateFlow()

    private var pendingSession: WebSession? = null

    init {
        if (accountId != null) {
            viewModelScope.launch {
                val account = accounts.get(accountId)
                val provider = WebProvider.fromId(account?.providerId)
                if (account == null || provider == null) {
                    _state.update { it.copy(step = WebStep.DONE) }
                } else {
                    _state.update { it.copy(provider = provider, label = account.displayName) }
                    proceed(provider)
                }
            }
        }
    }

    fun onLabel(v: String) = _state.update { it.copy(label = v.take(60)) }
    fun onWarningChecked(v: Boolean) = _state.update { it.copy(warningChecked = v) }
    fun onGeminiKey(v: String) = _state.update { it.copy(geminiKey = v.trim(), error = null) }
    fun dismissError() = _state.update { it.copy(error = null) }

    fun selectProvider(p: WebProvider) = _state.update { it.copy(provider = p, error = null) }

    fun onContinue() {
        val p = _state.value.provider ?: return
        proceed(p)
    }

    private fun proceed(p: WebProvider) {
        if (p.requiresBanWarning && !_state.value.warningChecked) {
            _state.update { it.copy(step = WebStep.WARNING) }
        } else {
            beginLogin()
        }
    }

    fun acceptWarning() {
        if (_state.value.warningChecked) beginLogin()
    }

    private fun beginLogin() {
        viewModelScope.launch {
            // Önceki hesabın çerezleri kalmasın: her giriş temiz bir çerez deposuyla başlar.
            WebCookies.clearAll()
            _state.update { it.copy(step = WebStep.LOGIN, error = null) }
        }
    }

    /** "Girişi tamamladım": çerezleri yakalar; Gemini ise anahtar adımına geçer, diğerleri kaydedilir. */
    fun finishLogin(webView: WebView?) {
        val s = _state.value
        val p = s.provider ?: return
        viewModelScope.launch {
            val session = WebCookies.capture(p, webView)
            if (session.isEmpty) {
                _state.update { it.copy(error = R.string.web_err_no_session) }
                return@launch
            }
            if (p == WebProvider.GEMINI) {
                pendingSession = session
                _state.update { it.copy(step = WebStep.KEY, error = null) }
            } else {
                saveWebAccount(p, session)
            }
        }
    }

    private suspend fun saveWebAccount(p: WebProvider, session: WebSession) {
        _state.update { it.copy(step = WebStep.SAVING) }
        try {
            if (accountId != null) {
                sessions.save(accountId, session)
                accounts.markReady(accountId)
            } else {
                val id = accounts.add(newAccount(p), secret = null)
                try {
                    sessions.save(id, session)
                } catch (t: Throwable) {
                    accounts.delete(id)
                    throw t
                }
            }
            WebCookies.clearAll()
            _state.update { it.copy(step = WebStep.DONE) }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            _state.update { it.copy(step = WebStep.LOGIN, error = R.string.web_err_save) }
        }
    }

    private suspend fun newAccount(p: WebProvider, authType: AuthType = AuthType.WEB_SESSION, modelId: String = "web", rank: Int = p.powerRank): AiAccount {
        val taken = accounts.observeAll().first().map { it.displayName }.toSet()
        val typed = _state.value.label.trim()
        val name = if (typed.isNotEmpty() && typed !in taken) {
            typed
        } else {
            val stem = typed.ifEmpty { p.displayName }
            var n = 1
            while ("$stem $n" in taken) n++
            "$stem $n"
        }
        return AiAccount(providerId = p.id, displayName = name, authType = authType, modelId = modelId, powerRank = rank)
    }

    /** Gemini: AI Studio sayfasındaki anahtarı okur. */
    fun readKeyFromPage(webView: WebView?) {
        if (webView == null) return
        viewModelScope.launch {
            val found = WebCookies.unquote(WebCookies.evalString(webView, WebScripts.FIND_API_KEY))
            if (found.isBlank()) _state.update { it.copy(error = R.string.web_key_not_found) }
            else _state.update { it.copy(geminiKey = found, error = null) }
        }
    }

    /** Gemini: anahtarı doğrular, en uygun modeli seçer ve API_KEY hesabı olarak kaydeder. */
    fun saveGeminiKey() {
        val key = _state.value.geminiKey
        if (!GEMINI_KEY.matches(key)) {
            _state.update { it.copy(error = R.string.web_key_invalid) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(step = WebStep.SAVING, error = null) }
            try {
                val endpoint = ProviderCatalog.byId(ProviderCatalog.GEMINI_ID)?.resolve()
                    ?: throw IllegalStateException("gemini endpoint")
                when (val r = models.discover(endpoint, key)) {
                    is ApiResult.Ok -> {
                        val rec = r.value.recommended ?: r.value.models.firstOrNull()?.model
                        if (rec == null) {
                            _state.update { it.copy(step = WebStep.KEY, error = R.string.web_key_rejected) }
                            return@launch
                        }
                        val score = r.value.models.firstOrNull { it.model.id == rec.id }?.score ?: WebProvider.GEMINI.powerRank
                        accounts.add(newAccount(WebProvider.GEMINI, AuthType.API_KEY, rec.id, score), key)
                        WebCookies.clearAll()
                        pendingSession = null
                        _state.update { it.copy(step = WebStep.DONE, geminiKey = "") }
                    }
                    is ApiResult.Fail -> _state.update { it.copy(step = WebStep.KEY, error = R.string.web_key_rejected) }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                _state.update { it.copy(step = WebStep.KEY, error = R.string.web_err_save) }
            }
        }
    }

    private companion object {
        val GEMINI_KEY = Regex("^AIza[0-9A-Za-z_\\-]{35}$")
    }
}
