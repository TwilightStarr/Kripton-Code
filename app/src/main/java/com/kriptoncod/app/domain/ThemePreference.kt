package com.kriptoncod.app.domain

/** Sade, koyu temalar. Eski kayıtlar ("system", "light", "dark") varsayılan Grafit'e düşer. */
enum class ThemePreference(val key: String) {
    GRAPHITE("graphite"),
    BLACK("black"),
    SAGE("sage");

    companion object {
        fun fromKey(key: String?): ThemePreference =
            entries.firstOrNull { it.key == key } ?: GRAPHITE
    }
}
