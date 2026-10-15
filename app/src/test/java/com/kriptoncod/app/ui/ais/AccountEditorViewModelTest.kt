package com.kriptoncod.app.ui.ais

import com.kriptoncod.app.R
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.db.KriptonDatabase
import com.kriptoncod.app.data.providers.ApiError
import com.kriptoncod.app.data.providers.ApiResult
import com.kriptoncod.app.data.providers.ModelRanking
import com.kriptoncod.app.data.providers.ProviderCatalog
import com.kriptoncod.app.data.providers.ProviderEndpoint
import com.kriptoncod.app.data.providers.RankedModel
import com.kriptoncod.app.data.providers.RemoteModel
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.data.vault.KeyVault
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.testutil.FakeKeyVault
import com.kriptoncod.app.testutil.awaitValue
import com.kriptoncod.app.testutil.inMemoryDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FlakyPutVault(private val inner: FakeKeyVault) : KeyVault by inner {
    @Volatile var failPut = false
    override suspend fun put(keyId: String, secret: String) {
        if (failPut) throw java.io.IOException("kasa hatası")
        inner.put(keyId, secret)
    }
}

private class FakeDiscover(var next: ApiResult<ModelRanking>) {
    @Volatile var calls = 0
    @Volatile var lastKey: String? = null
    suspend operator fun invoke(endpoint: ProviderEndpoint, key: String): ApiResult<ModelRanking> {
        calls++
        lastKey = key
        return next
    }
}

