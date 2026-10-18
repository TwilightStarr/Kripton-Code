package com.kriptoncod.app.testutil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kriptoncod.app.data.db.KriptonDatabase
import com.kriptoncod.app.data.vault.KeyVault
import com.kriptoncod.app.data.vault.SecretKeyProvider
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Bellek içi KeyVault (Keystore gerektirmez). */
class FakeKeyVault : KeyVault {
    val store = ConcurrentHashMap<String, String>()
    override suspend fun put(keyId: String, secret: String) {
        store[keyId] = secret
    }
    override suspend fun get(keyId: String): String? = store[keyId]
    override suspend fun contains(keyId: String): Boolean = store.containsKey(keyId)
    override suspend fun delete(keyId: String) {
        store.remove(keyId)
    }
    override suspend fun listIds(): List<String> = store.keys.sorted()
}

/** JVM'de Android Keystore yoktur; aynı AES-GCM kodunu yazılım anahtarıyla sınar. */
class SoftwareKeyProvider : SecretKeyProvider {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    override fun getOrCreateKey(): SecretKey = key
}

fun inMemoryDb(): KriptonDatabase {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return Room.inMemoryDatabaseBuilder(context, KriptonDatabase::class.java)
        .allowMainThreadQueries()
        .build()
}
