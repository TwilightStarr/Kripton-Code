package com.kriptoncod.app.launcher.search

import java.text.Normalizer
import java.util.Locale

/**
 * Arama için metni "katlar": büyük/küçük harf, aksan ve Türkçe harf farkı kalkar ("Şarj" ile "sarj", "ISPARTA" ile "isparta"
 * aynı sayılır). Saf mantıktır; birim testle doğrulanır.
 */
object TextFold {
    fun fold(text: String): String {
        val mapped = buildString(text.length) {
            for (c in text) {
                append(
                    when (c) {
                        'İ', 'I', 'ı' -> 'i'
                        'Ş', 'ş' -> 's'
                        'Ç', 'ç' -> 'c'
                        'Ğ', 'ğ' -> 'g'
                        'Ö', 'ö' -> 'o'
                        'Ü', 'ü' -> 'u'
                        else -> c
                    },
                )
            }
        }
        val decomposed = Normalizer.normalize(mapped, Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        for (c in decomposed) {
            if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) sb.append(c)
        }
        return sb.toString().lowercase(Locale.ROOT)
    }

    /** Sözcükleri ayırır (boşluk, tire, alt çizgi, nokta, eğik çizgi). */
    fun words(folded: String): List<String> = folded.split(' ', '-', '_', '.', '/', '\\').filter { it.isNotEmpty() }
}
