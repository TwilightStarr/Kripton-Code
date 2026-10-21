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
import com.kriptoncod.app.data.repo.UsageRepository
import com.kriptoncod.app.data.settings.ThemeStore
import com.kriptoncod.app.data.web.WebChatDriver
import com.kriptoncod.app.data.web.WebPacer
import com.kriptoncod.app.data.web.WebSessionGenerator
import com.kriptoncod.app.data.web.WebSessionStore
import com.kriptoncod.app.data.web.WebViewChatDriver
import com.kriptoncod.app.data.vault.AndroidKeystoreKeyVault
import com.kriptoncod.app.data.vault.KeyVault
import com.kriptoncod.app.data.run.AccountHealthChecker
import com.kriptoncod.app.data.run.CiFixer
import com.kriptoncod.app.data.run.CredentialProvider
import com.kriptoncod.app.data.run.FinalAiAssembler
import com.kriptoncod.app.data.run.LiveOutput
import com.kriptoncod.app.data.run.PromptDispatcher
import com.kriptoncod.app.data.run.RunCheckpointer
import com.kriptoncod.app.data.run.RunCompleter
import com.kriptoncod.app.data.run.RunFinalizer
import com.kriptoncod.app.data.run.RunGraphBuilder
import com.kriptoncod.app.data.run.RunLauncher
import com.kriptoncod.app.data.run.RunPublisher
import com.kriptoncod.app.data.run.TestSynthesizer
import com.kriptoncod.app.data.run.VaultCredentialProvider
import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.work.RunScheduler
import com.kriptoncod.app.work.RunWatchdog
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
    val usageRepository: UsageRepository by lazy { UsageRepository(database.usageEventDao()) }

    /** Üretimi süren düğümlerin canlı metni (bellekte); düğüm detay ekranı buradan akar. */
    val liveOutput: LiveOutput by lazy { LiveOutput() }

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
    val runCheckpointer: RunCheckpointer by lazy { RunCheckpointer(runRepository, githubService, githubTokenStore) }
    val ciFixer: CiFixer by lazy { CiFixer(runRepository, aiAccountRepository, credentialProvider, aiGenerator) }
    val testSynthesizer: TestSynthesizer by lazy {
        TestSynthesizer(runRepository, aiAccountRepository, credentialProvider, aiGenerator)
    }

    /**
     * Run bitişi: paketle; başarısız düğüm varsa PARTIAL (push onay ister); yoksa (testler eksikse AI'a yazdırıp)
     * GitHub'a gönder ve CI'ı izle. Kimi run'larında kodlar depoda toplanır; nihai paketi son AI üretir.
     */
    val runCompleter: RunCompleter by lazy {
        RunCompleter(
            runs = object : RunCompleter.Store {
                override suspend fun jobStates(runId: Long) = runRepository.getJobs(runId).map { it.state }
                override suspend fun status(runId: Long) = runRepository.getRun(runId)?.status
                override suspend fun setStatus(runId: Long, status: RunStatus) = runRepository.setRunStatus(runId, status)
                override suspend fun saveHistory(runId: Long) = runRepository.saveHistory(runId)
            },
            packageRun = { runId ->
                finalAiAssembler.assemble(runId) || runFinalizer.finalize(runId) is RunFinalizer.Result.Success
            },
            ensureTests = { runId -> testSynthesizer.ensure(runId) },
            shouldPublish = { runId -> runPublisher.shouldPublish(runId) },
            publish = { runId -> runPublisher.publish(runId) },
            startCiLoop = { runId -> runScheduler.startCiLoop(runId) },
        )
    }
    val accountHealthChecker: AccountHealthChecker by lazy {
        AccountHealthChecker(
            aiAccountRepository,
            credentialProvider,
            validate = { endpoint, key -> modelService.validate(endpoint, key) },
            hasWebSession = { accountId -> webSessionStore.has(accountId) },
        )
    }
    val promptDispatcher: PromptDispatcher by lazy {
        PromptDispatcher(
            runRepository, promptRepository, aiAccountRepository, credentialProvider, aiGenerator,
            onAllJobsDone = { runId -> runCompleter.complete(runId) },
            kimiCodes = kimiCodeRepository,
            graph = runGraphBuilder,
            web = webSessionGenerator,
            usage = usageRepository,
            live = liveOutput,
            onCatalogDone = { runId, merge -> runCheckpointer.checkpoint(runId, merge) },
            onJobError = { job, error ->
                com.kriptoncod.app.data.crash.RunErrorLog.record(appContext, "job", job.runId, job.id, job.title, error)
            },
        )
    }
    val runScheduler: RunScheduler by lazy { RunScheduler(WorkManager.getInstance(appContext), runRepository) }
    val runGraphBuilder: RunGraphBuilder by lazy { RunGraphBuilder(runRepository, aiAccountRepository) }
    val runLauncher: RunLauncher by lazy { RunLauncher(promptRepository, runRepository, runScheduler, runGraphBuilder) }

    /** Zinciri kopmuş yarım run'ları yeniden başlatır (bekçi ve açılış kurtarması ortak kullanır). */
    suspend fun runWatchdogSweep() {
        val views = runRepository.observeRuns().first().map { RunWatchdog.RunView(it.id, it.status) }
        RunWatchdog.sweep(
            runs = views,
            isDispatchActive = { runScheduler.isDispatchActive(it) },
            isCiActive = { runScheduler.isCiActive(it) },
            restartDispatch = { runScheduler.start(it) },
            restartCi = { runScheduler.startCiLoop(it) },
        )
    }

    /** Açılışta yarım kalan dosya/iş durumlarını toparlar; hata uygulamayı düşürmez. */
    fun startRecovery() {
        appScope.launch {
            runCatching {
                runRepository.recover()
                aiAccountRepository.purgeOrphanSecrets()
                aiAccountRepository.releaseExpiredWaits()
                runScheduler.ensureWatchdog()
                runScheduler.ensureAccountHealthCheck()
                // Süreç öldürüldüyse yarım kalan run'ların dağıtımını/CI izlemesini yeniden başlat.
                runWatchdogSweep()
            }
        }
    }
}
