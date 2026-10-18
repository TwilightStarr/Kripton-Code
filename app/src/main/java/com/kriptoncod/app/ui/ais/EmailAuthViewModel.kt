package com.kriptoncod.app.ui.ais

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kriptoncod.app.R
import com.kriptoncod.app.data.email.EmailAuthClient
import com.kriptoncod.app.data.email.EmailAuthState
import com.kriptoncod.app.data.email.EmailPasswordAuth
import com.kriptoncod.app.data.email.EmailTokenStore
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.domain.AccountStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Hesap düzenleyicisinde e-posta/şifre giriş kartının durumu. */
sealed interface EmailAuthCardState {
    /** Oturum açılmamış; "E-posta ile bağlan" formu aktif. */
    data object Disconnected : EmailAuthCardState

    /** Giriş yapılıyor / yenileniyor. */
    data object Loading : EmailAuthCardState

    /** Session token geçerli; hesap bağlı. */
    data object Connected : EmailAuthCardState

    /** Token yenilenemiyor; kullanıcı yeniden giriş yapmalı. */
    data object NeedsReauth : EmailAuthCardState

    /** Hata; [messageRes] gösterilir. */
    data class Error(@param:StringRes val messageRes: Int) : EmailAuthCardState
}

/**
 * E-posta/şifre oturum açma akışını yönetir.
 * GoogleAuthViewModel ile aynı sealed interface kalıbını izler.
 *
 * [accountId] null ise yeni hesap akışındayız; token henüz kaydedilmemiş.
 */
class EmailAuthViewModel(
    private val accountId: Long?,
    private val tokenStore: EmailTokenStore,
    private val authClient: EmailAuthClient,
    private val loginUrl: String,
    private val revokeUrl: String?,
    private val repository: AiAccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<EmailAuthCardState>(EmailAuthCardState.Loading)
    val state: StateFlow<EmailAuthCardState> = _state.asStateFlow()

    init {
        viewModelScope.launch { checkExistingToken() }
    }

    private suspend fun checkExistingToken() {
        val id = accountId ?: run {
            _state.value = EmailAuthCardState.Disconnected
            return
        }
        val auth = emailAuth(id)
        _state.value = when (auth.silentRefreshIfNeeded()) {
            is EmailAuthState.Valid     -> EmailAuthCardState.Connected
            is EmailAuthState.NeedsReauth -> EmailAuthCardState.NeedsReauth
            is EmailAuthState.Error     -> EmailAuthCardState.Disconnected
        }
    }

    /** Kullanıcı "Bağlan" düğmesine bastı. */
    fun signIn(email: String, password: String) {
        val id = accountId ?: return
        _state.value = EmailAuthCardState.Loading
        viewModelScope.launch {
            try {
                val auth = emailAuth(id)
                when (auth.signIn(email, password)) {
                    is EmailAuthState.Valid -> {
                        val account = repository.get(id)
                        if (account?.status == AccountStatus.ERROR) repository.markReady(id)
                        _state.value = EmailAuthCardState.Connected
                    }
                    is EmailAuthState.NeedsReauth ->
                        _state.value = EmailAuthCardState.NeedsReauth
                    is EmailAuthState.Error ->
                        _state.value = EmailAuthCardState.Error(R.string.email_auth_sign_in_failed)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                _state.value = EmailAuthCardState.Error(R.string.email_auth_sign_in_failed)
            }
        }
    }

    /** Sessiz yenileme — API çağrısı öncesinde tetiklenebilir. */
    fun silentRefresh() {
        val id = accountId ?: return
        viewModelScope.launch {
            _state.value = EmailAuthCardState.Loading
            val auth = emailAuth(id)
            _state.value = when (auth.silentRefreshIfNeeded()) {
                is EmailAuthState.Valid -> EmailAuthCardState.Connected
                is EmailAuthState.NeedsReauth -> {
                    repository.markError(id)
                    EmailAuthCardState.NeedsReauth
                }
                is EmailAuthState.Error ->
                    EmailAuthCardState.Error(R.string.email_auth_refresh_failed)
            }
        }
    }

    /** Oturumu kapat; token'ı sil. */
    fun signOut() {
        val id = accountId ?: return
        viewModelScope.launch {
            try {
                emailAuth(id).signOut()
                _state.value = EmailAuthCardState.Disconnected
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                _state.value = EmailAuthCardState.Error(R.string.email_auth_sign_out_failed)
            }
        }
    }

    private fun emailAuth(id: Long) =
        EmailPasswordAuth(tokenStore, authClient, id, loginUrl, revokeUrl)

    class Factory(
        private val accountId: Long?,
        private val tokenStore: EmailTokenStore,
        private val authClient: EmailAuthClient,
        private val loginUrl: String,
        private val revokeUrl: String?,
        private val repository: AiAccountRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EmailAuthViewModel(
                accountId, tokenStore, authClient, loginUrl, revokeUrl, repository,
            ) as T
    }
}
