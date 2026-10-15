package com.kriptoncod.app.data.vault

/** API anahtarı / token kasası. Değerler yalnız şifreli saklanır; Room, log ve zip'e girmez. */
interface KeyVault {
    suspend fun put(keyId: String, secret: String)

    /** Kayıt yoksa null; kayıt var ama çözülemiyorsa [KeyVaultException]. */
    suspend fun get(keyId: String): String?

    suspend fun contains(keyId: String): Boolean

    suspend fun delete(keyId: String)

    suspend fun listIds(): List<String>
}

class KeyVaultException(message: String, cause: Throwable? = null) : Exception(message, cause)

private val KEY_ID_REGEX = Regex("[A-Za-z0-9._-]{1,64}")

internal fun requireValidKeyId(keyId: String) {
    require(KEY_ID_REGEX.matches(keyId) && keyId != "." && keyId != "..") { "Geçersiz keyId" }
}
