package com.kriptoncod.app.ui.ais

import com.kriptoncod.app.R
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.db.KriptonDatabase
import com.kriptoncod.app.data.providers.ApiError
import com.kriptoncod.app.data.providers.ApiResult
import com.kriptoncod.app.data.providers.ModelRanking
import com.kriptoncod.app.data.providers.ProviderCatalog
import com.kriptoncod.app.data.providers.ProviderEndpoint
import com.kriptoncod.app.data.providers.ProxyProvider
import com.kriptoncod.app.data.providers.UpstreamProvider
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

    @Volatile var lastEndpoint: ProviderEndpoint? = null
    suspend operator fun invoke(endpoint: ProviderEndpoint, key: String): ApiResult<ModelRanking> {
        calls++
        lastKey = key
        lastEndpoint = endpoint
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

    private fun addVm(): AccountEditorViewModel = AccountEditorViewModel(null, repo, { e, k -> discover(e, k) }, { it.id })

    private fun editVm(id: Long): AccountEditorViewModel = AccountEditorViewModel(id, repo, { e, k -> discover(e, k) }, { it.id })

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
                providerId = "groq",
                displayName = "groq 1",
                authType = AuthType.API_KEY,
                modelId = model,
                powerRank = 5,
                status = status,
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

    // ---- Proxy (CLIProxyAPI uyumlu) hesabı

    private fun proxyRanking() = ModelRanking(
        models = listOf(
            RankedModel(RemoteModel("claude-sonnet-5-5", ownedBy = "anthropic"), 90),
            RankedModel(RemoteModel("gemini-3.8-flash", ownedBy = "google"), 80),
            RankedModel(RemoteModel("grok-4.7", ownedBy = "xai"), 70),
        ),
        recommended = RemoteModel("claude-sonnet-5-5"),
    )

    private fun verifiedProxyVm(url: String = "192.168.1.20:8317", key: String = "proxy-key"): AccountEditorViewModel {
        discover.next = ApiResult.Ok(proxyRanking())
        val vm = addVm()
        vm.selectProvider(ProxyProvider.ID)
        vm.onBaseUrlChanged(url)
        vm.onKeyChanged(key)
        vm.verify()
        vm.state.awaitValue { it.verified }
        return vm
    }

    private fun saveProxy(vm: AccountEditorViewModel): AiAccount = runBlocking {
        vm.save()
        vm.state.awaitValue { it.saved }
        repo.observeAll().first().maxByOrNull { it.id }!!
    }

    @Test fun proxyNeedsUrlAndKeyBeforeVerify() {
        val vm = addVm()
        vm.selectProvider(ProxyProvider.ID)
        assertTrue(vm.state.value.isProxy)
        assertFalse(vm.state.value.canVerify)
        vm.onKeyChanged("proxy-key")
        assertFalse(vm.state.value.canVerify)
        vm.onBaseUrlChanged("http://192.168.1.20:8317")
        assertTrue(vm.state.value.canVerify)
    }

    @Test fun proxyAccountIsSavedWithBaseUrlAndEncryptedKey() = runBlocking {
        val vm = verifiedProxyVm()
        assertEquals("http://192.168.1.20:8317/v1/chat/completions", discover.lastEndpoint!!.chatUrl.toString())
        assertEquals("proxy-key", discover.lastKey)
        assertEquals(UpstreamProvider.CLAUDE, vm.state.value.upstreamFilter)
        assertEquals(
            listOf(UpstreamProvider.CLAUDE, UpstreamProvider.GEMINI, UpstreamProvider.OTHER),
            vm.state.value.availableUpstreams,
        )
        vm.onNameChanged("Benim hesabım")
        val account = saveProxy(vm)
        assertEquals(AuthType.PROXY, account.authType)
        assertEquals("claude", account.providerId)
        assertEquals("http://192.168.1.20:8317/v1", account.baseUrl)
        assertEquals("Benim hesabım", account.displayName)
        assertEquals("claude-sonnet-5-5", account.modelId)
        assertEquals("proxy-key", repo.getSecret(account.id)) // anahtar yalnız kasada
        assertFalse(account.toString().contains("proxy-key"))
    }

    @Test fun sameProviderCanHaveSeveralProxyAccountsWithOwnKeys() = runBlocking {
        val mine = saveProxy(verifiedProxyVm("192.168.1.20:8317", "my-key").also { it.onNameChanged("Benim hesabım") })
        val friend = saveProxy(verifiedProxyVm("https://friend.example.com", "friend-key").also { it.onNameChanged("Arkadaşım") })
        val again = saveProxy(verifiedProxyVm("https://friend.example.com", "other-key").also { it.onNameChanged("Arkadaşım") })
        assertEquals(setOf("claude"), setOf(mine, friend, again).map { it.providerId }.toSet())
        assertEquals(3, setOf(mine.id, friend.id, again.id).size)
        assertEquals("my-key", repo.getSecret(mine.id))
        assertEquals("friend-key", repo.getSecret(friend.id))
        assertEquals("other-key", repo.getSecret(again.id))
        assertEquals("https://friend.example.com/v1", friend.baseUrl)
        assertEquals("Arkadaşım 1", again.displayName) // ad çakışması numaralanır
    }

    @Test fun unnamedProxyAccountsGetAutomaticNames() {
        val a = saveProxy(verifiedProxyVm())
        val b = saveProxy(verifiedProxyVm())
        assertEquals("Proxy 1", a.displayName)
        assertEquals("Proxy 2", b.displayName)
    }

    @Test fun upstreamFilterNarrowsModelsAndDeterminesSavedProvider() {
        val vm = verifiedProxyVm()
        vm.selectUpstream(UpstreamProvider.GEMINI)
        assertEquals(listOf("gemini-3.8-flash"), vm.state.value.visibleModels.map { it.id })
        assertEquals("gemini-3.8-flash", vm.state.value.selectedModelId)
        vm.selectUpstream(null)
        assertEquals(3, vm.state.value.visibleModels.size)
        vm.selectUpstream(UpstreamProvider.OTHER)
        assertEquals("grok-4.7", vm.state.value.selectedModelId)
        val account = saveProxy(vm)
        assertEquals("other", account.providerId)
        assertEquals("grok-4.7", account.modelId)
    }

    @Test fun insecurePublicUrlIsRejectedWithoutAnyRequest() {
        val vm = addVm()
        vm.selectProvider(ProxyProvider.ID)
        vm.onBaseUrlChanged("http://proxy.example.com")
        vm.onKeyChanged("proxy-key")
        vm.verify()
        val s = vm.state.awaitValue { it.error != null }
        assertEquals(R.string.err_proxy_url_insecure, s.error!!.messageRes)
        assertEquals(0, discover.calls)
        assertFalse(s.verified)
    }

    @Test fun changingBaseUrlInvalidatesVerification() {
        val vm = verifiedProxyVm()
        vm.onBaseUrlChanged("192.168.1.21:8317")
        val s = vm.state.value
        assertFalse(s.verified)
        assertTrue(s.models.isEmpty())
        assertNull(s.selectedModelId)
        assertFalse(s.canSave)
    }

    @Test fun editingProxyAccountReusesStoredKeyAndAllowsRename() = runBlocking {
        discover.next = ApiResult.Ok(proxyRanking())
        val id = repo.add(
            AiAccount(
                providerId = "claude",
                displayName = "Arkadaşım",
                authType = AuthType.PROXY,
                modelId = "gemini-3.8-flash",
                powerRank = 5,
                baseUrl = "http://10.0.0.5:8317/v1",
            ),
            "stored-proxy-key",
        )
        val vm = editVm(id)
        val s = vm.state.awaitValue { it.verified }
        assertTrue(s.isProxy)
        assertEquals("http://10.0.0.5:8317/v1", s.baseUrl)
        assertEquals("Arkadaşım", s.accountName)
        assertEquals("stored-proxy-key", discover.lastKey)
        assertEquals("gemini-3.8-flash", s.selectedModelId)
        vm.onNameChanged("Arkadaşım (yedek)")
        vm.save()
        vm.state.awaitValue { it.saved }
        val updated = repo.get(id)!!
        assertEquals("Arkadaşım (yedek)", updated.displayName)
        assertEquals("gemini", updated.providerId) // seçili modelin sağlayıcısı
        assertEquals(AuthType.PROXY, updated.authType)
        assertEquals("stored-proxy-key", repo.getSecret(id))
    }
}

private suspend fun AiAccountRepository.observeAllOnce(): List<AiAccount> = observeAll().first()
