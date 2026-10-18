package com.kriptoncod.app.data.repo

import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.db.AiAccountDao
import com.kriptoncod.app.data.vault.KeyVault
import com.kriptoncod.app.domain.AccountStatus
import kotlinx.coroutines.flow.Flow

/** Hesap meta verisi Room'da, anahtar/token yalnız KeyVault'ta. */
class AiAccountRepository(
    private val dao: AiAccountDao,
    private val vault: KeyVault,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun observeAll(): Flow<List<AiAccount>> = dao.observeAll()
    fun observeEnabled(): Flow<List<AiAccount>> = dao.observeEnabled()
    fun observe(id: Long): Flow<AiAccount?> = dao.observe(id)
    suspend fun get(id: Long): AiAccount? = dao.get(id)

    /** Hesabı ekler; [secret] varsa kasaya yazar. Kasa hatasında satır geri alınır. */
    suspend fun add(account: AiAccount, secret: String?): Long {
        val id = dao.insert(account.copy(id = 0))
        if (secret != null) {
            try {
                vault.put(AiAccount.vaultKeyId(id), secret)
            } catch (t: Throwable) {
                dao.deleteById(id)
                throw t
            }
        }
        return id
    }

    suspend fun update(account: AiAccount) {
        dao.update(account)
    }

    suspend fun setSecret(id: Long, secret: String) = vault.put(AiAccount.vaultKeyId(id), secret)
    suspend fun getSecret(id: Long): String? = vault.get(AiAccount.vaultKeyId(id))
    suspend fun hasSecret(id: Long): Boolean = vault.contains(AiAccount.vaultKeyId(id))

    suspend fun delete(id: Long) {
        vault.delete(AiAccount.vaultKeyId(id))
        // Web oturumu (çerezler) varsa onu da sil; yoksa sessizce geçer.
        runCatching { vault.delete("web-session-$id") }
        dao.deleteById(id)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled)
    }

    suspend fun markReady(id: Long) {
        dao.setStatus(id, AccountStatus.READY, null)
    }

    suspend fun markWaiting(id: Long, until: Long) {
        dao.setStatus(id, AccountStatus.WAITING, until)
    }

    suspend fun markError(id: Long) {
        dao.setStatus(id, AccountStatus.ERROR, null)
    }

    suspend fun releaseExpiredWaits(): Int = dao.releaseExpiredWaits(now(), AccountStatus.WAITING, AccountStatus.READY)

    /** Hesabı olmayan (yetim) kasa kayıtlarını siler; silinen sayısını döndürür. */
    suspend fun purgeOrphanSecrets(): Int {
        // Sıra önemli: önce kasa listelenir. Listede görünen kayıt, satırı eklendikten SONRA yazılmıştır;
        // bu yüzden allIds() sonradan alınırsa yeni eklenen hesabın anahtarı yanlışlıkla silinmez.
        val stored = vault.listIds().filter { it.startsWith(ACCOUNT_PREFIX) }
        val valid = dao.allIds().map { AiAccount.vaultKeyId(it) }.toSet()
        val orphans = stored.filter { it !in valid }
        orphans.forEach { vault.delete(it) }
        return orphans.size
    }

    private companion object {
        val ACCOUNT_PREFIX = AiAccount.vaultKeyId(0).removeSuffix("0")
    }
}
