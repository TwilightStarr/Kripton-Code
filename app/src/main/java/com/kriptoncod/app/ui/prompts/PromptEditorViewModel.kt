package com.kriptoncod.app.ui.prompts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kriptoncod.app.data.db.PromptItem
import com.kriptoncod.app.data.repo.PromptRepository
import com.kriptoncod.app.data.run.DirectiveIssue
import com.kriptoncod.app.data.run.PromptTemplate
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
    /** Yönerge/şablon sorunları (bilinmeyen kaynak, yanlış sıra, hatalı değişken/kural…). */
    val issues: List<DirectiveIssue> = emptyList(),
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

    /** Setteki diğer promptlar; `{{çıktı:..}}` başvurularının sırasını denetlemek için. */
    private var siblings: List<PromptItem> = emptyList()
    private var ownOrder: Int = Int.MAX_VALUE

    private fun lint(body: String): List<DirectiveIssue> {
        val others = siblings.filter { it.id != promptId }
        val earlier = others.filter { it.order < ownOrder }.map { it.title }
        return PromptTemplate.lint(body, earlier, others.map { it.title })
    }

    init {
        viewModelScope.launch {
            siblings = try {
                repository.getItems(setId)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                emptyList()
            }
            ownOrder = siblings.firstOrNull { it.id == promptId }?.order ?: Int.MAX_VALUE
            _state.update { it.copy(issues = lint(it.body)) }
        }
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
                    if (item == null) {
                        it.copy(loading = false, saved = true)
                    } else {
                        it.copy(loading = false, title = item.title, body = item.body, issues = lint(item.body))
                    }
                }
            }
        }
    }

    fun onTitleChange(value: String) {
        _state.update { it.copy(title = value) }
    }

    fun onBodyChange(value: String) {
        _state.update { it.copy(body = value, issues = lint(value)) }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val title = s.title.trim()
                if (promptId == null) {
                    repository.addItem(setId, title, s.body)
                } else {
                    repository.updateItem(promptId, title, s.body)
                }
                _state.update { it.copy(saving = false, saved = true) }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}
