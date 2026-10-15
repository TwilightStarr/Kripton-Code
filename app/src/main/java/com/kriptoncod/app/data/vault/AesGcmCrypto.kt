package com.kriptoncod.app.data.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

fun interface SecretKeyProvider {
    fun getOrCreateKey(): SecretKey
}

/** Android Keystore içindeki dışa aktarılamaz AES-256 ana anahtar. */
class AndroidKeystoreKeyProvider(private val alias: String = DEFAULT_ALIAS) : SecretKeyProvider {
    @Synchronized
    override fun getOrCreateKey(): SecretKey {
        try {
            val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
            (ks.getKey(alias, null) as? SecretKey)?.let { return it }
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
                .apply { init(spec) }
                .generateKey()
        } catch (e: GeneralSecurityException) {
            throw KeyVaultException("Keystore anahtarı alınamadı", e)
        }
    }

    companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "kriptoncod.vault.master"
    }
}

/**
 * AES-256-GCM. Blob biçimi: [sürüm=1][ivUzunluğu][iv][şifreli+tag].
 * keyId, AAD olarak bağlanır: bir kaydın blob'u başka id altına kopyalanırsa çözülmez.
 */
class AesGcmCrypto(private val keyProvider: SecretKeyProvider) {

    fun encrypt(plain: ByteArray, aad: ByteArray): ByteArray {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreateKey()) // IV'yi sağlayıcı üretir
            cipher.updateAAD(aad)
            val iv = cipher.iv
            val ct = cipher.doFinal(plain)
            return byteArrayOf(VERSION, iv.size.toByte()) + iv + ct
        } catch (e: GeneralSecurityException) {
            throw KeyVaultException("Şifreleme başarısız", e)
        }
    }

    fun decrypt(blob: ByteArray, aad: ByteArray): ByteArray {
        if (blob.size < 2 + MIN_IV + TAG_BYTES || blob[0] != VERSION) {
            throw KeyVaultException("Bozuk veya desteklenmeyen kasa kaydı")
        }
        val ivLen = blob[1].toInt() and 0xFF
        if (ivLen < MIN_IV || blob.size < 2 + ivLen + TAG_BYTES) {
            throw KeyVaultException("Bozuk kasa kaydı")
        }
        try {
            val iv = blob.copyOfRange(2, 2 + ivLen)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyProvider.getOrCreateKey(), GCMParameterSpec(TAG_BYTES * 8, iv))
            cipher.updateAAD(aad)
            return cipher.doFinal(blob, 2 + ivLen, blob.size - 2 - ivLen)
        } catch (e: GeneralSecurityException) {
            throw KeyVaultException("Kasa kaydı çözülemedi", e)
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val MIN_IV = 12
        const val TAG_BYTES = 16
    }
}
