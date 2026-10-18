package com.kriptoncod.app.data.kimi

import com.kriptoncod.app.data.vault.KeyVault
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KimiTokenStore(private val vault: KeyVault) {

    @Serializable
    private data class TokenEntry(
        val sessionToken: String,
        val refreshToken: String?,
        val expiresAtMs: Long,
    )

    private fun keyId(accountId: Long) = "kimi-token-$accountId"

    suspend fun save(accountId: Long, sessionToken: String, expiresAtMs: Long, refreshToken: String? = null) {
        vault.put(keyId(accountId), Json.encodeToString(TokenEntry(sessionToken, refreshToken, expiresAtMs)))
    }

    suspend fun getValid(accountId: Long, bufferMs: Long = BUFFER_MS): KimiTokenInfo? {
        val entry = getAny(accountId) ?: return null
        if (System.currentTimeMillis() + bufferMs >= entry.expiresAtMs) return null
        return entry
    }

    suspend fun getAny(accountId: Long): KimiTokenInfo? {
        val raw = vault.get(keyId(accountId)) ?: return null
        return runCatching {
            val e = Json.decodeFromString<TokenEntry>(raw)
            KimiTokenInfo(e.sessionToken, e.refreshToken, e.expiresAtMs)
        }.getOrNull()
    }

    suspend fun hasToken(accountId: Long): Boolean = vault.contains(keyId(accountId))

    suspend fun clear(accountId: Long) {
        vault.delete(keyId(accountId))
    }

    companion object {
        private const val BUFFER_MS = 5L * 60 * 1000
    }
}

data class KimiTokenInfo(
    val sessionToken: String,
    val refreshToken: String?,
    val expiresAtMs: Long,
)
