package com.kriptoncod.app.ui.ais

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.KriptonCodApp
import com.kriptoncod.app.R
import com.kriptoncod.app.data.providers.ProviderCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@Composable
internal fun rememberContainer(): AppContainer {
    val context = LocalContext.current.applicationContext
    return remember(context) { (context as KriptonCodApp).container }
}

@Composable
internal fun rememberAisViewModelFactory(): ViewModelProvider.Factory {
    val container = rememberContainer()
    return remember(container) {
        viewModelFactory { initializer { AisViewModel(container.aiAccountRepository) } }
    }
}

@Composable
internal fun rememberEditorViewModelFactory(accountId: Long?): ViewModelProvider.Factory {
    val container = rememberContainer()
    val context = LocalContext.current.applicationContext
    return remember(container, accountId) {
        viewModelFactory {
            initializer {
                AccountEditorViewModel(
                    accountId = accountId,
                    repository = container.aiAccountRepository,
                    discover = { endpoint, key -> container.modelService.discover(endpoint, key) },
                    providerName = { provider -> context.getString(provider.nameRes) },
                    proxyName = context.getString(R.string.provider_proxy_short),
                )
            }
        }
    }
}

/**
 * [GoogleAuthViewModel] factory'si.
 * [clientIdAvailable] ilk değeri senkron okunur (DataStore başlatılmış olmalı).
 */
@Composable
internal fun rememberGoogleAuthViewModelFactory(accountId: Long?): GoogleAuthViewModel.Factory {
    val container = rememberContainer()
    return remember(container, accountId) {
        val clientIdAvailable = runBlocking {
            container.googleAuthSettings.clientIdFlow.first() != null
        }
        GoogleAuthViewModel.Factory(
            accountId = accountId,
            tokenStore = container.googleTokenStore,
            authClient = container.googleAuthClient,
            repository = container.aiAccountRepository,
            clientIdAvailable = clientIdAvailable,
        )
    }
}

/**
 * [EmailAuthViewModel] factory'si.
 * [providerId] → ProviderCatalog'dan loginUrl türetilir.
 */
@Composable
internal fun rememberEmailAuthViewModelFactory(
    accountId: Long?,
    providerId: String,
): EmailAuthViewModel.Factory {
    val container = rememberContainer()
    return remember(container, accountId, providerId) {
        val provider = ProviderCatalog.byId(providerId)
        val endpoint = provider?.resolve()
        // loginUrl ve revokeUrl endpoint'ten alınır; null ise boş string guard yeterli
        val loginUrl = endpoint?.loginUrl ?: ""
        val revokeUrl: String? = null  // Anthropic / OpenAI revoke uç noktası şu an tanımlı değil
        EmailAuthViewModel.Factory(
            accountId = accountId,
            tokenStore = container.emailTokenStore,
            authClient = container.emailAuthClient,
            loginUrl = loginUrl,
            revokeUrl = revokeUrl,
            repository = container.aiAccountRepository,
        )
    }
}

/** [KimiAuthViewModel] factory'si. Kimi sağlayıcısının loginPath'i çözümlenir. */
@Composable
internal fun rememberKimiAuthViewModelFactory(
    accountId: Long?,
    providerId: String,
): KimiAuthViewModel.Factory {
    val container = rememberContainer()
    return remember(container, accountId, providerId) {
        val loginUrl = ProviderCatalog.byId(providerId)?.resolve()?.loginUrl
        KimiAuthViewModel.Factory(
            accountId = accountId,
            tokenStore = container.kimiTokenStore,
            authClient = container.kimiAuthClient,
            loginUrl = loginUrl.orEmpty(),
            revokeUrl = null,
            repository = container.aiAccountRepository,
        )
    }
}
