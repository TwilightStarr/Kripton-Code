package com.kriptoncod.app.ui.ais

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kriptoncod.app.R
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.providers.ApiError
import com.kriptoncod.app.data.providers.ApiResult
import com.kriptoncod.app.data.providers.ModelRanking
import com.kriptoncod.app.data.providers.Provider
import com.kriptoncod.app.data.providers.ProviderCatalog
import com.kriptoncod.app.data.providers.ProviderEndpoint
import com.kriptoncod.app.data.providers.ProxyProvider
import com.kriptoncod.app.data.providers.ProxyUrlError
import com.kriptoncod.app.data.providers.ProxyUrlResult
import com.kriptoncod.app.data.providers.UpstreamProvider
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AuthType
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RetryAction { VERIFY, SAVE }

/** [retry] null ise "Yeniden Dene" gösterilmez (ör. geçersiz anahtar: önce anahtar düzeltilmeli). */
data class EditorError(@param:StringRes val messageRes: Int, val retry: RetryAction?)

enum class Pricing { FREE, PAID, FREE_TIER, UNKNOWN }

/** [upstream]: modelin ait olduğu sağlayıcı; proxy hesaplarında sağlayıcı süzgeci ve hesabın `providerId`'si. */
data class ModelOption(
    val id: String,
    val score: Int,
    val recommended: Boolean,
    val pricing: Pricing,
    val upstream: UpstreamProvider = UpstreamProvider.OTHER,
)

data class EditorUiState(
    val editingId: Long? = null,
    val loading: Boolean = false,
    val providerId: String? = null,
    val displayName: String? = null,
    /** Bellekte tutulur, [SecretText] sayesinde yazdırılsa bile sızmaz. */
    val apiKey: SecretText = SecretText(""),
    val verifying: Boolean = false,
    val verified: Boolean = false,
    val models: List<ModelOption> = emptyList(),
    val selectedModelId: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: EditorError? = null,
    /** Yalnız proxy hesabı: kullanıcının verdiği ad ("Benim hesabım", "Arkadaşım" vb.); boşsa otomatik. */
    val accountName: String = "",
    /** Yalnız proxy hesabı: taban adres (ör. http://192.168.1.5:8317). */
    val baseUrl: String = "",
    /** Yalnız proxy hesabı: model listesini süzen sağlayıcı; null = tümü. */
    val upstreamFilter: UpstreamProvider? = null,
) {
    val isEdit: Boolean get() = editingId != null
    val isProxy: Boolean get() = providerId == ProxyProvider.ID

    val canVerify: Boolean
        get() = providerId != null && !loading && !verifying && !saving && (!apiKey.isEmpty || isEdit) &&
            (!isProxy || baseUrl.isNotBlank())

    /** Proxy'de sağlayıcı süzgecine göre görünen modeller; diğer hesaplarda hepsi. */
    val visibleModels: List<ModelOption>
        get() = if (isProxy && upstreamFilter != null) models.filter { it.upstream == upstreamFilter } else models

    /** Proxy model listesinde bulunan sağlayıcılar (süzgeç çipleri için), sabit sırada. */
    val availableUpstreams: List<UpstreamProvider>
        get() = if (isProxy) UpstreamProvider.entries.filter { u -> models.any { it.upstream == u } } else emptyList()

    val canSave: Boolean
        get() = verified && selectedModelId != null && !verifying && !saving && !saved
}

/**
 * "+ AI Ekle" ve düzenleme akışı. [discover] doğrula + modelleri çek + puanla adımıdır
 * (üretimde ModelService.discover); [providerName] hesap adını üretmek için yerelleştirilmiş ad verir.
 */
