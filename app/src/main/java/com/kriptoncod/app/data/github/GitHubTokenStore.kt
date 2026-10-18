package com.kriptoncod.app.data.github

import com.kriptoncod.app.data.vault.KeyVault

/**
 * GitHub kişisel erişim belirtecini (PAT) KeyVault'ta şifreli saklar.
 * Room, log, history dosyası veya zip'e asla yazılmaz.
 *
 * keyId: `github-token` ("ai-account-" önekini kullanmaz; yetim temizliğinden etkilenmez).
 */
class GitHubTokenStore(private val vault: KeyVault) {

    /** Boşlukları kırpıp kaydeder; biçim makul değilse [IllegalArgumentException] (mesaj token içermez). */
    suspend fun save(token: String) {
        val t = token.trim()
        require(isPlausible(t)) { "Geçersiz GitHub token biçimi" }
        vault.put(KEY_ID, t)
    }

    suspend fun get(): String? = vault.get(KEY_ID)

    suspend fun has(): Boolean = vault.contains(KEY_ID)

    suspend fun clear() {
        vault.delete(KEY_ID)
    }

    companion object {
        const val KEY_ID = "github-token"

        /** Yalnız biçim denetimi (uzunluk, boşluk yok, yazdırılabilir ASCII). Geçerlilik GitHub'da sınanır. */
        fun isPlausible(token: String): Boolean = token.length in 20..255 && token.all { it.code in 0x21..0x7E }
    }
}
