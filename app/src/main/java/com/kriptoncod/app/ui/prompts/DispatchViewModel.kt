package com.kriptoncod.app.ui.prompts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.repo.PromptRepository
import com.kriptoncod.app.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AccountDispatchUi(
    val account: AiAccount,
    val promptCount: Int,
    val totalTokens: Int,
)

data class DispatchUiState(
    val loading: Boolean = true,
    val prompts: List<DispatchPrompt> = emptyList(),
    val accounts: List<AiAccount> = emptyList(),
    val plan: DispatchPlan = DispatchPlan(emptyMap()),
    val summaries: List<AccountDispatchUi> = emptyList(),
)

class DispatchViewModel(
    private val setId: Long,
    private val promptRepository: PromptRepository,
    private val accountRepository: com.kriptoncod.app.data.repo.AiAccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DispatchUiState())
    val state: StateFlow<DispatchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                promptRepository.observeItems(setId),
                accountRepository.observeAll(),
            ) { items, accounts ->
                val prompts = items.map { DispatchPrompt(it.id, it.body.length / 4) }
                val plan = Dispatcher.dispatch(prompts, accounts.map {
                    DispatchAccount(it.id, it.powerRank, 100_000, it.status)
                })
                Triple(prompts, accounts, plan)
            }.collect { (prompts, accounts, plan) ->
                val summaries = accounts.map { acc ->
                    val assigned = plan.assignments.filterValues { it == acc.id }
                    AccountDispatchUi(acc, assigned.size,
                        assigned.keys.sumOf { id -> prompts.firstOrNull { it.id == id }?.estimatedTokens ?: 0 })
                }
                _state.update { it.copy(loading = false, prompts = prompts,
                    accounts = accounts, plan = plan, summaries = summaries) }
            }
        }
    }

    fun reorderAccounts(fromIndex: Int, toIndex: Int) {
        val accounts = _state.value.accounts.toMutableList()
        if (fromIndex !in accounts.indices || toIndex !in accounts.indices) return
        accounts.add(toIndex, accounts.removeAt(fromIndex))
        val plan = Dispatcher.dispatch(_state.value.prompts, accounts.map {
            DispatchAccount(it.id, it.powerRank, 100_000, it.status)
        })
        _state.update { it.copy(accounts = accounts, plan = plan) }
    }

    fun assignPrompt(promptId: Long, accountId: Long) {
        val prompts = _state.value.prompts.map {
            if (it.id == promptId) it.copy(manualAccountId = accountId) else it
        }
        val plan = Dispatcher.dispatch(prompts, _state.value.accounts.map {
            DispatchAccount(it.id, it.powerRank, 100_000, it.status)
        })
        _state.update { it.copy(prompts = prompts, plan = plan) }
    }

    fun autoArrange() {
        val prompts = _state.value.prompts.map { it.copy(manualAccountId = null) }
        val plan = Dispatcher.dispatch(prompts, _state.value.accounts.map {
            DispatchAccount(it.id, it.powerRank, 100_000, it.status)
        })
        _state.update { it.copy(prompts = prompts, plan = plan) }
    }

    companion object {
        fun factory(setId: Long, c: com.kriptoncod.app.AppContainer) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DispatchViewModel(setId, c.promptRepository, c.aiAccountRepository) as T
            }
    }
}
