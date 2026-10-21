package com.kriptoncod.app.ui.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.data.repo.UsageRepository
import com.kriptoncod.app.data.run.PoolStatus
import com.kriptoncod.app.data.run.UsageReport
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UsageUiState(
    val window: UsageReport.Window = UsageReport.Window.DAY,
    val report: UsageReport.Report? = null,
    /** Kota ve dinlenme durumu (son 24 saat); [PoolStatus] ile AI'lar ekranıyla aynı kurallar. */
    val pool: Map<Long, PoolStatus.Entry> = emptyMap(),
    val loading: Boolean = true,
)

/** Maliyet ve kota panosu: seçili pencerenin olaylarını canlı izler, raporu ve havuz durumunu hesaplar. */
@OptIn(ExperimentalCoroutinesApi::class)
class UsageViewModel(
    private val accounts: AiAccountRepository,
    private val usage: UsageRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {
    private val window = MutableStateFlow(UsageReport.Window.DAY)
    private val _state = MutableStateFlow(UsageUiState())
    val state: StateFlow<UsageUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val events = window.flatMapLatest { w -> usage.observeEvents(w.ms).map { w to it } }
            combine(events, accounts.observeAll()) { (w, evs), accs -> Triple(w, evs, accs) }
                .collect { (w, evs, accs) ->
                    val t = now()
                    val report = UsageReport.build(evs, accs, w, t, zone())
                    val pool = PoolStatus.resolve(accs, usage.healthSnapshot(), t)
                    _state.update { it.copy(window = w, report = report, pool = pool, loading = false) }
                }
        }
    }

    fun selectWindow(value: UsageReport.Window) {
        window.value = value
    }
}
