package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.providers.AccountEndpoints
import com.kriptoncod.app.data.providers.ApiError
import com.kriptoncod.app.data.providers.ApiResult
import com.kriptoncod.app.data.providers.ProviderEndpoint
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AuthType
import kotlinx.coroutines.flow.first

/**
 * Hesapların anahtarını kota harcamayan doğrulama isteğiyle (bkz. ModelService.validate) yoklar:
 *  - geçerli anahtar + hesap ERROR ise → READY (sağlayıcı kesintisi/yanlış alarm sonrası kendiliğinden toparlanır),
 *  - anahtar reddedilirse (401/403) → ERROR (iş gelmeden önce havuzdan çıkar),
 *  - 429, ağ ve sunucu hataları durumu DEĞİŞTİRMEZ: bunlar anahtarın bozuk olduğunu göstermez.
 *
 * Yalnız API anahtarlı ve proxy hesapları yoklanır. Google/e-posta/Kimi oturumları yenilenebilir belirteçlerdir,
 * web oturumları anahtar değil çerezdir; bunların süresi dolmuş görünmesi hesabı bozuk saymak için yeterli değildir.
 */
class AccountHealthChecker(
    private val accounts: AiAccountRepository,
    private val credentials: CredentialProvider,
    private val validate: suspend (ProviderEndpoint, String) -> ApiResult<Unit>,
    private val resolveEndpoint: (AiAccount) -> ProviderEndpoint? = AccountEndpoints::resolve,
    /**
     * Web oturumlu hesap için kayıtlı (boş olmayan) oturum var mı. null: web hesapları yoklanmaz (eski davranış).
     * Çerezlerin sunucuda süresi dolup dolmadığı çevrimdışı bilinemez; o durum bir iş giriş ekranıyla karşılaşınca
     * ([WebReply.Status.NEEDS_LOGIN]) hesabı ERROR yapar. Burada yalnız oturumu HİÇ olmayan hesap yakalanır.
     */
    private val hasWebSession: (suspend (accountId: Long) -> Boolean)? = null,
) {
    enum class Verdict {
        /** Anahtar geçerli, durum değişmedi. */
        OK,

        /** Anahtar geçerli ve ERROR olan hesap yeniden READY yapıldı. */
        RECOVERED,

        /** Anahtar reddedildi; hesap ERROR yapıldı (zaten ERROR ise öyle kaldı). */
        KEY_INVALID,

        /** Sağlayıcı sınırı döndü; anahtar büyük olasılıkla geçerli, durum değişmedi. */
        RATE_LIMITED,

        /** Ağ/sunucu/yanıt hatası: karar verilemedi, durum değişmedi. */
        UNREACHABLE,

        /** Yoklanmadı (desteklenmeyen tür, kimlik bilgisi/uç nokta yok). */
        SKIPPED,
    }

    data class Result(val accountId: Long, val verdict: Verdict)

    /** Açık hesapların hepsini sırayla yoklar. */
    suspend fun checkAll(): List<Result> = accounts.observeAll().first().filter { it.enabled }.map { check(it) }

    suspend fun check(account: AiAccount): Result {
        if (account.authType == AuthType.WEB_SESSION && hasWebSession != null) {
            if (!hasWebSession.invoke(account.id)) {
                if (account.status != AccountStatus.ERROR) accounts.markError(account.id)
                return Result(account.id, Verdict.KEY_INVALID)
            }
            return Result(account.id, Verdict.OK)
        }
        if (account.authType != AuthType.API_KEY && account.authType != AuthType.PROXY) return skipped(account)
        val endpoint = resolveEndpoint(account) ?: return skipped(account)
        val credential = credentials.credentialFor(account)
        if (credential.isNullOrBlank()) return skipped(account)

        val verdict = when (val r = validate(endpoint, credential)) {
            is ApiResult.Ok -> if (account.status == AccountStatus.ERROR) {
                accounts.markReady(account.id)
                Verdict.RECOVERED
            } else {
                Verdict.OK
            }

            is ApiResult.Fail -> when (r.error) {
                ApiError.INVALID_KEY -> {
                    if (account.status != AccountStatus.ERROR) accounts.markError(account.id)
                    Verdict.KEY_INVALID
                }

                ApiError.RATE_LIMITED -> Verdict.RATE_LIMITED

                else -> Verdict.UNREACHABLE
            }
        }
        return Result(account.id, verdict)
    }

    private fun skipped(account: AiAccount) = Result(account.id, Verdict.SKIPPED)
}
