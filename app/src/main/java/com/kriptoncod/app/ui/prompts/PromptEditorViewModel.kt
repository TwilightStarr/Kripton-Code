package com.kriptoncod.app.ui.prompts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kriptoncod.app.data.repo.PromptRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PromptEditorUiState(
    val loading: Boolean = false,
    val title: String = "",
    val body: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = !loading && !saving && title.isNotBlank()
}

/** [promptId] null ise yeni prompt eklenir, değilse mevcut prompt düzenlenir. */
class PromptEditorViewModel(
    private val setId: Long,
    private val promptId: Long?,
    private val repository: PromptRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PromptEditorUiState(loading = promptId != null))
    val state: StateFlow<PromptEditorUiState> = _state.asStateFlow()

    init {
        if (promptId != null) {
            viewModelScope.launch {
                val item = try {
                    repository.getItem(promptId)
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Throwable) {
                    null
                }
                _state.update {
                    // Prompt bulunamadıysa (silinmiş) ekranı kapat.
                    if (item == null) it.copy(loading = false, saved = true)
                    else it.copy(loading = false, title = item.title, body = item.body)
                }
            }
        }
    }

    fun onTitleChange(value: String) {
        _state.update { it.copy(title = value) }
    }

    fun onBodyChange(value: String) {
        _state.update { it.copy(body = value) }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val title = s.title.trim()
                if (promptId == null) repository.addItem(setId, title, s.body)
                else repository.updateItem(promptId, title, s.body)
                _state.update { it.copy(saving = false, saved = true) }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}
