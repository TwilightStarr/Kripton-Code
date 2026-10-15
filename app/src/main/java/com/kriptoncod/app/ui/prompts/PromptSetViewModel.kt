package com.kriptoncod.app.ui.prompts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.db.PromptItem
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.data.repo.PromptRepository
import com.kriptoncod.app.domain.AssignmentMode
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssignmentUi(
    val accountId: Long,
    val accountName: String,
    val mode: AssignmentMode,
)

data class PromptRowUi(
    val item: PromptItem,
    val assignment: AssignmentUi?,
)

data class PromptSetUiState(
    val loading: Boolean = true,
    val setName: String = "",
    val prompts: List<PromptRowUi> = emptyList(),
    /** Atama diyaloğunda seçilebilecek aktif hesaplar. */
    val accounts: List<AiAccount> = emptyList(),
    val pendingDelete: PromptItem? = null,
) {
    val assignedCount: Int get() = prompts.count { it.assignment != null }
}

class PromptSetViewModel(
    private val setId: Long,
    private val repository: PromptRepository,
    private val accountRepository: AiAccountRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PromptSetUiState())
    val state: StateFlow<PromptSetUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeSet(setId),
                repository.observeItems(setId),
                repository.observeAssignments(setId),
                accountRepository.observeAll(),
            ) { set, items, assignments, accounts ->
                val byPrompt = assignments.associateBy { it.promptId }
                val names = accounts.associate { it.id to it.displayName }
                val rows = items.map { item ->
                    val a = byPrompt[item.id]
                    PromptRowUi(
                        item = item,
                        assignment = a?.let { AssignmentUi(it.accountId, names[it.accountId].orEmpty(), it.mode) },
                    )
                }
                Snapshot(set?.name.orEmpty(), rows, accounts.filter { it.enabled })
            }
                .catch { _state.update { it.copy(loading = false) } }
                .collect { snap ->
                    _state.update { s ->
                        s.copy(
                            loading = false,
                            setName = snap.name,
                            prompts = snap.rows,
                            accounts = snap.accounts,
                            pendingDelete = s.pendingDelete?.let { p -> snap.rows.firstOrNull { it.item.id == p.id }?.item },
                        )
                    }
                }
        }
    }

    private class Snapshot(val name: String, val rows: List<PromptRowUi>, val accounts: List<AiAccount>)

    fun moveUp(promptId: Long) = move(promptId, -1)

    fun moveDown(promptId: Long) = move(promptId, +1)

    private fun move(promptId: Long, delta: Int) {
        val ids = _state.value.prompts.map { it.item.id }.toMutableList()
        val from = ids.indexOf(promptId)
        val to = from + delta
        if (from < 0 || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        launchSafely { repository.reorder(setId, ids) }
    }

    fun assign(promptId: Long, accountId: Long, mode: AssignmentMode) {
        launchSafely { repository.assign(promptId, accountId, mode) }
    }

    fun unassign(promptId: Long) {
        launchSafely { repository.unassign(promptId) }
    }

    fun requestDelete(promptId: Long) {
        _state.update { s -> s.copy(pendingDelete = s.prompts.firstOrNull { it.item.id == promptId }?.item) }
    }

    fun cancelDelete() {
        _state.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val target = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }
        launchSafely { repository.deleteItem(target.id) }
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
