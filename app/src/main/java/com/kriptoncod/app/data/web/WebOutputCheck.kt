package com.kriptoncod.app.data.web

import com.kriptoncod.app.data.run.CodeBlockParser
import com.kriptoncod.app.data.run.PathSafety
import com.kriptoncod.app.data.run.PlaceholderDetector

/**
 * Web sohbetinden gelen metni dosya bloklarına göre inceler ve standart biçime getirir:
 *  - yolsuz kod blokları (yalnız dil etiketli) kaybolmaz: `unnamed/part_N.<uzantı>` yoluna alınır,
 *  - aynı yola yazan bloklardan eksiksiz olan (yer tutucusuz/uzun) kazanır,
 *  - yer tutucu içeren ve hiç blok içermeyen yanıtlar onarım turu için işaretlenir.
 */
object WebOutputCheck {

    data class Inspection(
        val noBlocks: Boolean,
        val placeholderFiles: List<String>,
        val pathlessCount: Int,
    ) {
        val ok: Boolean get() = !noBlocks && placeholderFiles.isEmpty()
    }

    fun inspect(text: String): Inspection {
        val a = CodeBlockParser.analyze(text)
        val pathed = a.blocks.filter { it.path != null }
        val pathless = a.blocks.count { it.path == null }
        if (a.blocks.isEmpty()) return Inspection(true, emptyList(), 0)
        val files = (pathed.mapNotNull { b -> b.path?.let { CodeBlockParser.Block(it, b.content) } })
        val ph = PlaceholderDetector.find(files).map { it.path }.distinct()
        return Inspection(false, ph, pathless)
    }

    /** Dosya bloğu hiç yoksa metin olduğu gibi döner (belge/düz metin çıktısı). */
    fun canonical(text: String): String {
        val a = CodeBlockParser.analyze(text)
        if (a.blocks.isEmpty()) return text
        val byPath = linkedMapOf<String, String>()
        var n = 0
        for (b in a.blocks) {
            val raw = b.path ?: run {
                n++
                "unnamed/part_$n.${extFor(b.language)}"
            }
            val path = (PathSafety.validate(raw) as? PathSafety.Result.Safe)?.normalized ?: continue
            val old = byPath[path]
            byPath[path] = if (old == null || better(b.content, old)) b.content else old
        }
        if (byPath.isEmpty()) return text
        return byPath.entries.joinToString("\n\n") { (p, c) -> ZipBlocks.block(p, c) }
    }

    /** [candidate], [old]'dan daha iyi mi: yer tutucusuzluk önce, sonra uzunluk. */
    private fun better(candidate: String, old: String): Boolean {
        val cp = placeholderCount(candidate)
        val op = placeholderCount(old)
        if (cp != op) return cp < op
        return candidate.length >= old.length || candidate.length * 10 >= old.length * 6
    }

    private fun placeholderCount(content: String) = PlaceholderDetector.find(listOf(CodeBlockParser.Block("x.kt", content))).size

    fun repairPrompt(i: Inspection): String = when {
        i.noBlocks ->
            "Your last reply had no file blocks. Return EVERY file again, each as its own fenced code block whose info " +
                "string is the full relative file path (for example ```src/main/App.kt). Complete files only, no explanations."

        else ->
            "These files were shortened with placeholders (\"...\", \"rest of the code\"): " +
                i.placeholderFiles.take(12).joinToString(", ") +
                ". Send ONLY those files again, each complete from the first to the last line, as ```full/path fenced blocks. " +
                "No placeholders, no omitted parts, no explanations."
    }

    private fun extFor(lang: String?): String = when (lang?.lowercase()) {
        "kotlin", "kt" -> "kt"
        "kts" -> "kts"
        "java" -> "java"
        "python", "py" -> "py"
        "javascript", "js" -> "js"
        "typescript", "ts" -> "ts"
        "tsx" -> "tsx"
        "jsx" -> "jsx"
        "json", "jsonc" -> "json"
        "xml" -> "xml"
        "html" -> "html"
        "css" -> "css"
        "scss" -> "scss"
        "bash", "sh", "shell", "zsh" -> "sh"
        "yaml", "yml" -> "yml"
        "toml" -> "toml"
        "sql" -> "sql"
        "dart" -> "dart"
        "swift" -> "swift"
        "go", "golang" -> "go"
        "rust", "rs" -> "rs"
        "c" -> "c"
        "cpp" -> "cpp"
        "csharp", "cs" -> "cs"
        "php" -> "php"
        "ruby", "rb" -> "rb"
        "gradle", "groovy" -> "gradle"
        "markdown", "md" -> "md"
        "properties" -> "properties"
        else -> "txt"
    }
}