private fun ranking(vararg ids: String, recommended: String = ids.first()): ModelRanking = ModelRanking(
    models = ids.mapIndexed { i, id -> RankedModel(RemoteModel(id), 90 - i * 10) },
    recommended = RemoteModel(recommended),
)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AccountEditorViewModelTest {
    private lateinit var db: KriptonDatabase
    private lateinit var fake: FakeKeyVault
    private lateinit var vault: FlakyPutVault
    private lateinit var repo: AiAccountRepository
    private lateinit var discover: FakeDiscover

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = inMemoryDb()
        fake = FakeKeyVault()
        vault = FlakyPutVault(fake)
        repo = AiAccountRepository(db.aiAccountDao(), vault)
        discover = FakeDiscover(ApiResult.Ok(ranking("model-a", "model-b", "model-c")))
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun addVm(): AccountEditorViewModel =
        AccountEditorViewModel(null, repo, { e, k -> discover(e, k) }, { it.id })

    private fun editVm(id: Long): AccountEditorViewModel =
        AccountEditorViewModel(id, repo, { e, k -> discover(e, k) }, { it.id })

    private fun verifiedVm(provider: String = "groq", key: String = "gsk_abc123"): AccountEditorViewModel {
        val vm = addVm()
        vm.selectProvider(provider)
        vm.onKeyChanged(key)
        vm.verify()
        vm.state.awaitValue { it.verified }
        return vm
    }

    private fun seed(status: AccountStatus = AccountStatus.READY, model: String = "model-b"): Long = runBlocking {
        val id = repo.add(
            AiAccount(
                providerId = "groq", displayName = "groq 1", authType = AuthType.API_KEY,
                modelId = model, powerRank = 5, status = status,
            ),
            "stored-key",
        )
        if (status == AccountStatus.ERROR) repo.markError(id)
        id
    }

    @Test fun startsEmptyAndCannotVerifyOrSave() {
        val s = addVm().state.value
        assertNull(s.providerId)
        assertFalse(s.canVerify)
        assertFalse(s.canSave)
    }

    @Test fun customProviderIsNotSelectable() {
        assertTrue(AccountEditorViewModel.SELECTABLE_PROVIDERS.none { it.isCustom })
        val vm = addVm()
        vm.selectProvider(ProviderCatalog.CUSTOM_ID)
        assertNull(vm.state.value.providerId)
    }

    @Test fun pastedKeyIsSanitizedAndNeverPrinted() {
        val vm = addVm()
        vm.selectProvider("groq")
        vm.onKeyChanged("  gsk_abc \n123\t")
        val s = vm.state.value
        assertEquals("gsk_abc123", s.apiKey.value)
        assertTrue(s.canVerify)
        assertFalse(s.toString().contains("gsk_abc123"))
    }

    @Test fun verifyPreselectsRecommendedAtTop() {
        discover.next = ApiResult.Ok(ranking("model-a", "model-b", "model-c", recommended = "model-b"))
        val s = verifiedVm().state.value
        assertEquals("model-b", s.models.first().id)
        assertTrue(s.models.first().recommended)
        assertEquals("model-b", s.selectedModelId)
        assertEquals(1, s.models.count { it.recommended })
        assertEquals("gsk_abc123", discover.lastKey)
        assertTrue(s.canSave)
    }

    @Test fun pricingLabels() {
        discover.next = ApiResult.Ok(ranking("x/a:free", "x/b"))
        val or = verifiedVm("openrouter", "sk-or-1").state.value
        assertEquals(Pricing.FREE, or.models.first { it.id == "x/a:free" }.pricing)
        assertEquals(Pricing.PAID, or.models.first { it.id == "x/b" }.pricing)
        assertEquals(Pricing.FREE_TIER, verifiedVm("groq").state.value.models.first().pricing)
        assertEquals(Pricing.PAID, verifiedVm("deepseek", "sk-ds").state.value.models.first().pricing)
    }

    @Test fun userCanChangeSelectedModel() {
        val vm = verifiedVm()
        vm.selectModel("model-c")
        assertEquals("model-c", vm.state.value.selectedModelId)
        vm.selectModel("yok-boyle-model")
        assertEquals("model-c", vm.state.value.selectedModelId)
    }

    @Test fun invalidKeyShowsMessageWithoutRetry() {
        discover.next = ApiResult.Fail(ApiError.INVALID_KEY, 401)
        val vm = addVm()
        vm.selectProvider("groq")
        vm.onKeyChanged("bad")
        vm.verify()
        val s = vm.state.awaitValue { it.error != null }
        assertEquals(R.string.err_invalid_key, s.error!!.messageRes)
        assertNull(s.error!!.retry)
        assertFalse(s.verified)
        assertFalse(s.canSave)
        assertFalse(s.verifying)
    }

    @Test fun networkErrorCanBeRetried() {
        discover.next = ApiResult.Fail(ApiError.NETWORK)
        val vm = addVm()
        vm.selectProvider("groq")
        vm.onKeyChanged("k")
        vm.verify()
        val failed = vm.state.awaitValue { it.error != null }
        assertEquals(R.string.err_network, failed.error!!.messageRes)
        assertEquals(RetryAction.VERIFY, failed.error!!.retry)

        discover.next = ApiResult.Ok(ranking("model-a"))
        vm.retry()
        val ok = vm.state.awaitValue { it.verified }
        assertNull(ok.error)
        assertEquals(2, discover.calls)
    }

    @Test fun rateLimitIsRetryableAndEmptyModelListIsAnError() {
        discover.next = ApiResult.Fail(ApiError.RATE_LIMITED, 429)
        val vm = addVm()
        vm.selectProvider("groq")
        vm.onKeyChanged("k")
        vm.verify()
        assertEquals(RetryAction.VERIFY, vm.state.awaitValue { it.error != null }.error!!.retry)

        discover.next = ApiResult.Ok(ModelRanking(emptyList(), null))
        vm.retry()
        val s = vm.state.awaitValue { it.error?.messageRes == R.string.err_no_models }
        assertFalse(s.verified)
    }

    @Test fun changingKeyInvalidatesVerification() {
        val vm = verifiedVm()
        vm.onKeyChanged("baska-anahtar")
        val s = vm.state.value
        assertFalse(s.verified)
        assertTrue(s.models.isEmpty())
        assertNull(s.selectedModelId)
        assertFalse(s.canSave)
    }

    @Test fun changingProviderResetsKeyAndModels() {
        val vm = verifiedVm()
        vm.selectProvider("mistral")
        val s = vm.state.value
        assertEquals("mistral", s.providerId)
        assertTrue(s.apiKey.isEmpty)
        assertFalse(s.verified)
    }

    @Test fun emptyClipboardShowsMessage() {
        val vm = addVm()
        vm.onPasteEmpty()
        assertEquals(R.string.err_clipboard_empty, vm.state.value.error!!.messageRes)
    }

    @Test fun saveWritesKeyToVaultAndAccountToRoom() {
        val vm = verifiedVm()
        vm.selectModel("model-b")
        vm.save()
        val s = vm.state.awaitValue { it.saved }
        assertTrue(s.apiKey.isEmpty) // anahtar bellekten de silinir
        val accounts = runBlocking { repo.observeAllOnce() }
        val a = accounts.single()
        assertEquals("groq", a.providerId)
        assertEquals("model-b", a.modelId)
        assertEquals(80, a.powerRank)
        assertEquals("groq 1", a.displayName)
        assertTrue(a.enabled)
        assertEquals("gsk_abc123", fake.store[AiAccount.vaultKeyId(a.id)])
    }

    @Test fun sameProviderAllowsManyAccountsWithUniqueNames() {
        repeat(3) {
            val vm = verifiedVm(key = "key-$it")
            vm.save()
            vm.state.awaitValue { s -> s.saved }
        }
        val names = runBlocking { repo.observeAllOnce() }.map { it.displayName }.sorted()
        assertEquals(listOf("groq 1", "groq 2", "groq 3"), names)
    }

    @Test fun saveFailureRollsBackAndRetryWorks() {
        val vm = verifiedVm()
        vault.failPut = true
        vm.save()
        val failed = vm.state.awaitValue { it.error != null }
        assertEquals(R.string.err_save_failed, failed.error!!.messageRes)
        assertEquals(RetryAction.SAVE, failed.error!!.retry)
        assertFalse(failed.saving)
        assertTrue(runBlocking { repo.observeAllOnce() }.isEmpty()) // yarım hesap kalmadı

        vault.failPut = false
        vm.retry()
        vm.state.awaitValue { it.saved }
        assertEquals(1, runBlocking { repo.observeAllOnce() }.size)
    }

    @Test fun editAutoVerifiesWithStoredKeyAndPreselectsCurrentModel() {
        val id = seed(model = "model-b")
        val vm = editVm(id)
        val s = vm.state.awaitValue { it.verified }
        assertEquals("stored-key", discover.lastKey)
        assertEquals("model-b", s.selectedModelId) // önerilen model-a olsa da mevcut seçim korunur
        assertEquals("groq", s.providerId)
        assertTrue(s.isEdit)
    }

    @Test fun editSavesNewModelAndRecoversErrorStatus() {
        val id = seed(status = AccountStatus.ERROR)
        val vm = editVm(id)
        vm.state.awaitValue { it.verified }
        vm.selectModel("model-c")
        vm.save()
        vm.state.awaitValue { it.saved }
        val a = runBlocking { repo.get(id)!! }
        assertEquals("model-c", a.modelId)
        assertEquals(70, a.powerRank)
        assertEquals(AccountStatus.READY, a.status)
        assertEquals("stored-key", fake.store[AiAccount.vaultKeyId(id)]) // anahtar değişmedi
    }

    @Test fun editWithNewKeyReplacesSecret() {
        val id = seed()
        val vm = editVm(id)
        vm.state.awaitValue { it.verified }
        vm.onKeyChanged("yeni-anahtar")
        vm.verify()
        vm.state.awaitValue { it.verified }
        assertEquals("yeni-anahtar", discover.lastKey)
        vm.save()
        vm.state.awaitValue { it.saved }
        assertEquals("yeni-anahtar", fake.store[AiAccount.vaultKeyId(id)])
    }

    @Test fun editWithUnreadableKeyAsksForNewOne() {
        val id = seed()
        fake.store.remove(AiAccount.vaultKeyId(id))
        val s = editVm(id).state.awaitValue { it.error != null }
        assertEquals(R.string.err_key_unreadable, s.error!!.messageRes)
        assertEquals(0, discover.calls)
    }

    @Test fun editMissingAccountShowsError() {
        val s = editVm(999).state.awaitValue { it.error != null }
        assertEquals(R.string.err_account_missing, s.error!!.messageRes)
        assertFalse(s.loading)
    }
}

private suspend fun AiAccountRepository.observeAllOnce(): List<AiAccount> =
    observeAll().first()
