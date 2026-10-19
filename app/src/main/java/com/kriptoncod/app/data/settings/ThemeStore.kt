package com.kriptoncod.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kriptoncod.app.domain.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Sistem/Açık/Koyu seçimi + CI deneme sayısı. DataStore yazmaları atomiktir. */
class ThemeStore(private val context: Context) {

    val preference: Flow<ThemePreference> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { ThemePreference.fromKey(it[THEME_KEY]) }

    suspend fun set(value: ThemePreference) {
        context.settingsDataStore.edit {
            it[SCHEMA_KEY] = SCHEMA_VERSION
            it[THEME_KEY] = value.key
        }
    }

    // ---- CI deneme sayısı (1-10, varsayılan 3) ----
    val ciMaxAttemptsFlow: Flow<Int> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[CI_MAX_ATTEMPTS] ?: DEFAULT_CI_ATTEMPTS }

    suspend fun setCiMaxAttempts(value: Int) {
        context.settingsDataStore.edit { it[CI_MAX_ATTEMPTS] = value.coerceIn(1, 10) }
    }

    // ---- Bildirimler (varsayılan açık) ----
    val notifyFlow: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[NOTIFY] ?: true }

    suspend fun setNotify(value: Boolean) {
        context.settingsDataStore.edit { it[NOTIFY] = value }
    }

    // ---- Animasyonlar (varsayılan açık) ----
    val animationsFlow: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[ANIMATIONS] ?: true }

    suspend fun setAnimations(value: Boolean) {
        context.settingsDataStore.edit { it[ANIMATIONS] = value }
    }

    // ---- Arka plan aurası + dokunma dalgaları + kutlama efektleri (varsayılan açık; ana anahtar kapalıysa etkisiz) ----
    val effectsFlow: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[EFFECTS] ?: true }

    suspend fun setEffects(value: Boolean) {
        context.settingsDataStore.edit { it[EFFECTS] = value }
    }

    // ---- Logo sürprizleri / easter egg (varsayılan açık; ana anahtar kapalıysa etkisiz) ----
    val eggsFlow: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[EGGS] ?: true }

    suspend fun setEggs(value: Boolean) {
        context.settingsDataStore.edit { it[EGGS] = value }
    }

    // ---- Web hesabı "hesap kapatılabilir" uyarısı (bir kez onaylanır) ----
    val webWarningAccepted: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[WEB_WARNING_ACCEPTED] ?: false }

    suspend fun setWebWarningAccepted(value: Boolean) {
        context.settingsDataStore.edit { it[WEB_WARNING_ACCEPTED] = value }
    }

    private companion object {
        val WEB_WARNING_ACCEPTED = booleanPreferencesKey("web_warning_accepted")
        val ANIMATIONS = booleanPreferencesKey("animations")
        val EFFECTS = booleanPreferencesKey("animation_effects")
        val EGGS = booleanPreferencesKey("animation_eggs")
        val NOTIFY = booleanPreferencesKey("notify_events")
        const val SCHEMA_VERSION = 1
        const val DEFAULT_CI_ATTEMPTS = 3
        val SCHEMA_KEY = intPreferencesKey("schema")
        val THEME_KEY = stringPreferencesKey("theme")
        val CI_MAX_ATTEMPTS = intPreferencesKey("ci_max_attempts")
    }
}
