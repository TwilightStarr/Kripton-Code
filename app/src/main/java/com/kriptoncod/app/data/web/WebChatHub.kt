package com.kriptoncod.app.data.web

import com.kriptoncod.app.data.ai.AiGenerationException
import com.kriptoncod.app.data.ai.AiGenerationException.Kind
import com.kriptoncod.app.data.db.AiAccount
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * "AI sohbetleri": kullanıcının bir AI'ın web arayüzünü istediği an elle açıp gezmesi (WhatsApp benzeri liste -> sohbet).
 *
 * Açılan oturum, çalışmalarla AYNI sürücüyü ve hesabın kayıtlı oturumunu kullanır; ekrandan çıkılınca ([release]) kapanır
 * ve (giriş yapıldıysa) yenilenen çerezler kaydedilir. Hesap bir çalışma tarafından zaten kullanılıyorsa sayfa yeniden
 * açılmaz ve yeniden boyutlandırılmaz (otomasyon bozulmasın): durum "ödünç" olarak bildirilir.
 */
class WebChatHub(
    private val driver: WebChatDriver,
    private val monitor: WebMonitor,
    private val sessions: WebSessionStore,
    /** Elle girişten sonra oturum geçerliyse hesabı "hazır" yapmak için. */
    private val onLoggedIn: suspend (accountId: Long) -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    sealed interface State {
        object Idle : State
        object Opening : State

        /** [entryId]: [WebMonitor] kaydı. [borrowed]: sayfayı bir çalışma açtı; kapatmak bu sınıfın işi değil. */
        data class Ready(val entryId: Long, val borrowed: Boolean) : State

        data class Failed(val kind: Kind?) : State
    }

    private class Held(val session: WebChatSession, val provider: WebProvider)

    private val states = ConcurrentHashMap<Long, MutableStateFlow<State>>()
    private val held = ConcurrentHashMap<Long, Held>()
    private val jobs = ConcurrentHashMap<Long, Job>()

    private fun flow(id: Long) = states.getOrPut(id) { MutableStateFlow(State.Idle) }

    fun state(accountId: Long): StateFlow<State> = flow(accountId).asStateFlow()

    /** Hesabın web sayfasını açar (zaten açıksa ya da açılıyorsa bir şey yapmaz). */
    @Synchronized
    fun open(account: AiAccount) {
        val f = flow(account.id)
        val provider = WebProvider.fromId(account.providerId)
        if (provider == null || provider.output == OutputStyle.API_KEY) {
            f.value = State.Failed(Kind.BAD_REQUEST)
            return
        }
        if (jobs[account.id]?.isActive == true) return
        val live = monitor.entries.value.firstOrNull { it.accountId == account.id && it.isLive }
        if (live != null) {
            f.value = State.Ready(live.id, borrowed = !held.containsKey(account.id))
            return
        }
        f.value = State.Opening
        jobs[account.id] = scope.launch {
            try {
                val session = driver.openInteractive(account, provider)
                held[account.id] = Held(session, provider)
                val entry = monitor.entries.value.firstOrNull { it.accountId == account.id && it.isLive }
                if (entry == null) {
                    held.remove(account.id)
                    runCatching { session.close() }
                    f.value = State.Failed(null)
                } else {
                    f.value = State.Ready(entry.id, borrowed = false)
                }
            } catch (c: CancellationException) {
                f.value = State.Idle
                throw c
            } catch (e: AiGenerationException) {
                f.value = State.Failed(e.kind)
            } catch (_: Throwable) {
                f.value = State.Failed(null)
            }
        }
    }

    /** Ekrandan çıkıldı: açılışı iptal eder, bu sınıfın açtığı oturumu kapatır (çerezler kaydedilir). */
    @Synchronized
    fun release(accountId: Long) {
        jobs.remove(accountId)?.cancel()
        val h = held.remove(accountId)
        flow(accountId).value = State.Idle
        if (h != null) {
            scope.launch {
                runCatching { h.session.close() }
                runCatching {
                    val saved = sessions.load(accountId)
                    if (saved != null && h.provider.isLoggedIn(saved)) onLoggedIn(accountId)
                }
            }
        }
    }

    /** Açılışı baştan dener (hata ya da kapanmış sayfa sonrası). */
    fun reopen(account: AiAccount) {
        release(account.id)
        open(account)
    }
}
