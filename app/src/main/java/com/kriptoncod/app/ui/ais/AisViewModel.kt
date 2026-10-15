package com.kriptoncod.app.ui.ais

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kriptoncod.app.R
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.repo.AiAccountRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ListAction {
    data object Reload : ListAction
    data class SetEnabled(val id: Long, val enabled: Boolean) : ListAction
    data class Delete(val id: Long) : ListAction
}

/** [retry] her zaman doludur: hata kartındaki "Yeniden Dene" aynı eylemi tekrarlar. */
data class ListError(@StringRes val messageRes: Int, val retry: ListAction)

data class AisUiState(
    val loading: Boolean = true,
    val accounts: List<AiAccount> = emptyList(),
    val pendingDelete: AiAccount? = null,
    val error: ListError? = null,
)

class AisViewModel(private val repository: AiAccountRepository) : ViewModel() {
    private val _state = MutableStateFlow(AisUiState())
    val state: StateFlow<AisUiState> = _state.asStateFlow()

    private var collectJob: Job? = null

    init {
        startCollecting()
    }

    private fun startCollecting() {
        collectJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        collectJob = viewModelScope.launch {
            repository.observeAll()
                .catch {
                    _state.update { s ->
                        s.copy(loading = false, error = ListError(R.string.err_load_failed, ListAction.Reload))
                    }
                }
                .collect { list ->
                    _state.update { s ->
                        s.copy(
                            loading = false,
                            accounts = list,
                            pendingDelete = s.pendingDelete?.let { p -> list.firstOrNull { it.id == p.id } },
                        )
                    }
                }
        }
    }

    fun setEnabled(id: Long, enabled: Boolean) = perform(ListAction.SetEnabled(id, enabled))

    fun requestDelete(id: Long) {
        _state.update { s -> s.copy(pendingDelete = s.accounts.firstOrNull { it.id == id }) }
    }

    fun cancelDelete() {
        _state.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val target = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }
        perform(ListAction.Delete(target.id))
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun retry() {
        val action = _state.value.error?.retry ?: return
        perform(action)
    }

    private fun perform(action: ListAction) {
        if (action is ListAction.Reload) {
            startCollecting()
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                when (action) {
                    is ListAction.SetEnabled -> repository.setEnabled(action.id, action.enabled)
                    is ListAction.Delete -> repository.delete(action.id)
                    ListAction.Reload -> Unit
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.update { it.copy(error = ListError(R.string.err_action_failed, action)) }
            }
        }
    }
}
