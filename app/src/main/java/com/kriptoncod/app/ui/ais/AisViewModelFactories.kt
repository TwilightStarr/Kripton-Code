package com.kriptoncod.app.ui.ais

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.KriptonCodApp

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
