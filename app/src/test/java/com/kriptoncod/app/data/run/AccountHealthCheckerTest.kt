package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.db.KriptonDatabase
import com.kriptoncod.app.data.providers.ApiError
import com.kriptoncod.app.data.providers.ApiResult
import com.kriptoncod.app.data.providers.ProviderEndpoint
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.data.run.AccountHealthChecker.Verdict
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.testutil.FakeKeyVault
import com.kriptoncod.app.testutil.inMemoryDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountHealthCheckerTest {
    private lateinit var db: KriptonDatabase
    private lateinit var accounts: AiAccountRepository
    private var credential: String? = "key"
    private var validation: ApiResult<Unit> = ApiResult.Ok(Unit)
    private var validateCalls = 0

    private val credentials = object : CredentialProvider {
        override suspend fun credentialFor(account: AiAccount): String? = credential
    }

    private val checker
        get() = AccountHealthChecker(
            accounts,
            credentials,
            validate = { _: ProviderEndpoint, _: String ->
                validateCalls++
                validation
            },
        )

    @Before fun setUp() {
        db = inMemoryDb()
        accounts = AiAccountRepository(db.aiAccountDao(), FakeKeyVault())
    }

    @After fun tearDown() {
        db.close()
    }

    private suspend fun add(
        authType: AuthType = AuthType.API_KEY,
        status: AccountStatus = AccountStatus.READY,
        enabled: Boolean = true,
    ): Long {
        val id = accounts.add(
            AiAccount(providerId = "openai", displayName = "x", authType = authType, modelId = "m", powerRank = 1, enabled = enabled),
            "sk",
        )
        when (status) {
            AccountStatus.ERROR -> accounts.markError(id)
            AccountStatus.WAITING -> accounts.markWaiting(id, Long.MAX_VALUE)
            AccountStatus.READY -> Unit
        }
        return id
    }

    @Test fun validKeyRecoversErrorAccount(): Unit = runBlocking {
        val id = add(status = AccountStatus.ERROR)
        val result = checker.checkAll().single()
        assertEquals(Verdict.RECOVERED, result.verdict)
        assertEquals(AccountStatus.READY, accounts.get(id)!!.status)
    }

    @Test fun validKeyLeavesHealthyAccountAlone(): Unit = runBlocking {
        val id = add()
        assertEquals(Verdict.OK, checker.checkAll().single().verdict)
        assertEquals(AccountStatus.READY, accounts.get(id)!!.status)
    }

    @Test fun validKeyDoesNotClearProviderWait(): Unit = runBlocking {
        val id = add(status = AccountStatus.WAITING)
        assertEquals(Verdict.OK, checker.checkAll().single().verdict)
        assertEquals(AccountStatus.WAITING, accounts.get(id)!!.status)
    }

    @Test fun rejectedKeyMarksAccountError(): Unit = runBlocking {
        val id = add()
        validation = ApiResult.Fail(ApiError.INVALID_KEY, 401)
        assertEquals(Verdict.KEY_INVALID, checker.checkAll().single().verdict)
        assertEquals(AccountStatus.ERROR, accounts.get(id)!!.status)
    }

    @Test fun rateLimitNetworkAndServerErrorsChangeNothing(): Unit = runBlocking {
        val id = add()
        validation = ApiResult.Fail(ApiError.RATE_LIMITED, 429)
        assertEquals(Verdict.RATE_LIMITED, checker.checkAll().single().verdict)
        for (e in listOf(ApiError.NETWORK, ApiError.SERVER, ApiError.NOT_FOUND, ApiError.BAD_RESPONSE)) {
            validation = ApiResult.Fail(e)
            assertEquals(Verdict.UNREACHABLE, checker.checkAll().single().verdict)
        }
        assertEquals(AccountStatus.READY, accounts.get(id)!!.status)
    }

    @Test fun sessionBasedAccountsAreSkippedWithoutNetworkCall(): Unit = runBlocking {
        for (type in listOf(AuthType.GOOGLE, AuthType.EMAIL, AuthType.KIMI, AuthType.WEB_SESSION)) add(authType = type)
        val results = checker.checkAll()
        assertEquals(4, results.size)
        assertEquals(setOf(Verdict.SKIPPED), results.map { it.verdict }.toSet())
        assertEquals(0, validateCalls)
    }

    @Test fun missingCredentialIsSkippedNotMarkedBroken(): Unit = runBlocking {
        val id = add()
        credential = null
        assertEquals(Verdict.SKIPPED, checker.checkAll().single().verdict)
        assertEquals(AccountStatus.READY, accounts.get(id)!!.status)
        assertEquals(0, validateCalls)
    }

    @Test fun disabledAccountsAreNotChecked(): Unit = runBlocking {
        add(enabled = false)
        assertEquals(emptyList<AccountHealthChecker.Result>(), checker.checkAll())
        assertEquals(0, validateCalls)
    }
}
