package com.kriptoncod.app.data.web

import android.view.ViewGroup
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Açık bir web sohbetinin WebView'ına dışarıdan bakmak için tutamaç. Yalnız ana iş parçacığında çağrılır.
 * WebView her zaman 1280x2000 piksellik sabit düzende kalır (otomasyonun seçicileri masaüstü düzenine göre yazıldı);
 * izleme ekranı onu yalnız ölçekleyerek gösterir, yeniden boyutlandırmaz.
 */
interface WebMonitorHandle {
    /** WebView'ı [host] içine koyar (eski ebeveyninden çıkarır). */
    fun mount(host: ViewGroup)

    /**
     * WebView'ı [host]'a ekranı dolduracak (sabit 1280x2000 DEĞİL) boyutta koyar; yalnız kullanıcının elle gezdiği
     * oturumlar içindir ("AI sohbetleri"). Otomasyonun çalıştığı oturumda KULLANILMAZ (seçiciler masaüstü düzenine göre).
     */
    fun mountResponsive(host: ViewGroup) = mount(host)

    /** WebView'ı [host]'tan çıkarır ve ekran dışı ölçüm düzenini geri kurar. */
    fun unmount(host: ViewGroup)

    /** Sayfanın o anki adresi; yoksa null. */
    fun currentUrl(): String?
}

/** İzlenen bir oturumun anlık aşaması. */
enum class WebPhase {
    /** Sayfa yükleniyor. */
    OPENING,

    /** Sayfa hazır; bir sonraki prompt bekleniyor. */
    READY,

    /** Prompt sayfaya yazılıp gönderiliyor; AI henüz çıktı vermedi. */
    SENDING,

    /** AI çıktı veriyor (durdur düğmesi görünür ya da yanıt metni büyüyor). */
    GENERATING,

    /** Son tur tamamlandı, yanıt alındı. */
    REPLIED,

    /** Son tur sorunla bitti (kota, oturum, zaman aşımı, hata). */
    PROBLEM,

    /** Oturum kapandı; yalnız kayıt kalır, canlı sayfa yoktur. */
    CLOSED,
}

/** Bir oturumda sayfaya gönderilen tek mesaj ve (geldiyse) yanıtı. */
data class WebTurn(
    val number: Int,
    /** Sayfaya yazılan metin; çok uzunsa [WebMonitor.PROMPT_CAP] karaktere kısaltılır. */
    val prompt: String,
    /** Promptun gerçek uzunluğu. */
    val promptChars: Int,
    val sentAtMs: Long,
    /** true: mesaj gönderilmedi, yalnız sohbetteki son yanıt okundu (seri mod kurtarma). */
    val peek: Boolean = false,
    val replyPreview: String? = null,
    val replyChars: Int = 0,
    val fileCount: Int = 0,
    /** [WebReply.Status] adı (ve varsa neden); tur bitmediyse null. */
    val status: String? = null,
    val doneAtMs: Long? = null,
) {
    val isDone: Boolean get() = doneAtMs != null
}

/** İzleme ekranında görünen bir web oturumu. */
data class WebMonitorEntry(
    val id: Long,
    val accountId: Long,
    val accountName: String,
    val provider: WebProvider,
    val openedAtMs: Long,
    val phase: WebPhase = WebPhase.OPENING,
    /** Son [WebMonitor.TURN_CAP] tur, eskiden yeniye. */
    val turns: List<WebTurn> = emptyList(),
    /** Üretim sürerken sayfadaki son AI mesajının uzunluğu. */
    val liveChars: Int = 0,
    /** Üretim sürerken son AI mesajının sonu. */
    val liveTail: String = "",
    /** [liveChars] en son ne zaman değişti (yanıt takıldı mı anlamak için). */
    val lastGrowthAtMs: Long = openedAtMs,
    val closedAtMs: Long? = null,
    /** Sayfa giriş ekranında açıldı: kayıtlı oturum düşmüş (çerezler kabul edilmedi). */
    val needsLogin: Boolean = false,
) {
    val isLive: Boolean get() = closedAtMs == null
    val lastTurn: WebTurn? get() = turns.lastOrNull()

    /** Son tur gönderildi ama yanıtı henüz bitmedi. */
    val awaitingReply: Boolean get() = isLive && lastTurn?.isDone == false

    /** Yanıt beklenirken çıktının en son büyümesinden bu yana geçen süre. */
    fun idleMs(nowMs: Long): Long = (nowMs - lastGrowthAtMs).coerceAtLeast(0L)

    /** Yanıt beklenirken [STALL_MS]'dir çıktı büyümediyse true (AI takılmış ya da sayfa donmuş olabilir). */
    fun looksStalled(nowMs: Long): Boolean = awaitingReply && idleMs(nowMs) >= STALL_MS

    companion object {
        const val STALL_MS = 60_000L
    }
}

/**
 * Açık web oturumlarının kaydı: kullanıcı "Canlı web" ekranından istediği an bakıp promptun sayfaya girip girmediğini,
 * AI'ın çıktı verip vermediğini görebilsin diye. Hem akış (run) hem seri mod aynı sürücüyü kullandığından
 * ikisi de burada görünür. Hiçbir şey diske yazılmaz; uygulama kapanınca kayıt gider.
 */
