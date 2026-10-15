package com.kriptoncod.app.data.vault

import com.kriptoncod.app.testutil.SoftwareKeyProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Saf JVM testi: Keystore yerine yazılım AES anahtarı; şifreleme/dosya mantığı aynıdır. */
class AndroidKeystoreKeyVaultTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun vault(dir: File = File(tmp.root, "vault"), provider: SecretKeyProvider = SoftwareKeyProvider()) =
        AndroidKeystoreKeyVault(dir, provider)

    @Test fun putGetRoundTripAndDelete() = runBlocking {
        val v = vault()
        assertNull(v.get("acc-1"))
        v.put("acc-1", "sk-çok-gizli-ğüşiöç")
        assertEquals("sk-çok-gizli-ğüşiöç", v.get("acc-1"))
        assertTrue(v.contains("acc-1"))
        v.delete("acc-1")
        assertFalse(v.contains("acc-1"))
        assertNull(v.get("acc-1"))
    }

    @Test fun overwriteReplacesValueAndListIds() = runBlocking {
        val v = vault()
        v.put("b", "1"); v.put("a", "1"); v.put("a", "2")
        assertEquals("2", v.get("a"))
        assertEquals(listOf("a", "b"), v.listIds())
    }

    @Test fun fileNeverContainsPlaintext() = runBlocking {
        val v = vault()
        val secret = "SUPER-SECRET-TOKEN-123456"
        v.put("acc-1", secret)
        val bytes = File(tmp.root, "vault").listFiles()!!.single().readBytes()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains(secret))
    }

    @Test fun sameSecretEncryptsDifferentlyEachTime() = runBlocking {
        val v = vault()
        v.put("x", "same")
        val first = File(tmp.root, "vault/x.blob").readBytes()
        v.put("x", "same")
        val second = File(tmp.root, "vault/x.blob").readBytes()
        assertFalse(first.contentEquals(second)) // rastgele IV
    }

    @Test fun tamperedBlobIsRejected() = runBlocking {
        val v = vault()
        v.put("acc-1", "secret")
        val f = File(tmp.root, "vault/acc-1.blob")
        val b = f.readBytes()
        b[b.size - 1] = (b[b.size - 1].toInt() xor 0x01).toByte()
        f.writeBytes(b)
        expectFailure { v.get("acc-1") }
    }

    @Test fun truncatedOrGarbageBlobIsRejected() = runBlocking {
        val v = vault()
        v.put("acc-1", "secret")
        val f = File(tmp.root, "vault/acc-1.blob")
        f.writeBytes(f.readBytes().copyOf(5))
        expectFailure { v.get("acc-1") }
        f.writeBytes(ByteArray(0))
        expectFailure { v.get("acc-1") }
        f.writeBytes(ByteArray(64) { 7 })
        expectFailure { v.get("acc-1") }
    }

    @Test fun blobCopiedToAnotherIdCannotBeDecrypted() = runBlocking {
        val v = vault()
        v.put("acc-1", "secret")
        File(tmp.root, "vault/acc-1.blob").copyTo(File(tmp.root, "vault/acc-2.blob"))
        expectFailure { v.get("acc-2") }
    }

    @Test fun differentMasterKeyCannotDecrypt() = runBlocking {
        val dir = File(tmp.root, "vault")
        vault(dir, SoftwareKeyProvider()).put("acc-1", "secret")
        expectFailure { vault(dir, SoftwareKeyProvider()).get("acc-1") }
    }

    @Test fun invalidKeyIdsAreRejected() = runBlocking {
        val v = vault()
        listOf("", "../x", "a/b", "a b", "..", ".", "a".repeat(65)).forEach { bad ->
            try {
                v.put(bad, "s")
                fail("'$bad' reddedilmeliydi")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test fun concurrentPutsLeaveOneIntactValueAndNoTempFiles() = runBlocking {
        val v = vault()
        val values = (1..40).map { "değer-$it" }
        values.map { s -> async(Dispatchers.IO) { v.put("acc", s) } }.awaitAll()
        assertTrue(v.get("acc") in values)
        assertTrue(File(tmp.root, "vault").listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun concurrentPutsToDifferentIdsAllReadable() = runBlocking {
        val v = vault()
        (1..40).map { i -> async(Dispatchers.IO) { v.put("k$i", "v$i") } }.awaitAll()
        (1..40).forEach { assertEquals("v$it", v.get("k$it")) }
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        try {
            block()
            fail("KeyVaultException bekleniyordu")
        } catch (_: KeyVaultException) {
        }
    }
}
