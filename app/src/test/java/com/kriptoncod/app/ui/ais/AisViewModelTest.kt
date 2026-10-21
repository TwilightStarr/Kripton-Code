package com.kriptoncod.app.ui.ais

import com.kriptoncod.app.R
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.db.KriptonDatabase
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.data.repo.UsageRepository
import com.kriptoncod.app.data.run.PoolStatus
import com.kriptoncod.app.domain.UsageOutcome
import com.kriptoncod.app.data.vault.KeyVault
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.testutil.FakeKeyVault
import com.kriptoncod.app.testutil.awaitValue
import com.kriptoncod.app.testutil.inMemoryDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Silme sırasında kasa hatası üretebilen sahte kasa. */
private class FlakyVault(private val inner: FakeKeyVault) : KeyVault by inner {
    @Volatile var failDelete = false
    override suspend fun delete(keyId: String) {
        if (failDelete) throw java.io.IOException("kasa hatası")
        inner.delete(keyId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AisViewModelTest {
    private lateinit var db: KriptonDatabase
    private lateinit var fake: FakeKeyVault
    private lateinit var vault: FlakyVault
    private lateinit var repo: AiAccountRepository

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = inMemoryDb()
        fake = FakeKeyVault()
        vault = FlakyVault(fake)
        repo = AiAccountRepository(db.aiAccountDao(), vault)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun addAccount(name: String, rank: Int = 1): Long = runBlocking {
        repo.add(
            AiAccount(providerId = "groq", displayName = name, authType = AuthType.API_KEY, modelId = "m", powerRank = rank),
            "secret-$name",
        )
    }

    @Test fun emptyListFinishesLoading() {
        val vm = AisViewModel(repo)
        val s = vm.state.awaitValue { !it.loading }
        assertTrue(s.accounts.isEmpty())
        assertNull(s.error)
    }

    @Test fun listsAllAccountsIncludingSameProvider() {
        addAccount("Groq 1", rank = 5)
        addAccount("Groq 2", rank = 9)
        val vm = AisViewModel(repo)
        val s = vm.state.awaitValue { it.accounts.size == 2 }
        assertEquals(listOf("Groq 2", "Groq 1"), s.accounts.map { it.displayName }) // güce göre azalan
    }

    @Test fun toggleEnabledPersistsAndUpdatesState() {
        val id = addAccount("Groq 1")
        val vm = AisViewModel(repo)
        vm.state.awaitValue { it.accounts.size == 1 }
        vm.setEnabled(id, false)
        vm.state.awaitValue { s -> s.accounts.singleOrNull()?.enabled == false }
        assertFalse(runBlocking { repo.get(id)!!.enabled })
        vm.setEnabled(id, true)
        vm.state.awaitValue { s -> s.accounts.singleOrNull()?.enabled == true }
    }

    @Test fun deleteNeedsConfirmationAndRemovesKey() {
        val keep = addAccount("Groq 1")
        val gone = addAccount("Groq 2")
        val vm = AisViewModel(repo)
        vm.state.awaitValue { it.accounts.size == 2 }

        vm.requestDelete(gone)
        assertEquals(gone, vm.state.value.pendingDelete?.id)
        vm.cancelDelete()
        assertNull(vm.state.value.pendingDelete)
        assertEquals(2, vm.state.value.accounts.size)

        vm.requestDelete(gone)
        vm.confirmDelete()
        val s = vm.state.awaitValue { it.accounts.size == 1 }
        assertEquals(keep, s.accounts.single().id)
        assertNull(s.pendingDelete)
        assertFalse(fake.store.containsKey(AiAccount.vaultKeyId(gone)))
        assertTrue(fake.store.containsKey(AiAccount.vaultKeyId(keep)))
    }

    @Test fun confirmDeleteWithoutRequestDoesNothing() {
        addAccount("Groq 1")
        val vm = AisViewModel(repo)
        vm.state.awaitValue { it.accounts.size == 1 }
        vm.confirmDelete()
        assertEquals(1, vm.state.value.accounts.size)
        assertNull(vm.state.value.error)
    }

    @Test fun failedDeleteShowsErrorAndRetrySucceeds() {
        val id = addAccount("Groq 1")
        val vm = AisViewModel(repo)
        vm.state.awaitValue { it.accounts.size == 1 }

        vault.failDelete = true
        vm.requestDelete(id)
        vm.confirmDelete()
        val failed = vm.state.awaitValue { it.error != null }
        assertEquals(R.string.err_action_failed, failed.error!!.messageRes)
        assertEquals(ListAction.Delete(id), failed.error!!.retry)
        assertEquals(1, failed.accounts.size) // hesap silinmedi

        vault.failDelete = false
        vm.retry()
        val ok = vm.state.awaitValue { it.accounts.isEmpty() && it.error == null }
        assertNotNull(ok)
    }

    @Test fun dismissErrorClearsIt() {
        val id = addAccount("Groq 1")
        val vm = AisViewModel(repo)
        vm.state.awaitValue { it.accounts.size == 1 }
        vault.failDelete = true
        vm.requestDelete(id)
        vm.confirmDelete()
        vm.state.awaitValue { it.error != null }
        vm.dismissError()
        assertNull(vm.state.value.error)
    }

    @Test fun poolStaysEmptyWithoutUsageRepository() {
        addAccount("a")
        val s = AisViewModel(repo).state.awaitValue { !it.loading && it.accounts.isNotEmpty() }
        assertTrue(s.pool.isEmpty())
    }

    @Test fun poolShowsFullQuotaAccountAsResting() {
        val id = runBlocking {
            repo.add(
                AiAccount(
                    providerId = "groq",
                    displayName = "q",
                    authType = AuthType.API_KEY,
                    modelId = "m",
                    powerRank = 1,
                    dailyLimit = 1,
                ),
                "secret-q",
            )
        }
        runBlocking { UsageRepository(db.usageEventDao()).record(id, null, UsageOutcome.SUCCESS, 10) }
        val vm = AisViewModel(repo, UsageRepository(db.usageEventDao()))
        val s = vm.state.awaitValue { it.pool[id] != null }
        assertEquals(PoolStatus.State.RESTING_QUOTA, s.pool.getValue(id).state)
        assertEquals(1, s.poolSummary.resting)
    }
}