class WebMonitor(
    private val now: () -> Long = System::currentTimeMillis,
    private val keepClosed: Int = DEFAULT_KEEP_CLOSED,
) {
    private val seq = AtomicLong(0)
    private val handles = ConcurrentHashMap<Long, WebMonitorHandle>()
    private val state = MutableStateFlow<List<WebMonitorEntry>>(emptyList())

    /** En yeni başta; canlı oturumlar kapananlardan önce gelir. */
    val entries: StateFlow<List<WebMonitorEntry>> = state.asStateFlow()

    /** Yeni oturum kaydeder ve kimliğini döndürür. */
    fun open(accountId: Long, accountName: String, provider: WebProvider, handle: WebMonitorHandle): Long {
        val id = seq.incrementAndGet()
        handles[id] = handle
        val entry = WebMonitorEntry(id, accountId, accountName, provider, openedAtMs = now(), lastGrowthAtMs = now())
        state.update { sorted(listOf(entry) + it) }
        return id
    }

    /** Sayfa yüklendi, giriş alanı çizilebilir. */
    fun ready(id: Long) = change(id) { if (it.phase == WebPhase.OPENING) it.copy(phase = WebPhase.READY) else it }

    /** Sayfa giriş ekranına düştü: oturum geçersiz. */
    fun loginNeeded(id: Long) = change(id) { it.copy(phase = WebPhase.PROBLEM, needsLogin = true) }

    /** Mesaj sayfaya yazılmak üzere ([peek] ise yalnız son yanıt okunacak). */
    fun promptSent(id: Long, prompt: String, peek: Boolean = false) = change(id) { e ->
        val t = now()
        val turn = WebTurn(
            number = (e.turns.lastOrNull()?.number ?: 0) + 1,
            prompt = prompt.take(PROMPT_CAP),
            promptChars = prompt.length,
            sentAtMs = t,
            peek = peek,
        )
        e.copy(
            phase = if (peek) WebPhase.GENERATING else WebPhase.SENDING,
            turns = (e.turns + turn).takeLast(TURN_CAP),
            liveChars = 0,
            liveTail = "",
            lastGrowthAtMs = t,
        )
    }

    /** Üretim sürerken sayfadan okunan anlık durum. */
    fun live(id: Long, generating: Boolean, chars: Int, tail: String) = change(id) { e ->
        if (e.phase != WebPhase.SENDING && e.phase != WebPhase.GENERATING) return@change e
        val grew = chars != e.liveChars
        val busy = generating || (grew && chars > 0)
        e.copy(
            phase = if (busy) WebPhase.GENERATING else e.phase,
            liveChars = chars,
            liveTail = tail.takeLast(TAIL_CAP),
            lastGrowthAtMs = if (grew) now() else e.lastGrowthAtMs,
        )
    }

    /** Son tur bitti. */
    fun replied(id: Long, reply: WebReply) = change(id) { e ->
        val last = e.turns.lastOrNull() ?: return@change e.copy(phase = phaseOf(reply))
        val done = last.copy(
            replyPreview = preview(reply.text).takeIf { it.isNotEmpty() },
            replyChars = reply.text.length,
            fileCount = reply.fileUrls.size,
            status = reply.status.name + (reply.reason?.let { ":$it" } ?: ""),
            doneAtMs = now(),
        )
        e.copy(phase = phaseOf(reply), turns = e.turns.dropLast(1) + done, liveChars = reply.text.length, lastGrowthAtMs = now())
    }

    /** Oturum kapandı: canlı sayfa gider, kayıt (son [keepClosed] adet) kalır. */
    fun close(id: Long) {
        handles.remove(id)
        state.update { list ->
            val closed = list.map { if (it.id == id && it.closedAtMs == null) it.copy(phase = WebPhase.CLOSED, closedAtMs = now()) else it }
            val sortedList = sorted(closed)
            val live = sortedList.filter { it.isLive }
            live + sortedList.filter { !it.isLive }.take(keepClosed)
        }
    }

    /** Kapanmış kayıtları siler. */
    fun clearClosed() = state.update { l -> l.filter { it.isLive } }

    /** Canlı sayfa tutamacı; oturum kapandıysa null. */
    fun handle(id: Long): WebMonitorHandle? = handles[id]

    private fun change(id: Long, f: (WebMonitorEntry) -> WebMonitorEntry) {
        state.update { list -> list.map { if (it.id == id && it.isLive) f(it) else it } }
    }

    private fun sorted(list: List<WebMonitorEntry>) = list.sortedWith(compareByDescending<WebMonitorEntry> { it.isLive }.thenByDescending { it.id })

    private fun phaseOf(reply: WebReply) = if (reply.status == WebReply.Status.OK) WebPhase.REPLIED else WebPhase.PROBLEM

    private fun preview(text: String): String {
        val t = text.trim()
        return if (t.length <= PREVIEW_HEAD + PREVIEW_TAIL + 20) t else t.take(PREVIEW_HEAD) + "\n…\n" + t.takeLast(PREVIEW_TAIL)
    }

    companion object {
        const val PROMPT_CAP = 12_000
        const val TURN_CAP = 6
        const val TAIL_CAP = 400
        const val PREVIEW_HEAD = 500
        const val PREVIEW_TAIL = 250
        const val DEFAULT_KEEP_CLOSED = 8
    }
}
