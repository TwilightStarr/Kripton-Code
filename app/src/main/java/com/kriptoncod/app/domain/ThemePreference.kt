package com.kriptoncod.app.domain

/** Sade, koyu temalar; varsayılan siyah-beyaz. Eski kayıtlar ("system", "light", "dark") Siyah-Beyaz'a düşer. */
enum class ThemePreference(val key: String) {
    BLACK("black"),
    GRAPHITE("graphite"),
    SAGE("sage"),
    ;

    companion object {
        fun fromKey(key: String?): ThemePreference = entries.firstOrNull { it.key == key } ?: BLACK
    }
}
