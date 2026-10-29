package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.web.WebOutputCheck
import com.kriptoncod.app.data.web.ZipBlocks

/**
 * Bir AI yanıtını "işe yarayan" kısma indirger. Sıra her zaman şudur:
 *  1. ham yanıt ÖNCE diske yazılır (bkz. [com.kriptoncod.app.data.files.RawReplyLog]),
 *  2. buradan yalnız dosya blokları alınır (sohbet sözü, düşünme/araç gürültüsü ve aynı yolun eski sürümleri elenir),
 *  3. elenen bloklar varsa [Result.archive] (TÜM kod blokları, birebir) saklanır; böylece kod hiçbir koşulda kaybolmaz.
 *
 * Bu nesne yalnız saf metin işler; dosyaya dokunmaz.
 */
object OutputTriage {

    class Result(
        /** Çalışmaya giren, yinelenenleri ayıklanmış metin (dosya bloğu yoksa yanıtın kendisi). */
        val useful: String,
        /** Ham yanıttaki TÜM kod blokları birebir (elenenler dahil); blok yoksa boş. */
        val archive: String,
        val rawChars: Int,
        val usefulChars: Int,
        val rawBlocks: Int,
        val keptBlocks: Int,
    ) {
        /** Çalışma metninden çıkan (eski sürüm, yinelenen, güvenli olmayan yollu) blok sayısı; bunlar [archive]'dedir. */
        val droppedBlocks: Int get() = (rawBlocks - keptBlocks).coerceAtLeast(0)

        /** Çalışma metninin ham yanıta göre boyutu (0..1); gürültünün ne kadar atıldığını gösterir. */
        val ratio: Double get() = if (rawChars == 0) 1.0 else usefulChars.toDouble() / rawChars
    }

    fun triage(raw: String): Result {
        val a = CodeBlockParser.analyze(raw)
        if (a.blocks.isEmpty()) {
            val t = raw.trim()
            return Result(t, "", raw.length, t.length, 0, 0)
        }
        val useful = WebOutputCheck.canonical(raw)
        val kept = CodeBlockParser.analyze(useful).blocks.size
        return Result(useful, codeOnly(raw), raw.length, useful.length, a.blocks.size, kept)
    }

    /** [raw] içindeki bütün kod blokları, içerikleri değiştirilmeden; aralarındaki yazı atılır. Blok yoksa boş. */
    fun codeOnly(raw: String): String = CodeBlockParser.analyze(raw).blocks.joinToString("\n\n") { b ->
        ZipBlocks.block(b.path ?: (b.language ?: "text"), b.content)
    }

    /** [raw]'daki blok sayısı. */
    fun blockCount(raw: String): Int = CodeBlockParser.analyze(raw).blocks.size
}
