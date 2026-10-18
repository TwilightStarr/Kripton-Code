package com.kriptoncod.app.ui.ais

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kriptoncod.app.R
import com.kriptoncod.app.data.kimi.KimiAuthClient
import com.kriptoncod.app.data.kimi.KimiAuthState
import com.kriptoncod.app.data.kimi.KimiSessionAuth
import com.kriptoncod.app.data.kimi.KimiTokenStore
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.domain.AccountStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface KimiAuthCardState {
    data object Disconnected : KimiAuthCardState
    data object Loading : KimiAuthCardState
    data object Connected : KimiAuthCardState
    data object NeedsReauth : KimiAuthCardState
    data class Error(@param:StringRes val messageRes: Int) : KimiAuthCardState
}

class KimiAuthViewModel(
    private val accountId: Long?,
    private val tokenStore: KimiTokenStore,
    private val authClient: KimiAuthClient,
    private val loginUrl: String,
    private val revokeUrl: String?,
    private val repository: AiAccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<KimiAuthCardState>(KimiAuthCardState.Loading)
    val state: StateFlow<KimiAuthCardState> = _state.asStateFlow()

    init {
        viewModelScope.launch { checkExistingToken() }
    }

    private suspend fun checkExistingToken() {
        val id = accountId ?: run {
            _state.value = KimiAuthCardState.Disconnected
            return
        }
        _state.value = when (kimiAuth(id).silentRefreshIfNeeded()) {
            is KimiAuthState.Valid -> KimiAuthCardState.Connected
            is KimiAuthState.NeedsReauth -> KimiAuthCardState.NeedsReauth
            is KimiAuthState.Error -> KimiAuthCardState.Disconnected
        }
    }

    fun signIn(email: String, password: String) {
        val id = accountId ?: return
        _state.value = KimiAuthCardState.Loading
        viewModelScope.launch {
            try {
                when (kimiAuth(id).signIn(email, password)) {
                    is KimiAuthState.Valid -> {
                        repository.get(id)?.let {
                            if (it.status == AccountStatus.ERROR) repository.markReady(id)
                        }
                        _state.value = KimiAuthCardState.Connected
                    }
                    is KimiAuthState.NeedsReauth ->
                        _state.value = KimiAuthCardState.NeedsReauth
                    is KimiAuthState.Error ->
                        _state.value = KimiAuthCardState.Error(R.string.kimi_auth_sign_in_failed)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                _state.value = KimiAuthCardState.Error(R.string.kimi_auth_sign_in_failed)
            }
        }
    }

    fun silentRefresh() {
        val id = accountId ?: return
        viewModelScope.launch {
            _state.value = KimiAuthCardState.Loading
            _state.value = when (kimiAuth(id).silentRefreshIfNeeded()) {
                is KimiAuthState.Valid -> KimiAuthCardState.Connected
                is KimiAuthState.NeedsReauth -> {
                    repository.markError(id)
                    KimiAuthCardState.NeedsReauth
                }
                is KimiAuthState.Error ->
                    KimiAuthCardState.Error(R.string.kimi_auth_refresh_failed)
            }
        }
    }

    fun signOut() {
        val id = accountId ?: return
        viewModelScope.launch {
            try {
                kimiAuth(id).signOut()
                _state.value = KimiAuthCardState.Disconnected
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                _state.value = KimiAuthCardState.Error(R.string.kimi_auth_sign_out_failed)
            }
        }
    }

    private fun kimiAuth(id: Long) =
        KimiSessionAuth(tokenStore, authClient, id, loginUrl, revokeUrl)

    class Factory(
        private val accountId: Long?,
        private val tokenStore: KimiTokenStore,
        private val authClient: KimiAuthClient,
        private val loginUrl: String,
        private val revokeUrl: String?,
        private val repository: AiAccountRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            KimiAuthViewModel(
                accountId, tokenStore, authClient, loginUrl, revokeUrl, repository,
            ) as T
    }
}
