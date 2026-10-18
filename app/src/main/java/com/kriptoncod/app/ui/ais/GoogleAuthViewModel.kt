package com.kriptoncod.app.ui.ais

import android.app.Activity
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kriptoncod.app.R
import com.kriptoncod.app.data.google.GeminiGoogleAuth
import com.kriptoncod.app.data.google.GoogleAuthClient
import com.kriptoncod.app.data.google.GoogleAuthState
import com.kriptoncod.app.data.google.GoogleTokenStore
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AuthType
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Hesap düzenleyicisinde Google OAuth kartının durumu. */
sealed interface GoogleAuthCardState {
    /** OAuth istemci kimliği tanımlanmamış; kart pasif. */
    data class Unavailable(@param:StringRes val reasonRes: Int) : GoogleAuthCardState

    /** Bağlanılmamış; "Google ile bağlan" düğmesi aktif. */
    data object Disconnected : GoogleAuthCardState

    /** Bağlanılıyor / yenileniyor. */
    data object Loading : GoogleAuthCardState

    /** Token geçerli; hesap bağlı. */
    data object Connected : GoogleAuthCardState

    /** Token yenilenemiyor; kullanıcı yeniden giriş yapmalı. */
    data object NeedsReauth : GoogleAuthCardState

    /** Hata; [messageRes] gösterilir. */
    data class Error(@param:StringRes val messageRes: Int) : GoogleAuthCardState
}

/**
 * Gemini Google OAuth akışını yönetir.
 * [accountId] null ise yeni hesap akışındayız; token henüz kaydedilmemiş.
 */
class GoogleAuthViewModel(
    private val accountId: Long?,
    private val tokenStore: GoogleTokenStore,
    private val authClient: GoogleAuthClient,
    private val repository: AiAccountRepository,
    private val clientIdAvailable: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow<GoogleAuthCardState>(GoogleAuthCardState.Loading)
    val state: StateFlow<GoogleAuthCardState> = _state.asStateFlow()

    init {
        if (!clientIdAvailable) {
            _state.value = GoogleAuthCardState.Unavailable(R.string.google_auth_no_client_id)
        } else {
            viewModelScope.launch { checkExistingToken() }
        }
    }

    private suspend fun checkExistingToken() {
        val id = accountId ?: run {
            _state.value = GoogleAuthCardState.Disconnected
            return
        }
        val auth = geminiAuth(id)
        _state.value = when (auth.silentRefreshIfNeeded()) {
            is GoogleAuthState.Valid -> GoogleAuthCardState.Connected
            is GoogleAuthState.NeedsReauth -> GoogleAuthCardState.NeedsReauth
            is GoogleAuthState.Error -> GoogleAuthCardState.Disconnected
        }
    }

    /** Kullanıcı "Google ile bağlan" düğmesine bastı. */
    fun signIn(activity: Activity) {
        val id = accountId ?: return // Yeni hesap: önce hesap kaydedilmeli
        _state.value = GoogleAuthCardState.Loading
        viewModelScope.launch {
            try {
                val auth = geminiAuth(id)
                when (val result = auth.signIn(activity)) {
                    is GoogleAuthState.Valid -> {
                        // Hesap durumunu READY yap (NeedsReauth'dan kurtarma)
                        val account = repository.get(id)
                        if (account?.status == AccountStatus.ERROR) repository.markReady(id)
                        _state.value = GoogleAuthCardState.Connected
                    }
                    is GoogleAuthState.NeedsReauth -> _state.value = GoogleAuthCardState.NeedsReauth
                    is GoogleAuthState.Error -> _state.value =
                        GoogleAuthCardState.Error(R.string.google_auth_sign_in_failed)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                _state.value = GoogleAuthCardState.Error(R.string.google_auth_sign_in_failed)
            }
        }
    }

    /** Sessiz yenileme – WorkManager veya API çağrısı öncesinde tetiklenebilir. */
    fun silentRefresh() {
        val id = accountId ?: return
        viewModelScope.launch {
            _state.value = GoogleAuthCardState.Loading
            val auth = geminiAuth(id)
            _state.value = when (auth.silentRefreshIfNeeded()) {
                is GoogleAuthState.Valid -> GoogleAuthCardState.Connected
                is GoogleAuthState.NeedsReauth -> {
                    repository.markError(id)
                    GoogleAuthCardState.NeedsReauth
                }
                is GoogleAuthState.Error ->
                    GoogleAuthCardState.Error(R.string.google_auth_refresh_failed)
            }
        }
    }

    /** Oturumu kapat; token'ı sil. */
    fun signOut() {
        val id = accountId ?: return
        viewModelScope.launch {
            try {
                geminiAuth(id).signOut()
                _state.value = GoogleAuthCardState.Disconnected
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                _state.value = GoogleAuthCardState.Error(R.string.google_auth_sign_out_failed)
            }
        }
    }

    private fun geminiAuth(id: Long) = GeminiGoogleAuth(tokenStore, authClient, id)

    class Factory(
        private val accountId: Long?,
        private val tokenStore: GoogleTokenStore,
        private val authClient: GoogleAuthClient,
        private val repository: AiAccountRepository,
        private val clientIdAvailable: Boolean,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GoogleAuthViewModel(accountId, tokenStore, authClient, repository, clientIdAvailable) as T
    }
}
