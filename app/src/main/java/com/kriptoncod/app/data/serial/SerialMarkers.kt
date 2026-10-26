package com.kriptoncod.app.data.serial

import com.kriptoncod.app.data.web.OutputStyle
import com.kriptoncod.app.data.web.WebProvider

/**
 * Seri moddaki her alışverişin (prompt ya da CI düzeltmesi) başına ve sonuna AI'a yazdırılan benzersiz işaretler.
 *
 *  - BAŞLA işareti: yanıtın BU alışverişe ait olduğunu kanıtlar. Sayfa yenilenip yanıt kaybolunca sohbetteki son yanıt
 *    okunur; işaret varsa yanıt zaten gelmiştir (aynı prompt ikinci kez gönderilmez).
 *  - TAMAM işareti: yanıtın GERÇEKTEN bittiğini kanıtlar (kesilmiş yanıt işaretsiz biter).
 */
object SerialMarkers {

    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun newNonce(random: () -> Int = { (Math.random() * Int.MAX_VALUE).toInt() }): String = buildString { repeat(6) { append(ALPHABET[random().mod(ALPHABET.length)]) } }

    fun begin(nonce: String) = "KRIPTON-BASLA-$nonce"

    fun end(nonce: String) = "KRIPTON-TAMAM-$nonce"

    /** Yanıt bu alışverişe ait mi (işaretlerden biri geçiyor mu). */
    fun belongs(text: String, nonce: String): Boolean = text.contains(begin(nonce)) || text.contains(end(nonce))

    /** Bitiş işareti yanıtın SONUNDA mı. */
    fun isComplete(text: String, nonce: String): Boolean = text.takeLast(TAIL).contains(end(nonce))

    /** İşaret satırlarını (varsa etrafındaki `*`, `` ` ``, boşluk dahil) metinden çıkarır. */
    fun strip(text: String, nonce: String): String {
        val keys = listOf(begin(nonce), end(nonce))
        val kept = text.lines().filter { line ->
            val core = line.trim().trim('`', '*', '_', ' ', '#', '>')
            keys.none { core == it }
        }
        return kept.joinToString("\n").trim()
    }

    private const val TAIL = 240

    private const val FORMAT_COMMON =
        "[Kripton automation — mandatory output format]\n" +
            "1. Begin your reply with this exact line on its own: "

    /**
     * [body] promptunu sağlayıcıya uygun çıktı kuralları ve işaretlerle sarar. [expectFiles] false ise dosya kuralları eklenmez.
     * [header]: gövdenin önüne konan açıklama (ör. projenin güncel hâlinin eklendiği bilgisi); boş olabilir.
     */
    fun wrap(provider: WebProvider, header: String, body: String, nonce: String, expectFiles: Boolean): String = buildString {
        if (header.isNotBlank()) {
            append(header.trim()).append("\n\n")
        }
        append(body.trim())
        append("\n\n---\n")
        append(FORMAT_COMMON).append(begin(nonce)).append("\n")
        if (expectFiles) {
            append(fileRules(provider)).append("\n")
            append("3. When everything is completely finished, end your reply with this exact line on its own: ").append(end(nonce))
        } else {
            append("2. Answer normally; no files are required.\n")
            append("3. When your answer is completely finished, end your reply with this exact line on its own: ").append(end(nonce))
        }
        append("\nIf you are cut off I will say 'continue'.")
    }

    private fun fileRules(provider: WebProvider): String = if (provider.output == OutputStyle.ZIP_FILE) {
        "2. Deliver ALL created or changed files together as ONE downloadable .zip archive (actually create the file, do not just " +
            "describe it) with full relative paths. Complete files only: no placeholders, no '...', no omitted parts. " +
            "If you cannot create a zip, return every file as its own fenced code block whose info string is the full relative " +
            "file path (for example ```src/main/App.kt). No explanations outside the files except one short summary line."
    } else {
        "2. Return EVERY created or changed file as its own fenced code block whose info string is the full relative file path " +
            "(for example ```src/main/App.kt). Complete files only: no placeholders, no '...', no omitted parts. " +
            "No explanations outside the code blocks except one short summary line."
    }

    /** Kesilen/yarım yanıtı sürdürmek için. */
    fun continuation(nonce: String): String = "Continue exactly where you stopped. Do not repeat finished files. If you were in the middle of a file, " +
        "restart that file with the same ```path fence and write it complete, then finish the remaining files. " +
        "When everything is completely finished, end with this exact line on its own: ${end(nonce)}"

    /** Yanıt bitmiş görünüyor ama bitiş işareti yok: bitti mi diye sorulur. */
    fun finishCheck(nonce: String): String = "If your previous answer is completely finished, reply with ONLY this exact line: ${end(nonce)}\n" +
        "If something is still missing, continue exactly where you stopped (complete files only) and end with ${end(nonce)}."

    /** CI günlüğü AI'a iletilirken mesajın başına konan açıklama. [kind]: "E" hata, "W" uyarı. */
    fun fixHead(kind: String): String {
        val what = if (kind == "W") "warnings" else "errors"
        return "The GitHub Actions build for your last output reported the $what below. Fix ALL of them. " +
            "Return ONLY the files you change, each complete from the first to the last line (not diffs, no placeholders)."
    }

    /** Günlüğün kendisi; olduğu gibi (özetlenmeden) yapıştırılır. */
    fun fixBody(kind: String, log: String): String = "BUILD LOG (${if (kind == "W") "warnings" else "errors"}):\n$log"
}
