package com.kriptoncod.app.domain

/**
 * Tema seçimi: sistemi izle (varsayılan), koyu ya da açık.
 * Eski kayıtlar: "black"/"graphite"/"sage" koyu temalardı, bu yüzden DARK'a; bilinmeyen/boş değer SYSTEM'e düşer.
 */
enum class ThemePreference(val key: String) {
    SYSTEM("system"),
    DARK("dark"),
    LIGHT("light"),
    ;

    companion object {
        fun fromKey(key: String?): ThemePreference = when (key) {
            "dark", "black", "graphite", "sage" -> DARK
            "light" -> LIGHT
            else -> SYSTEM
        }
    }
}
