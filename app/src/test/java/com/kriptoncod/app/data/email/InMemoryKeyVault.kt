package com.kriptoncod.app.data.email

import com.kriptoncod.app.data.vault.KeyVault

/** Test amaçlı bellek-içi KeyVault; disk veya Keystore kullanmaz. */
class InMemoryKeyVault : KeyVault {
    private val store = mutableMapOf<String, String>()

    override suspend fun put(keyId: String, secret: String) {
        store[keyId] = secret
    }
    override suspend fun get(keyId: String): String? = store[keyId]
    override suspend fun contains(keyId: String): Boolean = keyId in store
    override suspend fun delete(keyId: String) {
        store.remove(keyId)
    }
    override suspend fun listIds(): List<String> = store.keys.toList()
}