class AccountEditorViewModel(
    private val accountId: Long?,
    private val repository: AiAccountRepository,
    private val discover: suspend (ProviderEndpoint, String) -> ApiResult<ModelRanking>,
    private val providerName: (Provider) -> String,
    /** Proxy hesabına otomatik ad verirken kullanılan yerelleştirilmiş ön ek. */
    private val proxyName: String = "Proxy",
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState(editingId = accountId, loading = accountId != null))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var verifyJob: Job? = null
    private var originalModelId: String? = null

    init {
        if (accountId != null) {
            verifyJob = viewModelScope.launch {
                val account = try {
                    repository.get(accountId)
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    null
                }
                if (account == null) {
                    _state.update { it.copy(loading = false, error = EditorError(R.string.err_account_missing, null)) }
                    return@launch
                }
                originalModelId = account.modelId
                val proxy = account.authType == AuthType.PROXY
                _state.update {
                    it.copy(
                        loading = false,
                        providerId = if (proxy) ProxyProvider.ID else account.providerId,
                        displayName = account.displayName,
                        accountName = if (proxy) account.displayName else "",
                        baseUrl = if (proxy) account.baseUrl.orEmpty() else "",
                    )
                }
                runVerify()
            }
        }
    }

    fun selectProvider(id: String) {
        val s = _state.value
        if (s.isEdit || s.verifying || s.saving || s.providerId == id) return
        if (id != ProxyProvider.ID) {
            val provider = ProviderCatalog.byId(id) ?: return
            if (provider.isCustom) return
        }
        _state.update {
            it.copy(
                providerId = id,
                apiKey = SecretText(""),
                verified = false,
                models = emptyList(),
                selectedModelId = null,
                upstreamFilter = null,
                error = null,
            )
        }
    }

    /** Proxy: taban adres değişince doğrulama ve model listesi geçersizleşir. */
    fun onBaseUrlChanged(raw: String) {
        val s = _state.value
        if (s.saving || s.loading || !s.isProxy) return
        verifyJob?.cancel()
        _state.update {
            it.copy(
                baseUrl = raw.trim(),
                verifying = false,
                verified = false,
                models = emptyList(),
                selectedModelId = null,
                upstreamFilter = null,
                error = null,
            )
        }
    }

    /** Proxy: hesap adı (ör. "Arkadaşım"). Doğrulamayı etkilemez. */
    fun onNameChanged(raw: String) {
        val s = _state.value
        if (s.saving || s.loading || !s.isProxy) return
        _state.update { it.copy(accountName = raw) }
    }

    /**
     * Proxy: sağlayıcı seçimi (null = tümü). Seçili model yeni süzgeçte yoksa, süzgeçteki en iyi
     * (listenin başındaki) model seçilir.
     */
    fun selectUpstream(upstream: UpstreamProvider?) {
        val s = _state.value
        if (!s.isProxy || s.saving || s.verifying) return
        _state.update { cur ->
            val visible = if (upstream == null) cur.models else cur.models.filter { it.upstream == upstream }
            val keep = cur.selectedModelId?.takeIf { id -> visible.any { it.id == id } }
            cur.copy(upstreamFilter = upstream, selectedModelId = keep ?: visible.firstOrNull()?.id)
        }
    }

    fun onKeyChanged(raw: String) {
        val s = _state.value
        if (s.saving || s.loading) return
        verifyJob?.cancel()
        val key = raw.filterNot { it.isWhitespace() }
        _state.update {
            it.copy(
                apiKey = SecretText(key),
                verifying = false,
                verified = false,
                models = emptyList(),
                selectedModelId = null,
                upstreamFilter = null,
                error = null,
            )
        }
    }

    fun onPasteEmpty() {
        _state.update { it.copy(error = EditorError(R.string.err_clipboard_empty, null)) }
    }

    fun selectModel(id: String) {
        _state.update { s -> if (s.models.any { it.id == id } && !s.saving) s.copy(selectedModelId = id) else s }
    }

    fun verify() {
        if (!_state.value.canVerify) return
        verifyJob?.cancel()
        verifyJob = viewModelScope.launch { runVerify() }
    }

    fun retry() {
        when (_state.value.error?.retry) {
            RetryAction.VERIFY -> verify()
            RetryAction.SAVE -> save()
            null -> Unit
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun runVerify() {
        val s = _state.value
        val provider: Provider?
        val endpoint: ProviderEndpoint?
        if (s.isProxy) {
            provider = null
            when (val parsed = ProxyProvider.parseBaseUrl(s.baseUrl)) {
                is ProxyUrlResult.Invalid -> {
                    failVerify(EditorError(parsed.error.messageRes, null))
                    return
                }

                is ProxyUrlResult.Valid -> endpoint = ProxyProvider.endpoint(parsed.baseUrl.toString())
            }
        } else {
            provider = s.providerId?.let { ProviderCatalog.byId(it) }
            endpoint = provider?.resolve()
        }
        if (endpoint == null || (provider == null && !s.isProxy)) {
            failVerify(EditorError(R.string.err_bad_response, null))
            return
        }
        _state.update { it.copy(verifying = true, error = null) }
        val typed = s.apiKey.value
        val key = if (typed.isNotEmpty()) {
            typed
        } else {
            val stored = s.editingId?.let { id ->
                try {
                    repository.getSecret(id)
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    null
                }
            }
            if (stored.isNullOrEmpty()) {
                failVerify(EditorError(R.string.err_key_unreadable, null))
                return
            }
            stored
        }
        val result = discover(endpoint, key)
        currentCoroutineContext().ensureActive() // iptal edilmiş (anahtar değişmiş) işin sonucu uygulanmaz
        when (result) {
            is ApiResult.Ok -> applyRanking(provider, result.value)
            is ApiResult.Fail -> failVerify(result.error.toEditorError())
        }
    }

    private fun applyRanking(provider: Provider?, ranking: ModelRanking) {
        val recommendedId = ranking.recommended?.id
        val options = ranking.models.map {
            ModelOption(
                id = it.model.id,
                score = it.score,
                recommended = it.model.id == recommendedId,
                pricing = if (provider == null) Pricing.UNKNOWN else pricingOf(provider, it.model.id),
                upstream = UpstreamProvider.detect(it.model.id, it.model.ownedBy),
            )
        }
        if (options.isEmpty()) {
            failVerify(EditorError(R.string.err_no_models, RetryAction.VERIFY))
            return
        }
        val ordered = options.filter { it.recommended } + options.filterNot { it.recommended }
        val current = originalModelId?.takeIf { id -> ordered.any { it.id == id } }
        val selected = ordered.first { it.id == (current ?: ordered.first().id) }
        _state.update {
            it.copy(
                verifying = false,
                verified = true,
                models = ordered,
                selectedModelId = selected.id,
                // Proxy: başlangıç süzgeci seçili modelin sağlayıcısıdır; kullanıcı "Tümü"ne dönebilir.
                upstreamFilter = if (it.isProxy) selected.upstream else null,
                error = null,
            )
        }
    }

    private fun failVerify(error: EditorError) {
        _state.update {
            it.copy(verifying = false, verified = false, models = emptyList(), selectedModelId = null, error = error)
        }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        val option = s.models.firstOrNull { it.id == s.selectedModelId } ?: return
        val editorProviderId = s.providerId ?: return
        val proxy = s.isProxy
        val provider = if (proxy) null else (ProviderCatalog.byId(editorProviderId) ?: return)
        // Proxy hesabında kayıtlı sağlayıcı, seçilen modelin yukarı akış sağlayıcısıdır; taban adres normalleştirilir.
        val baseUrl = if (proxy) (ProxyProvider.normalizeBaseUrl(s.baseUrl) ?: return) else null
        val providerId = if (proxy) option.upstream.id else editorProviderId
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                if (s.editingId == null) {
                    val taken = repository.observeAll().first().map { it.displayName }.toSet()
                    val account = AiAccount(
                        providerId = providerId,
                        displayName = if (proxy) {
                            proxyDisplayName(s.accountName, taken)
                        } else {
                            nextName(providerName(provider!!), taken)
                        },
                        authType = if (proxy) AuthType.PROXY else AuthType.API_KEY,
                        modelId = option.id,
                        powerRank = option.score,
                        baseUrl = baseUrl,
                    )
                    repository.add(account, s.apiKey.value) // anahtar KeyVault'a, hesap Room'a
                } else {
                    val current = repository.get(s.editingId) ?: throw IllegalStateException("missing")
                    if (!s.apiKey.isEmpty) repository.setSecret(s.editingId, s.apiKey.value)
                    val recovered = current.status == AccountStatus.ERROR
                    val taken = if (proxy) {
                        repository.observeAll().first().filter { it.id != current.id }.map { it.displayName }.toSet()
                    } else {
                        emptySet()
                    }
                    repository.update(
                        current.copy(
                            providerId = if (proxy) providerId else current.providerId,
                            baseUrl = if (proxy) baseUrl else current.baseUrl,
                            displayName = if (proxy) proxyDisplayName(s.accountName, taken, current.displayName) else current.displayName,
                            modelId = option.id,
                            powerRank = option.score,
                            status = if (recovered) AccountStatus.READY else current.status,
                            waitUntil = if (recovered) null else current.waitUntil,
                        ),
                    )
                }
                _state.update { it.copy(saving = false, saved = true, apiKey = SecretText("")) }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.update { it.copy(saving = false, error = EditorError(R.string.err_save_failed, RetryAction.SAVE)) }
            }
        }
    }

    private fun ApiError.toEditorError(): EditorError = EditorError(
        messageRes,
        when (this) {
            ApiError.INVALID_KEY, ApiError.NOT_FOUND -> null
            else -> RetryAction.VERIFY
        },
    )

    /** Kullanıcı ad verdiyse onu (çakışırsa numaralayarak), vermediyse "Proxy N"yi kullanır. */
    private fun proxyDisplayName(typed: String, taken: Set<String>, fallback: String? = null): String {
        val name = typed.trim()
        return when {
            name.isEmpty() -> fallback ?: nextName(proxyName, taken)
            name in taken -> nextName(name, taken)
            else -> name
        }
    }

    private val ProxyUrlError.messageRes: Int
        get() = when (this) {
            ProxyUrlError.EMPTY -> R.string.err_proxy_url_empty
            ProxyUrlError.MALFORMED -> R.string.err_proxy_url_malformed
            ProxyUrlError.HAS_CREDENTIALS -> R.string.err_proxy_url_credentials
            ProxyUrlError.HAS_QUERY -> R.string.err_proxy_url_query
            ProxyUrlError.INSECURE_PUBLIC_HOST -> R.string.err_proxy_url_insecure
        }

    private fun nextName(base: String, taken: Set<String>): String {
        var n = 1
        while ("$base $n" in taken) n++
        return "$base $n"
    }

    companion object {

        /** Özel sağlayıcı kalıcı adres alanı olmadığı için akışta yer almaz. */
        val SELECTABLE_PROVIDERS: List<Provider> = ProviderCatalog.all.filterNot { it.isCustom }

        fun pricingOf(provider: Provider, modelId: String): Pricing {
            val marker = provider.freeModelMarker
            return when {
                marker != null -> if (modelId.endsWith(marker, ignoreCase = true)) Pricing.FREE else Pricing.PAID
                provider.hasFreeTier -> Pricing.FREE_TIER
                else -> Pricing.PAID
            }
        }
    }
}
