package com.kriptoncod.app.data.google

import com.kriptoncod.app.data.vault.KeyVault
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Google erişim belirtecini ve bitiş zamanını KeyVault'ta şifreli saklar.
 * Room, log veya zip'e asla yazılmaz.
 *
 * keyId formatı: `google-token-<accountId>`
 */
class GoogleTokenStore(private val vault: KeyVault) {

    @Serializable
    private data class TokenEntry(val accessToken: String, val expiresAtMs: Long)

    private fun keyId(accountId: Long) = "google-token-$accountId"

    suspend fun save(accountId: Long, accessToken: String, expiresAtMs: Long) {
        val entry = TokenEntry(accessToken, expiresAtMs)
        vault.put(keyId(accountId), Json.encodeToString(entry))
    }

    /**
     * Geçerli token bilgisini döndürür.
     * [bufferMs] kadar öncesinde süresi dolmuş sayılır (varsayılan 5 dakika).
     */
    suspend fun getValid(accountId: Long, bufferMs: Long = BUFFER_MS): TokenInfo? {
        val raw = vault.get(keyId(accountId)) ?: return null
        return runCatching {
            val entry = Json.decodeFromString<TokenEntry>(raw)
            if (System.currentTimeMillis() + bufferMs >= entry.expiresAtMs) {
                null
            } else {
                TokenInfo(entry.accessToken, entry.expiresAtMs)
            }
        }.getOrNull()
    }

    suspend fun clear(accountId: Long) {
        vault.delete(keyId(accountId))
    }

    suspend fun hasToken(accountId: Long): Boolean = vault.contains(keyId(accountId))

    companion object {
        private const val BUFFER_MS = 5L * 60 * 1000 // 5 dakika güvenlik payı
    }
}

data class TokenInfo(val accessToken: String, val expiresAtMs: Long)
