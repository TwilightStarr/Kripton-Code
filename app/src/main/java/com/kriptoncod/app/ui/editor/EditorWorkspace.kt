package com.kriptoncod.app.ui.editor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kriptoncod.app.data.files.AtomicFiles
import java.io.File
import java.util.Locale

/** Editör sekmesinin çalışma alanı klasörü ve dosya adı yardımcıları (saf Kotlin; birim testle doğrulanır). */
object EditorWorkspace {
    private const val DIR = "editor"

    /** Uygulamaya özel, her zaman yazılabilir çalışma klasörü. */
    fun root(context: Context): File = File(context.filesDir, DIR).also { it.mkdirs() }

    /** Dosya/klasör adı geçerli mi: boş değil, yol ayırıcı/NUL yok, "." ya da ".." değil, en çok 120 karakter. */
    fun validName(name: String): Boolean {
        val n = name.trim()
        return n.isNotEmpty() && n.length <= 120 && n != "." && n != ".." &&
            n.none { it == '/' || it == '\\' || it == '\u0000' }
    }

    /** [dir] içinde var olanı ezmeyen ad: "a.txt" doluysa "a (1).txt", "a (2).txt"… */
    fun uniqueFile(dir: File, name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            val f = File(dir, "$stem ($i)$ext")
            if (!f.exists()) return f
            i++
        }
    }

    /** Klasör içeriği: önce klasörler, sonra dosyalar; ada göre (büyük/küçük harf duyarsız); yarım kalmış geçici dosyalar gizli. */
    fun list(dir: File): List<File> {
        val all = dir.listFiles().orEmpty().filter { !(it.isFile && it.name.endsWith(AtomicFiles.TMP_SUFFIX)) }
        return all.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
    }

    /** [f], [root] içinde mi (sembolik bağlantı/".." kaçışı yok)? */
    fun isInside(root: File, f: File): Boolean {
        val r = runCatching { root.canonicalFile }.getOrNull() ?: return false
        val c = runCatching { f.canonicalFile }.getOrNull() ?: return false
        return c.path == r.path || c.path.startsWith(r.path + File.separator)
    }

    /** Kullanıcıya gösterilen yol: kök "/" ile başlar. */
    fun display(root: File, f: File): String {
        val rel = runCatching { f.relativeTo(root).path }.getOrDefault(f.name)
        return if (rel.isEmpty() || rel == ".") "/" else "/" + rel.replace(File.separatorChar, '/')
    }
}

/**
 * Editör sekmesindeki açık dosyalar ve etkin sekme. Süreç boyunca tek kopya durur (ekran yeniden kurulsa da sekmeler gitmez);
 * uygulama yeniden başlayınca SharedPreferences'tan geri yüklenir.
 */
object EditorSession {
    private const val PREFS = "code_editor_session"
    val open = mutableStateListOf<String>()
    var active: String? by mutableStateOf(null)
        private set
    private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun ensureLoaded(context: Context): Boolean {
        if (loaded) return true
        loaded = true
        val p = prefs(context)
        val paths = p.getString("open", "").orEmpty().split('\n').filter { it.isNotBlank() && File(it).isFile }.distinct()
        open.clear()
        open.addAll(paths)
        active = p.getString("active", null)?.takeIf { it in open } ?: open.lastOrNull()
        return true
    }

    private fun persist(context: Context) {
        prefs(context).edit().putString("open", open.joinToString("\n")).putString("active", active).apply()
    }

    /** Dosyayı sekme olarak açar (zaten açıksa ona geçer). */
    fun openFile(context: Context, path: String) {
        if (path !in open) open.add(path)
        active = path
        persist(context)
    }

    fun select(context: Context, path: String) {
        if (path in open) {
            active = path
            persist(context)
        }
    }

    /** Sekmeyi kapatır; etkin sekmeyse komşusuna geçilir. Taslağı da siler. */
    fun close(context: Context, path: String) {
        val i = open.indexOf(path)
        if (i < 0) return
        open.removeAt(i)
        EditorDrafts.remove(path)
        if (active == path) active = open.getOrNull(i.coerceAtMost(open.size - 1))
        persist(context)
    }

    /** Dosya ya da klasör silindi/taşındı: altındaki açık sekmeler kapanır. */
    fun forget(context: Context, path: String) {
        val victims = open.filter { it == path || it.startsWith(path + File.separator) }
        victims.forEach { close(context, it) }
    }

    /** Yeniden adlandırma: açık sekme yolu ve taslağı yeni yola taşınır. */
    fun renamed(context: Context, from: String, to: String) {
        val i = open.indexOf(from)
        if (i < 0) return
        open[i] = to
        EditorDrafts.get(from)?.let {
            EditorDrafts.put(to, it)
            EditorDrafts.remove(from)
        }
        if (active == from) active = to
        persist(context)
    }
}

/** Sekme değişince ya da ekran kapanınca kaydedilmemiş metnin bellekte saklandığı taslaklar. */
object EditorDrafts {
    /** [base]: taslağın alındığı andaki disk değişiklik zamanı (dosya sonradan değiştiyse kaydederken uyarılır). */
    class Draft(val text: String, val bom: Boolean, val base: Long)

    private val map = mutableStateMapOf<String, Draft>()

    fun get(path: String): Draft? = map[path]

    fun put(path: String, draft: Draft) {
        map[path] = draft
    }

    fun remove(path: String) {
        map.remove(path)
    }

    fun has(path: String): Boolean = map.containsKey(path)
}
