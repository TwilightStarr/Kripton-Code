package com.kriptoncod.app.data.web

import com.kriptoncod.app.data.vault.KeyVault
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Bir hesabın web oturumu: köke göre çerez metinleri ve (gerekirse) localStorage anahtarları.
 * Şifre hiç saklanmaz; yalnız oturum çerezleri KeyVault'ta şifreli durur.
 */
@Serializable
data class WebSession(
    /** Köken (https://claude.ai) -> "ad=değer; ad2=değer2" çerez metni. */
    val cookies: Map<String, String> = emptyMap(),
    val localStorage: Map<String, String> = emptyMap(),
    val savedAtMs: Long = 0L,
) {
    val isEmpty: Boolean get() = cookies.values.all { it.isBlank() } && localStorage.isEmpty()
}

class WebSessionStore(private val vault: KeyVault, private val now: () -> Long = System::currentTimeMillis) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun keyId(accountId: Long) = "web-session-$accountId"

    suspend fun save(accountId: Long, session: WebSession) {
        vault.put(keyId(accountId), json.encodeToString(session.copy(savedAtMs = now())))
    }

    suspend fun load(accountId: Long): WebSession? {
        val raw = vault.get(keyId(accountId)) ?: return null
        return runCatching { json.decodeFromString<WebSession>(raw) }.getOrNull()
    }

    suspend fun has(accountId: Long): Boolean = vault.contains(keyId(accountId))

    suspend fun clear(accountId: Long) = vault.delete(keyId(accountId))
}

/** "a=b; c=d" biçimli çerez metnini çiftlere ayırır (bozuk parçalar atlanır). */
object CookieText {
    fun parse(cookieHeader: String?): List<Pair<String, String>> =
        cookieHeader.orEmpty().split(';').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
        }.filter { it.first.isNotEmpty() }
}
