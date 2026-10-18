package com.kriptoncod.app.data.google

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.googleAuthDataStore by preferencesDataStore(name = "google_auth_settings")

/**
 * OAuth istemci kimliğini DataStore'da saklar.
 * Token/anahtar değil; güvenlik sınıflandırması: yapılandırma verisi.
 *
 * BuildConfig.GOOGLE_OAUTH_CLIENT_ID tanımlıysa varsayılan olarak kullanılır.
 */
class GoogleAuthSettings(private val context: Context) {

    private val clientIdKey = stringPreferencesKey("oauth_client_id")

    /** İstemci kimliği akışı; boş veya boşluk-only ise null. */
    val clientIdFlow: Flow<String?> = context.googleAuthDataStore.data
        .map { prefs -> prefs[clientIdKey]?.takeIf { it.isNotBlank() } }

    suspend fun setClientId(id: String) {
        context.googleAuthDataStore.edit { prefs ->
            if (id.isBlank()) {
                prefs.remove(clientIdKey)
            } else {
                prefs[clientIdKey] = id.trim()
            }
        }
    }

    suspend fun clearClientId() {
        context.googleAuthDataStore.edit { it.remove(clientIdKey) }
    }
}
