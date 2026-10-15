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
data class EditorError(@StringRes val messageRes: Int, val retry: RetryAction?)

enum class Pricing { FREE, PAID, FREE_TIER }

data class ModelOption(val id: String, val score: Int, val recommended: Boolean, val pricing: Pricing)

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
) {
    val isEdit: Boolean get() = editingId != null
    val canVerify: Boolean
        get() = providerId != null && !loading && !verifying && !saving && (!apiKey.isEmpty || isEdit)
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
                _state.update {
                    it.copy(loading = false, providerId = account.providerId, displayName = account.displayName)
                }
                runVerify()
            }
        }
    }

    fun selectProvider(id: String) {
        val s = _state.value
        if (s.isEdit || s.verifying || s.saving || s.providerId == id) return
        val provider = ProviderCatalog.byId(id) ?: return
        if (provider.isCustom) return
        _state.update {
            it.copy(
                providerId = id,
                apiKey = SecretText(""),
                verified = false,
                models = emptyList(),
                selectedModelId = null,
                error = null,
            )
        }
    }

    fun onKeyChanged(raw: String) {
        val s = _state.value
        if (s.saving || s.loading) return
        verifyJob?.cancel()
        val key = raw.filterNot { it.isWhitespace() }.take(MAX_KEY_LENGTH)
        _state.update {
            it.copy(
                apiKey = SecretText(key),
                verifying = false,
                verified = false,
                models = emptyList(),
                selectedModelId = null,
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
        val provider = s.providerId?.let { ProviderCatalog.byId(it) }
        val endpoint = provider?.resolve()
        if (provider == null || endpoint == null) {
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

    private fun applyRanking(provider: Provider, ranking: ModelRanking) {
        val recommendedId = ranking.recommended?.id
        val options = ranking.models.map {
            ModelOption(it.model.id, it.score, it.model.id == recommendedId, pricingOf(provider, it.model.id))
        }
        if (options.isEmpty()) {
            failVerify(EditorError(R.string.err_no_models, RetryAction.VERIFY))
            return
        }
        val ordered = options.filter { it.recommended } + options.filterNot { it.recommended }
        val current = originalModelId?.takeIf { id -> ordered.any { it.id == id } }
        _state.update {
            it.copy(
                verifying = false,
                verified = true,
                models = ordered,
                selectedModelId = current ?: ordered.first().id,
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
        val providerId = s.providerId ?: return
        val provider = ProviderCatalog.byId(providerId) ?: return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                if (s.editingId == null) {
                    val taken = repository.observeAll().first().map { it.displayName }.toSet()
                    val account = AiAccount(
                        providerId = providerId,
                        displayName = nextName(providerName(provider), taken),
                        authType = AuthType.API_KEY,
                        modelId = option.id,
                        powerRank = option.score,
                    )
                    repository.add(account, s.apiKey.value) // anahtar KeyVault'a, hesap Room'a
                } else {
                    val current = repository.get(s.editingId) ?: throw IllegalStateException("missing")
                    if (!s.apiKey.isEmpty) repository.setSecret(s.editingId, s.apiKey.value)
                    val recovered = current.status == AccountStatus.ERROR
                    repository.update(
                        current.copy(
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

    private fun nextName(base: String, taken: Set<String>): String {
        var n = 1
        while ("$base $n" in taken) n++
        return "$base $n"
    }

    companion object {
        const val MAX_KEY_LENGTH = 512

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
