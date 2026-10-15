package com.kriptoncod.app.data.email

import com.kriptoncod.app.data.vault.KeyVault
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Session token'ını KeyVault'ta şifreli saklar.
 * Room, log veya zip'e asla yazılmaz.
 *
 * keyId formatı: `email-token-<accountId>`
 */
class EmailTokenStore(private val vault: KeyVault) {

    @Serializable
    private data class TokenEntry(val sessionToken: String, val expiresAtMs: Long)

    private fun keyId(accountId: Long) = "email-token-$accountId"

    suspend fun save(accountId: Long, sessionToken: String, expiresAtMs: Long) {
        val entry = TokenEntry(sessionToken, expiresAtMs)
        vault.put(keyId(accountId), Json.encodeToString(entry))
    }

    /**
     * Geçerli token döndürür; süresi dolmuşsa ya da kayıt yoksa null.
     * [bufferMs] kadar öncesinde süresi dolmuş sayılır (varsayılan 5 dakika).
     */
    suspend fun getValid(accountId: Long, bufferMs: Long = BUFFER_MS): EmailTokenInfo? {
        val raw = vault.get(keyId(accountId)) ?: return null
        return runCatching {
            val entry = Json.decodeFromString<TokenEntry>(raw)
            if (System.currentTimeMillis() + bufferMs >= entry.expiresAtMs) null
            else EmailTokenInfo(entry.sessionToken, entry.expiresAtMs)
        }.getOrNull()
    }

    /** Token var mı (süresi dolmuş olsa bile). */
    suspend fun hasToken(accountId: Long): Boolean = vault.contains(keyId(accountId))

    suspend fun clear(accountId: Long) { vault.delete(keyId(accountId)) }

    companion object {
        private const val BUFFER_MS = 5L * 60 * 1000 // 5 dakika güvenlik payı
    }
}

data class EmailTokenInfo(val sessionToken: String, val expiresAtMs: Long)
