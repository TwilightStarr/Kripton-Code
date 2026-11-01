package com.kriptoncod.app.data.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Uygulama içi dil seçimi (Sistem / Türkçe / English). SharedPreferences senkron okunur: dil, arayüz kurulmadan
 * önce (attachBaseContext) uygulanmak zorundadır. Android 13+ ta sistemin "uygulama dili" ayarı da çalışmaya devam eder;
 * burada "Sistem" seçiliyse hiçbir şey ezilmez.
 */
object AppLanguage {
    const val SYSTEM = "system"
    const val TR = "tr"
    const val EN = "en"
    private const val PREFS = "app_language"
    private const val KEY = "lang"

    fun get(context: Context): String {
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM)
        return if (v == TR || v == EN) v else SYSTEM
    }

    fun set(context: Context, value: String) {
        val clean = if (value == TR || value == EN) value else SYSTEM
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, clean).apply()
    }

    /** Seçili dile göre ayarlanmış bağlam; "Sistem" ise aynen döner. */
    fun wrap(base: Context): Context {
        val lang = get(base)
        if (lang == SYSTEM) return base
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
