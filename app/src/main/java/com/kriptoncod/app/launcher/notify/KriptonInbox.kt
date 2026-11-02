package com.kriptoncod.app.launcher.notify

import android.content.Context
import com.kriptoncod.app.data.files.AtomicFiles
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Kripton'un kendi olayları (çalışma bitti, hata, hesap bekleniyor, duraklatıldı…). */
@Serializable
data class InboxItem(
    val id: Long,
    val time: Long,
    /** [KIND_DONE], [KIND_FAILED], [KIND_WAITING], [KIND_PAUSED], [KIND_INFO] */
    val kind: Int,
    val title: String,
    val text: String,
    val runId: Long = 0L,
    val read: Boolean = false,
)

const val KIND_DONE = 1
const val KIND_FAILED = 2
const val KIND_WAITING = 3
const val KIND_PAUSED = 4
const val KIND_INFO = 9

/**
 * Kripton'un KENDİ bildirim kutusu. Çalışma olayları buraya yazılır ve launcher'ın bildirim panelinde, Kripton simgesinin
 * rozetinde görünür; Android bildirim gölgesine bağlı değildir. Kalıcıdır (son [MAX] olay), yalnız cihazdadır.
 */
object KriptonInbox {
    const val MAX = 60

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var file: File? = null
    private val _items = MutableStateFlow<List<InboxItem>>(emptyList())
    val items: StateFlow<List<InboxItem>> = _items.asStateFlow()

    /** Uygulama başlarken bir kez çağrılır (tekrar çağırmak zararsızdır). */
    fun init(context: Context) {
        synchronized(lock) {
            if (file != null) return
            val f = File(context.applicationContext.filesDir, "launcher-inbox.json")
            file = f
            _items.value = load(f)
        }
    }

    private fun load(f: File): List<InboxItem> = try {
        if (!f.isFile) emptyList() else json.decodeFromString(ListSerializer(InboxItem.serializer()), f.readText()).takeLast(MAX)
    } catch (_: Exception) {
        emptyList()
    }

    private fun save(list: List<InboxItem>) {
        val f = file ?: return
        runCatching { AtomicFiles.write(f, json.encodeToString(ListSerializer(InboxItem.serializer()), list).toByteArray()) }
    }

    /**
     * Olay ekler. Aynı çalışma + aynı tür tekrar gelirse eskisini günceller (çalışma başına tek kart).
     */
    fun post(kind: Int, title: String, text: String, runId: Long = 0L, now: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val cur = _items.value
            val next = (if (runId != 0L) cur.filterNot { it.runId == runId && it.kind == kind } else cur) +
                InboxItem(id = (cur.maxOfOrNull { it.id } ?: 0L) + 1, time = now, kind = kind, title = title, text = text, runId = runId)
            val trimmed = next.takeLast(MAX)
            _items.value = trimmed
            save(trimmed)
        }
    }

    fun markAllRead() = change { list -> list.map { it.copy(read = true) } }

    fun remove(id: Long) = change { list -> list.filter { it.id != id } }

    /** Bir çalışmanın (ör. devam edince kalkan "duraklatıldı") kartını kaldırır. */
    fun removeRun(runId: Long, kind: Int) = change { list -> list.filterNot { it.runId == runId && it.kind == kind } }

    fun clear() = change { emptyList() }

    fun unread(list: List<InboxItem>): Int = list.count { !it.read }

    private fun change(f: (List<InboxItem>) -> List<InboxItem>) {
        synchronized(lock) {
            val next = f(_items.value)
            _items.value = next
            save(next)
        }
    }
}
