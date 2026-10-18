package com.kriptoncod.app.data.vault

import com.kriptoncod.app.data.files.AtomicFiles
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Her anahtar `<vaultDir>/<keyId>.blob` dosyasında AES-GCM ile şifreli tutulur.
 * Ana anahtar [keyProvider]'dadır (üretimde Android Keystore). Yazma atomiktir.
 */
class AndroidKeystoreKeyVault(
    private val vaultDir: File,
    keyProvider: SecretKeyProvider = AndroidKeystoreKeyProvider(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : KeyVault {
    private val crypto = AesGcmCrypto(keyProvider)
    private val mutex = Mutex()

    override suspend fun put(keyId: String, secret: String) {
        requireValidKeyId(keyId)
        withLock {
            val blob = crypto.encrypt(secret.toByteArray(Charsets.UTF_8), keyId.toByteArray(Charsets.UTF_8))
            AtomicFiles.write(fileFor(keyId), blob)
        }
    }

    override suspend fun get(keyId: String): String? {
        requireValidKeyId(keyId)
        return withLock {
            val file = fileFor(keyId)
            if (!file.isFile) return@withLock null
            val blob = try {
                file.readBytes()
            } catch (e: IOException) {
                throw KeyVaultException("Kasa dosyası okunamadı", e)
            }
            String(crypto.decrypt(blob, keyId.toByteArray(Charsets.UTF_8)), Charsets.UTF_8)
        }
    }

    override suspend fun contains(keyId: String): Boolean {
        requireValidKeyId(keyId)
        return withLock { fileFor(keyId).isFile }
    }

    override suspend fun delete(keyId: String) {
        requireValidKeyId(keyId)
        withLock { fileFor(keyId).delete() }
    }

    override suspend fun listIds(): List<String> = withLock {
        vaultDir.listFiles { f -> f.isFile && f.name.endsWith(EXT) }
            ?.map { it.name.removeSuffix(EXT) }
            ?.sorted()
            .orEmpty()
    }

    private fun fileFor(keyId: String) = File(vaultDir, keyId + EXT)

    private suspend fun <T> withLock(block: () -> T): T = mutex.withLock { withContext(dispatcher) { block() } }

    private companion object {
        const val EXT = ".blob"
    }
}
