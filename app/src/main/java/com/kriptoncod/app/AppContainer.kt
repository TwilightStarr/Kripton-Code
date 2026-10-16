package com.kriptoncod.app

import android.content.Context
import com.kriptoncod.app.data.ai.AiGenerator
import com.kriptoncod.app.data.ai.OpenAiCompatibleGenerator
import com.kriptoncod.app.data.ai.RetryingAiGenerator
import com.kriptoncod.app.data.db.KriptonDatabase
import com.kriptoncod.app.data.email.EmailAuthClient
import com.kriptoncod.app.data.email.EmailTokenStore
import com.kriptoncod.app.data.email.RealEmailAuthClient
import com.kriptoncod.app.data.files.HistoryStore
import com.kriptoncod.app.data.files.OutputStore
import com.kriptoncod.app.data.github.GitHubService
import com.kriptoncod.app.data.github.GitHubTokenStore
import com.kriptoncod.app.data.google.GoogleAuthClient
import com.kriptoncod.app.data.google.GoogleAuthSettings
import com.kriptoncod.app.data.google.GoogleTokenStore
import com.kriptoncod.app.data.google.RealGoogleAuthClient
import com.kriptoncod.app.data.kimi.KimiAuthClient
import com.kriptoncod.app.data.kimi.KimiCodeRepository
import com.kriptoncod.app.data.kimi.KimiTokenStore
import com.kriptoncod.app.data.kimi.RealKimiAuthClient
import com.kriptoncod.app.data.providers.ModelService
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.data.repo.PromptRepository
import com.kriptoncod.app.data.repo.RunRepository
import com.kriptoncod.app.data.settings.ThemeStore
import com.kriptoncod.app.data.web.WebChatDriver
import com.kriptoncod.app.data.web.WebPacer
import com.kriptoncod.app.data.web.WebSessionGenerator
import com.kriptoncod.app.data.web.WebSessionStore
import com.kriptoncod.app.data.web.WebViewChatDriver
import com.kriptoncod.app.data.vault.AndroidKeystoreKeyVault
import com.kriptoncod.app.data.vault.KeyVault
import com.kriptoncod.app.data.run.CredentialProvider
import com.kriptoncod.app.data.run.FinalAiAssembler
import com.kriptoncod.app.data.run.PromptDispatcher
import com.kriptoncod.app.data.run.RunFinalizer
import com.kriptoncod.app.data.run.RunGraphBuilder
import com.kriptoncod.app.data.run.RunLauncher
import com.kriptoncod.app.data.run.RunPublisher
import com.kriptoncod.app.data.run.VaultCredentialProvider
import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.work.RunScheduler
import androidx.work.WorkManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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

    val modelService: ModelService by lazy { ModelService() }

    val aiAccountRepository: AiAccountRepository by lazy { AiAccountRepository(database.aiAccountDao(), keyVault) }
    val promptRepository: PromptRepository by lazy { PromptRepository(database) }
    val runRepository: RunRepository by lazy { RunRepository(database, outputStore, historyStore) }

    // --- AI üretimi ---
    /** Normalizer'lı üretici; eksik/bozuk çıktıda aynı hesapla otomatik yeniden dener. */
    val aiGenerator: AiGenerator by lazy { RetryingAiGenerator(OpenAiCompatibleGenerator()) }

    // --- GitHub ---
    val githubTokenStore: GitHubTokenStore by lazy { GitHubTokenStore(keyVault) }
    val githubService: GitHubService by lazy { GitHubService() }

    // --- Google Auth ---
    val googleAuthSettings: GoogleAuthSettings by lazy { GoogleAuthSettings(appContext) }
    val googleTokenStore: GoogleTokenStore by lazy { GoogleTokenStore(keyVault) }
    /** İstemci kimliği her çağrıda ayarlardan okunur; Ayarlar'da değişince yeniden başlatmak gerekmez. */
    val googleAuthClient: GoogleAuthClient by lazy {
        RealGoogleAuthClient { googleAuthSettings.clientIdFlow.first() }
    }

    // --- Email Auth ---
    /** Session token kasası; Google ile aynı KeyVault'u paylaşır, key prefix farklıdır. */
    val emailTokenStore: EmailTokenStore by lazy { EmailTokenStore(keyVault) }

    /** Üretim HTTP istemcisi; testlerde FakeEmailAuthClient ile değiştirilebilir. */
    val emailAuthClient: EmailAuthClient by lazy { RealEmailAuthClient() }

    // --- Kimi Auth ---
    /** Kimi oturum token kasası; key prefix `kimi-token-` (Email/Google'dan ayrık). */
    val kimiTokenStore: KimiTokenStore by lazy { KimiTokenStore(keyVault) }

    /** Kimi web oturum açma istemcisi (e-posta/şifre + refresh). */
    val kimiAuthClient: KimiAuthClient by lazy { RealKimiAuthClient() }

    /** Kimi'nin ürettiği kod bloklarını run başına toplayan depo. */
    val kimiCodeRepository: KimiCodeRepository by lazy { KimiCodeRepository(appContext.filesDir) }

    // --- Web oturumu (e-posta ile giriş, API anahtarsız) ---
    /** Hesap başına çerez/oturum kasası (KeyVault, `web-session-<id>`). */
    val webSessionStore: WebSessionStore by lazy { WebSessionStore(keyVault) }
    val webChatDriver: WebChatDriver by lazy {
        WebViewChatDriver(appContext, webSessionStore, File(appContext.filesDir, "web-selectors.json"))
    }
    val webSessionGenerator: WebSessionGenerator by lazy { WebSessionGenerator(webChatDriver, WebPacer()) }

    // --- Dağıtım (prompt → AI hesabı) ---
    val credentialProvider: CredentialProvider by lazy {
        VaultCredentialProvider(aiAccountRepository, googleTokenStore, emailTokenStore, kimiTokenStore)
    }
    val runFinalizer: RunFinalizer by lazy { RunFinalizer(runRepository) }
    val finalAiAssembler: FinalAiAssembler by lazy {
        FinalAiAssembler(runRepository, aiAccountRepository, credentialProvider, aiGenerator, kimiCodeRepository)
    }
    val runPublisher: RunPublisher by lazy { RunPublisher(runRepository, githubService, githubTokenStore) }
    val promptDispatcher: PromptDispatcher by lazy {
        PromptDispatcher(
            runRepository, promptRepository, aiAccountRepository, credentialProvider, aiGenerator,
            onAllJobsDone = { runId ->
                // Kimi run'ları: kodlar depoda toplandı; nihai paketi son AI (en güçlü API_KEY) üretir.
                val packaged = finalAiAssembler.assemble(runId) ||
                    runFinalizer.finalize(runId) is RunFinalizer.Result.Success
                when {
                    !packaged -> RunStatus.FAILED
                    !runPublisher.shouldPublish(runId) -> RunStatus.DONE
                    // Repo seçilmiş: dalı gönder, ardından CI sonucunu izle.
                    runPublisher.publish(runId) -> {
                        runScheduler.startCiLoop(runId)
                        RunStatus.CI_WAITING
                    }
                    else -> RunStatus.FAILED
                }
            },
            kimiCodes = kimiCodeRepository,
            graph = runGraphBuilder,
            web = webSessionGenerator,
        )
    }
    val runScheduler: RunScheduler by lazy { RunScheduler(WorkManager.getInstance(appContext), runRepository) }
    val runGraphBuilder: RunGraphBuilder by lazy { RunGraphBuilder(runRepository, aiAccountRepository) }
    val runLauncher: RunLauncher by lazy { RunLauncher(promptRepository, runRepository, runScheduler, runGraphBuilder) }

    /** Açılışta yarım kalan dosya/iş durumlarını toparlar; hata uygulamayı düşürmez. */
    fun startRecovery() {
        appScope.launch {
            runCatching {
                runRepository.recover()
                aiAccountRepository.purgeOrphanSecrets()
                aiAccountRepository.releaseExpiredWaits()
                // Süreç öldürüldüyse yarım kalan run'ların dağıtımını yeniden başlat (KEEP: çift çalışmaz).
                runRepository.observeRuns().first()
                    .filter {
                        it.status == RunStatus.PREPARING || it.status == RunStatus.RUNNING ||
                            it.status == RunStatus.PUSHING
                    }
                    .forEach { runScheduler.start(it.id) }
            }
        }
    }
}
