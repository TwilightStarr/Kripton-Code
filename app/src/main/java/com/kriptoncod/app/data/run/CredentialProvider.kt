package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.email.EmailTokenStore
import com.kriptoncod.app.data.google.GoogleTokenStore
import com.kriptoncod.app.data.kimi.KimiTokenStore
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.data.vault.KeyVaultException
import com.kriptoncod.app.domain.AuthType

/** Hesabın istek atmaya hazır kimlik bilgisini verir; yoksa/süresi dolmuşsa null. */
interface CredentialProvider {
    suspend fun credentialFor(account: AiAccount): String?
}

/** API anahtarı, Google erişim belirteci, e-posta oturumu veya Kimi oturumunu KeyVault'tan okur. */
class VaultCredentialProvider(
    private val accounts: AiAccountRepository,
    private val googleTokens: GoogleTokenStore,
    private val emailTokens: EmailTokenStore,
    private val kimiTokens: KimiTokenStore,
) : CredentialProvider {

    override suspend fun credentialFor(account: AiAccount): String? = try {
        when (account.authType) {
            AuthType.API_KEY, AuthType.PROXY -> accounts.getSecret(account.id)
            AuthType.GOOGLE -> googleTokens.getValid(account.id)?.accessToken
            AuthType.EMAIL -> emailTokens.getValid(account.id)?.sessionToken
            AuthType.KIMI -> kimiTokens.getValid(account.id)?.sessionToken
        }
    } catch (_: KeyVaultException) {
        null // çözülemeyen kayıt = kimlik bilgisi yok; çağıran hesabı ERROR yapar
    }
}
