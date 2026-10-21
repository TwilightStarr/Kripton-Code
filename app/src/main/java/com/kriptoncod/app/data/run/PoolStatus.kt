package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.domain.AccountHealth
import com.kriptoncod.app.domain.AccountStatus

/**
 * Hesap havuzunun kullanıcıya gösterilen anlık görünümü. [AccountSelector] ile AYNI kuralları kullanır
 * (kota, devre kesici, sağlayıcı beklemesi, sıralama); yalnız "neden havuzda değil" ve "kaçıncı sırada"
 * sorularını yanıtlar. Durumsuz ve saftır: veritabanına, saate ya da ağa dokunmaz.
 */
object PoolStatus {
    enum class State {
        /** Havuzda ve birincil: işler önce bunlara gider. */
        ACTIVE,

        /** Havuzda ama yedek: yalnız birincillerin hiçbiri kullanılamazken devreye girer. */
        STANDBY,

        /** Sağlayıcı sınırı (429) nedeniyle bekliyor. */
        RESTING_PROVIDER,

        /** Günlük istek sınırı doldu. */
        RESTING_QUOTA,

        /** Art arda arıza yüzünden devre kesici açık. */
        RESTING_BREAKER,

        /** Anahtar reddedildi ya da hata: kullanıcı/sağlık kontrolü düzeltene kadar seçilmez. */
        ERROR,

        /** Kullanıcı kapattı. */
        DISABLED,
    }

    /**
     * @property untilMs dinlenen hesabın havuza döneceği zaman (epoch ms); diğer durumlarda null.
     * @property used24h kayan 24 saatte kotaya sayılan istek sayısı.
     * @property dailyLimit hesabın günlük sınırı; sınırsızsa null.
     * @property unhealthy son pencerede başarı oranı düşük: hesap sıranın sonuna itilmiş demektir.
     * @property order havuzdaki seçilme sırası (1 = ilk denenecek); havuzda değilse null.
     */
    data class Entry(
        val accountId: Long,
        val state: State,
        val untilMs: Long?,
        val used24h: Int,
        val dailyLimit: Int?,
        val unhealthy: Boolean,
        val order: Int?,
    ) {
        val inPool: Boolean get() = state == State.ACTIVE || state == State.STANDBY
        val resting: Boolean
            get() = state == State.RESTING_PROVIDER || state == State.RESTING_QUOTA || state == State.RESTING_BREAKER
    }

    data class Summary(val active: Int, val standby: Int, val resting: Int, val error: Int)

    private data class Block(val state: State, val until: Long)

    fun resolve(accounts: List<AiAccount>, health: Map<Long, AccountHealth>, t: Long): Map<Long, Entry> {
        val blocks = HashMap<Long, Block?>()
        for (a in accounts) {
            if (!a.enabled || a.status == AccountStatus.ERROR) continue
            blocks[a.id] = blockOf(a, health[a.id], t)
        }
        val inPool = accounts.filter { it.enabled && it.status != AccountStatus.ERROR && blocks[it.id] == null }
        val orderOf = inPool.sortedWith(AccountSelector.ordering(health, t))
            .mapIndexed { index, a -> a.id to index + 1 }
            .toMap()

        return accounts.associate { a ->
            val h = health[a.id]
            val block = blocks[a.id]
            val state = when {
                !a.enabled -> State.DISABLED
                a.status == AccountStatus.ERROR -> State.ERROR
                block != null -> block.state
                a.isBackup -> State.STANDBY
                else -> State.ACTIVE
            }
            a.id to Entry(
                accountId = a.id,
                state = state,
                untilMs = block?.until,
                used24h = h?.requestTimes.orEmpty().count { it > t - AccountSelector.DAY_MS },
                dailyLimit = a.dailyLimit?.takeIf { it > 0 },
                unhealthy = AccountSelector.healthScore(h, t) < AccountSelector.UNHEALTHY_BELOW,
                order = orderOf[a.id],
            )
        }
    }

    fun summarize(entries: Collection<Entry>): Summary = Summary(
        active = entries.count { it.state == State.ACTIVE },
        standby = entries.count { it.state == State.STANDBY },
        resting = entries.count { it.resting },
        error = entries.count { it.state == State.ERROR },
    )

    /** Engelin nedeni: birden çoksa en geç açılan olan; eşitlikte sağlayıcı > kota > devre kesici. */
    private fun blockOf(account: AiAccount, health: AccountHealth?, t: Long): Block? {
        val providerWait = if (account.status == AccountStatus.WAITING) account.waitUntil?.takeIf { it > t } else null
        return listOfNotNull(
            providerWait?.let { Block(State.RESTING_PROVIDER, it) },
            AccountSelector.quotaResetAt(account, health, t)?.let { Block(State.RESTING_QUOTA, it) },
            AccountSelector.breakerReopenAt(health, t)?.let { Block(State.RESTING_BREAKER, it) },
        ).maxByOrNull { it.until }
    }
}
