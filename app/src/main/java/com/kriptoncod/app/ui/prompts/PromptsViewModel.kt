package com.kriptoncod.app.ui.prompts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kriptoncod.app.data.db.PromptSet
import com.kriptoncod.app.data.repo.PromptRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PromptsUiState(
    val loading: Boolean = true,
    val sets: List<PromptSet> = emptyList(),
    val pendingDelete: PromptSet? = null,
)

class PromptsViewModel(private val repository: PromptRepository) : ViewModel() {
    private val _state = MutableStateFlow(PromptsUiState())
    val state: StateFlow<PromptsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeSets()
                .catch { _state.update { it.copy(loading = false) } }
                .collect { list ->
                    _state.update { s ->
                        s.copy(
                            loading = false,
                            sets = list,
                            pendingDelete = s.pendingDelete?.let { p -> list.firstOrNull { it.id == p.id } },
                        )
                    }
                }
        }
    }

    fun createSet(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        launchSafely { repository.createSet(trimmed) }
    }

    fun requestDelete(id: Long) {
        _state.update { s -> s.copy(pendingDelete = s.sets.firstOrNull { it.id == id }) }
    }

    fun cancelDelete() {
        _state.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val target = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }
        launchSafely { repository.deleteSet(target.id) }
    }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                // Sessiz: Room akışı mevcut durumu zaten yansıtır.
            }
        }
    }
}
