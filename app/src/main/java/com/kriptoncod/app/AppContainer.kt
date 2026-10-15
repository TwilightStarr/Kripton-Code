package com.kriptoncod.app

import android.content.Context
import com.kriptoncod.app.data.db.KriptonDatabase
import com.kriptoncod.app.data.files.HistoryStore
import com.kriptoncod.app.data.files.OutputStore
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.data.repo.PromptRepository
import com.kriptoncod.app.data.repo.RunRepository
import com.kriptoncod.app.data.settings.ThemeStore
import com.kriptoncod.app.data.vault.AndroidKeystoreKeyVault
import com.kriptoncod.app.data.vault.KeyVault
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Elle basit DI: tekil bağımlılıklar burada tembel (lazy) oluşturulur. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val themeStore: ThemeStore by lazy { ThemeStore(appContext) }

    val database: KriptonDatabase by lazy { KriptonDatabase.build(appContext) }
    val keyVault: KeyVault by lazy { AndroidKeystoreKeyVault(File(appContext.filesDir, "vault")) }
    val outputStore: OutputStore by lazy { OutputStore(appContext.filesDir) }
    val historyStore: HistoryStore by lazy { HistoryStore(appContext.filesDir) }

    val aiAccountRepository: AiAccountRepository by lazy { AiAccountRepository(database.aiAccountDao(), keyVault) }
    val promptRepository: PromptRepository by lazy { PromptRepository(database) }
    val runRepository: RunRepository by lazy { RunRepository(database, outputStore, historyStore) }

    /** Açılışta yarım kalan dosya/iş durumlarını toparlar; hata uygulamayı düşürmez. */
    fun startRecovery() {
        appScope.launch {
            runCatching {
                runRepository.recover()
                aiAccountRepository.purgeOrphanSecrets()
                aiAccountRepository.releaseExpiredWaits()
            }
        }
    }
}
