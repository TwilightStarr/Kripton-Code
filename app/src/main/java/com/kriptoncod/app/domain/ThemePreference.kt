package com.kriptoncod.app.domain

enum class ThemePreference(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemePreference =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}
