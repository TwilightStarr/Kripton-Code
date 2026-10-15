package com.kriptoncod.app.ui.prompts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kriptoncod.app.ui.ais.rememberContainer

@Composable
internal fun rememberPromptsViewModelFactory(): ViewModelProvider.Factory {
    val container = rememberContainer()
    return remember(container) {
        viewModelFactory { initializer { PromptsViewModel(container.promptRepository) } }
    }
}

@Composable
internal fun rememberPromptSetViewModelFactory(setId: Long): ViewModelProvider.Factory {
    val container = rememberContainer()
    return remember(container, setId) {
        viewModelFactory {
            initializer {
                PromptSetViewModel(setId, container.promptRepository, container.aiAccountRepository)
            }
        }
    }
}

@Composable
internal fun rememberPromptEditorViewModelFactory(setId: Long, promptId: Long?): ViewModelProvider.Factory {
    val container = rememberContainer()
    return remember(container, setId, promptId) {
        viewModelFactory {
            initializer { PromptEditorViewModel(setId, promptId, container.promptRepository) }
        }
    }
}
