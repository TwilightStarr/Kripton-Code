package com.kriptoncod.app.ui.ais

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.KriptonCodApp
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
        // runBlocking: Compose çağrı thread'inde DataStore ilk değerini okumak için.
        // DataStore önbellekte tutulur — ikinci çağrıda gecikme olmaz.
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
