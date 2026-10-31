package com.kriptoncod.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kriptoncod.app.domain.ModeCodec
import com.kriptoncod.app.domain.ModeProfile
import com.kriptoncod.app.domain.ModeRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.modesDataStore: DataStore<Preferences> by preferencesDataStore(name = "modes")

/** Mod listesinin görünümü: hazır modlar + kullanıcı modları ve etkin olan. */
data class ModeState(
    val custom: List<ModeProfile>,
    val activeId: String,
) {
    val all: List<ModeProfile> get() = ModeProfile.BUILT_INS + custom

    /** Etkin mod; silinmiş/bilinmeyen kimlik varsayılana (Dengeli) düşer, çökmez. */
    val active: ModeProfile get() = all.firstOrNull { it.id == activeId } ?: ModeProfile.BALANCED
}

/**
 * Modları DataStore'da saklar. Hazır modlar koda gömülüdür (kaydedilmez, sürümle iyileşir); yalnız kullanıcı
 * modları ve etkin kimlik yazılır. Her yazma atomiktir. Etkin mod değişince [ModeRuntime] güncellenir.
 */
class ModeStore(private val context: Context) {

    val state: Flow<ModeState> = context.modesDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            ModeState(
                custom = ModeCodec.decodeList(prefs[CUSTOM]),
                activeId = prefs[ACTIVE] ?: ModeProfile.ID_BALANCED,
            )
        }
        .distinctUntilChanged()

    /** Etkin modu çalışma zamanına yükler (uygulama açılışında ve her değişimde AppContainer çağırır). */
    suspend fun syncRuntime() {
        ModeRuntime.set(state.first().active)
    }

    /** Etkin modu seç (tek dokunuş). Bilinmeyen kimlik yok sayılır. */
    suspend fun activate(id: String) {
        val current = state.first()
        if (current.all.none { it.id == id }) return
        context.modesDataStore.edit { it[ACTIVE] = id }
    }

    /** Yeni ya da düzenlenmiş kullanıcı modunu kaydeder; hazır mod değiştirilemez. @return kaydedilen mod. */
    suspend fun save(profile: ModeProfile): ModeProfile? {
        if (ModeProfile.builtInById(profile.id) != null) return null
        val clean = profile.copy(builtIn = false).coerced()
        var saved: ModeProfile? = null
        context.modesDataStore.edit { prefs ->
            val list = ModeCodec.decodeList(prefs[CUSTOM]).toMutableList()
            val i = list.indexOfFirst { it.id == clean.id }
            if (i >= 0) {
                list[i] = clean
            } else {
                if (list.size >= ModeProfile.Limits.MAX_CUSTOM) return@edit
                list += clean
            }
            prefs[CUSTOM] = ModeCodec.encodeList(list)
            saved = clean
        }
        return saved
    }

    /** Herhangi bir modun (hazır dahil) düzenlenebilir kopyasını oluşturur ve etkin yapmadan kaydeder. */
    suspend fun duplicate(source: ModeProfile, newName: String): ModeProfile? = save(source.copy(id = ModeProfile.newId(), name = newName, builtIn = false))

    /** Kullanıcı modunu siler; etkinse Dengeli'ye dönülür. Hazır mod silinemez. */
    suspend fun delete(id: String) {
        if (ModeProfile.builtInById(id) != null) return
        context.modesDataStore.edit { prefs ->
            val list = ModeCodec.decodeList(prefs[CUSTOM]).filterNot { it.id == id }
            prefs[CUSTOM] = ModeCodec.encodeList(list)
            if (prefs[ACTIVE] == id) prefs[ACTIVE] = ModeProfile.ID_BALANCED
        }
    }

    /** Dışa aktarılmış metni içe alır; hepsi yeni kimlikle eklenir. @return eklenen sayısı, geçersizse null. */
    suspend fun importText(text: String): Int? {
        val result = ModeCodec.import(text)
        if (result !is ModeCodec.ImportResult.Ok) return null
        var added = 0
        context.modesDataStore.edit { prefs ->
            val list = ModeCodec.decodeList(prefs[CUSTOM]).toMutableList()
            for (m in result.modes) {
                if (list.size >= ModeProfile.Limits.MAX_CUSTOM) break
                list += m
                added++
            }
            prefs[CUSTOM] = ModeCodec.encodeList(list)
        }
        return added
    }

    /** Tüm kullanıcı modlarını siler ve Dengeli'ye döner. */
    suspend fun resetAll() {
        context.modesDataStore.edit {
            it.remove(CUSTOM)
            it[ACTIVE] = ModeProfile.ID_BALANCED
        }
    }

    private companion object {
        val CUSTOM = stringPreferencesKey("custom_modes")
        val ACTIVE = stringPreferencesKey("active_mode")
    }
}
