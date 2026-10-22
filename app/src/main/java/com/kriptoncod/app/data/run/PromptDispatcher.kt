package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.ai.AiGenerationException
import com.kriptoncod.app.data.ai.AiGenerationException.Kind
import com.kriptoncod.app.data.ai.AiGenerator
import com.kriptoncod.app.data.ai.StreamSink
import com.kriptoncod.app.data.ai.TokenEstimator
import com.kriptoncod.app.data.ai.TokenMeter
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.db.Job
import com.kriptoncod.app.data.db.PromptItem
import com.kriptoncod.app.data.kimi.KimiCodeRepository
import com.kriptoncod.app.data.providers.AccountEndpoints
import com.kriptoncod.app.data.providers.ProviderEndpoint
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.data.repo.PromptRepository
import com.kriptoncod.app.data.repo.RunRepository
import com.kriptoncod.app.data.repo.UsageRepository
import com.kriptoncod.app.data.web.WebAsset
import com.kriptoncod.app.data.web.WebSessionGenerator
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AssignmentMode
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.domain.Catalog
import com.kriptoncod.app.domain.CatalogParser
import com.kriptoncod.app.domain.CatalogPrompt
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.domain.UsageOutcome
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

sealed interface DispatchOutcome {
    data class Finished(val status: RunStatus) : DispatchOutcome
    data class Wait(val delayMs: Long) : DispatchOutcome
    data object Cancelled : DispatchOutcome
}

class PromptDispatcher(
    private val runs: RunRepository,
    private val prompts: PromptRepository,
    private val accounts: AiAccountRepository,
    private val credentials: CredentialProvider,
    private val generator: AiGenerator,
    private val now: () -> Long = System::currentTimeMillis,
    private val resolveEndpoint: (AiAccount) -> ProviderEndpoint? = AccountEndpoints::resolve,
    private val onAllJobsDone: suspend (runId: Long) -> RunStatus = { RunStatus.DONE },
    private val kimiCodes: KimiCodeRepository? = null,
    private val graph: RunGraphBuilder? = null,
    /** WEB_SESSION hesapları için üretici; null ise bu tür hesaplar ERROR yapılıp atlanır. */
    private val web: WebSessionGenerator? = null,
    /** Hesap başına istek sonuçlarını kaydeder (kullanım panosu); null ise kaydedilmez. */
    private val usage: UsageRepository? = null,
    /** Verilirse API üretimi akıtılır ve metin geldikçe buraya yazılır (ekranda canlı görünür). */
    private val live: LiveOutput? = null,
    /**
     * Bir katalog birleştirmesi bitince çağrılır (ara kayıt: WIP dalına commit). En iyi çabadır; hata run'ı etkilemez.
     */
    private val onCatalogDone: suspend (runId: Long, mergeJob: Job) -> Unit = { _, _ -> },
    /** `[[tekrar-bekle: ..]]` beklemesi; testlerde sahte verilir. */
    private val sleep: suspend (ms: Long) -> Unit = { delay(it) },
    /** Beklenmedik bir istisna düğümü düşürünce çağrılır (çökme günlüğüne yazmak için); kendi hatası yutulur. */
    private val onJobError: (job: Job, error: Throwable) -> Unit = { _, _ -> },
) {

    private fun sinkFor(job: Job): StreamSink = live?.sinkFor(job.id) ?: StreamSink.NONE

    private suspend fun recordUsage(
        account: AiAccount,
        job: Job,
        outcome: UsageOutcome,
        startedAt: Long,
        waitMs: Long? = null,
        meter: TokenMeter? = null,
        prompt: String? = null,
        output: String? = null,
    ) {
        // Sağlayıcı kullanım bildirdiyse o kaydedilir (başarısız istekte de: harcanan token silinmez). Bildirmediyse
        // ve istek başarılıysa karakterden tahmin edilir ve "tahmini" diye işaretlenir; başarısızda bilinmiyor (null).
        val reported = meter?.reported()
        val estimated = if (reported == null && outcome == UsageOutcome.SUCCESS && prompt != null && output != null) {
            TokenEstimator.estimate(prompt, output)
        } else {
            null
        }
        usage?.record(
            account.id,
            job.id,
            outcome,
            (now() - startedAt).coerceAtLeast(0),
            waitMs,
            tokens = reported ?: estimated,
            tokensEstimated = reported == null && estimated != null,
        )
    }

    /** İş başına akış yönergeleri (bkz. [FlowDirectivesParser]); iş kimlikleri benzersizdir. */
    private val directiveCache = ConcurrentHashMap<Long, FlowDirectives>()

    private suspend fun directivesOf(job: Job): FlowDirectives {
        directiveCache[job.id]?.let { return it }
        val raw = job.body ?: prompts.getItem(job.promptId)?.body.orEmpty()
        val parsed = FlowDirectivesParser.parse(raw)
        directiveCache[job.id] = parsed
        return parsed
    }

    /** [job]'un `[[girdi: ...]]` ile istediği, ÖNCEKİ (daha küçük kimlikli) WORK düğümleri. */
    private fun inputSources(job: Job, d: FlowDirectives, all: List<Job>): List<Job> {
        if (d.inputs.isEmpty()) return emptyList()
        val wanted = d.inputs.map { FlowDirectivesParser.normalize(it) }.toSet()
        return all.filter {
            it.kind == JobKind.WORK && it.id < job.id && FlowDirectivesParser.normalize(it.title) in wanted
        }
    }

    /**
     * [job]'un çıktısına bağlı olduğu ([FlowDirectives.dependencies]: `[[girdi:]]` + `{{çıktı:..}}` vb.) ÖNCEKİ
     * WORK düğümleri. Hazır olma ve `[[kaynak: zorunlu]]` denetimi bunlara bakar.
     */
    private fun dependencySources(job: Job, d: FlowDirectives, all: List<Job>): List<Job> {
        val wanted = d.dependencies.map { FlowDirectivesParser.normalize(it) }.toSet()
        if (wanted.isEmpty()) return emptyList()
        return all.filter {
            it.kind == JobKind.WORK && it.id < job.id && FlowDirectivesParser.normalize(it.title) in wanted
        }
    }

    /** Kullanıcı değişkenleri: bu düğüme kadar (dahil) olan WORK düğümlerinin tanımları; sonraki tanım öncekini ezer. */
    private suspend fun variablesFor(job: Job, all: List<Job>): Map<String, String> {
        val vars = LinkedHashMap<String, String>()
        for (w in all.filter { it.kind == JobKind.WORK && it.id <= job.id }.sortedBy { it.id }) {
            for (v in directivesOf(w).variables) vars[PromptTemplate.normName(v.name)] = v.value
        }
        return vars
    }

    /** @param onProgress her adımdan sonra (biten düğüm, toplam düğüm) ile çağrılır; bildirimler için. */
    suspend fun process(
        runId: Long,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
        /** true dönerse yeni düğüm başlatılmaz ve kısa beklemeyle çıkılır (ön plan koruması yoksa dilimli çalışma). */
        shouldYield: () -> Boolean = { false },
    ): DispatchOutcome {
        val run = runs.getRun(runId) ?: return DispatchOutcome.Cancelled
        when (run.status) {
            RunStatus.CANCELLED, RunStatus.PAUSED -> return DispatchOutcome.Cancelled
            RunStatus.DONE, RunStatus.PARTIAL, RunStatus.FAILED -> return DispatchOutcome.Finished(run.status)
            else -> Unit
        }
        runs.setRunStatus(runId, RunStatus.RUNNING)
        accounts.releaseExpiredWaits()

        runs.getJobs(runId).filter { it.state == JobState.RUNNING }.forEach { runs.requeueJob(it.id) }

        var wakeAtMs: Long? = null
        // Düğüm grafiği: işler bağımlılık sırasıyla (PLAN -> WORK -> katalog birleştirme -> nihai) çalışır.
        // Her adımda taze liste okunur; böylece biten işlerin ardılları aynı turda hazır olur.
        val visited = mutableSetOf<Long>()
        while (true) {
            val all = runs.getJobs(runId)
            val job = all.firstOrNull {
                it.id !in visited && (it.state == JobState.PENDING || it.state == JobState.WAITING) && readyOrBroken(it, all)
            } ?: break
            if (shouldYield()) return DispatchOutcome.Wait(YIELD_DELAY_MS)
            visited += job.id
            when (runs.getRun(runId)?.status) {
                RunStatus.CANCELLED, RunStatus.PAUSED -> return DispatchOutcome.Cancelled
                else -> Unit
            }
            // Beklenmedik istisna run'ı sessizce kilitlemesin: düğüm sebebiyle birlikte BAŞARISIZ olur, run sürer;
            // kullanıcı düğümü tek başına yeniden deneyebilir (RunScheduler.retryNode).
            val result = try {
                processJob(job, all)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                live?.clear(job.id)
                runCatching { runs.failJob(job.id) }
                live?.note(job.id, "Beklenmeyen hata: ${e::class.simpleName}: ${e.message.orEmpty().take(300)}")
                runCatching { onJobError(job, e) }
                JobResult.Failed
            }
            runs.getJobs(runId).let { l -> onProgress(l.count { it.state == JobState.DONE }, l.size) }
            if (result is JobResult.Wait) {
                wakeAtMs = minOf(wakeAtMs ?: result.untilMs, result.untilMs)
            } else if (result is JobResult.Done && job.kind == JobKind.PLAN) {
                expandPlan(job)
            } else if (result is JobResult.Done && job.kind == JobKind.CATALOG_MERGE) {
                runs.getJob(job.id)?.let { done -> runCatching { onCatalogDone(runId, done) } }
            }
        }

        val jobs = runs.getJobs(runId)
        if (jobs.any { it.state in OPEN_STATES }) {
            val delay = ((wakeAtMs ?: (now() + RETRY_BASE_MS)) - now()).coerceAtLeast(MIN_DELAY_MS)
            return DispatchOutcome.Wait(delay)
        }

        val isGraph = jobs.any { it.kind != JobKind.WORK }
        val success = if (isGraph) {
            jobs.any { it.kind == JobKind.FINAL_MERGE && it.state == JobState.DONE }
        } else {
            jobs.all { it.state == JobState.DONE }
        }
        val status = if (success) onAllJobsDone(runId) else RunStatus.FAILED
        runs.setRunStatus(runId, status)
        runs.saveHistory(runId)
        return DispatchOutcome.Finished(status)
    }

    /** WORK/PLAN hemen hazırdır; birleştirmeler bağımlı oldukları işler bitince (DONE/FAILED) hazır olur. */
    private suspend fun isReady(job: Job, all: List<Job>): Boolean = when (job.kind) {
        JobKind.PLAN -> true

        // WORK: `[[girdi: ...]]` / `{{çıktı:..}}` ile çıktısı istenen önceki promptlar bitene kadar bekler.
        JobKind.WORK -> dependencySources(job, directivesOf(job), all).all { it.isTerminal() }

        JobKind.CATALOG_MERGE ->
            all.filter { it.kind == JobKind.WORK && it.catalog == job.catalog }.all { it.isTerminal() }

        JobKind.FINAL_MERGE ->
            all.filter { it.kind == JobKind.WORK || it.kind == JobKind.CATALOG_MERGE }.all { it.isTerminal() }
    }

    /** Hazırlık denetimi (yönerge ayrıştırma vb.) fırlatırsa düğüm "hazır" sayılır; hata [processJob]'da sebebiyle düşer. */
    private suspend fun readyOrBroken(job: Job, all: List<Job>): Boolean = try {
        isReady(job, all)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        true
    }

    private fun waitNote(untilMs: Long): String {
        val minutes = ((untilMs - now()) / 60_000L).coerceAtLeast(0) + 1
        return "Tüm hesaplar şu an kullanılamıyor (sağlayıcı sınırı, günlük kota ya da geçici arıza). " +
            "Yaklaşık $minutes dk sonra yeniden denenecek."
    }

    private fun Job.isTerminal() = state == JobState.DONE || state == JobState.FAILED

    /** Planlayıcı bitti: çıktısını kataloglara ayırır (olmazsa girdi promptlarıyla tek katalog) ve grafiği kurar. */
    private suspend fun expandPlan(plan: Job) {
        val builder = graph ?: return
        val output = runs.readOutput(plan.id).orEmpty()
        val catalogs = CatalogParser.parseText(output)
            .ifEmpty { CatalogParser.parseText(plan.body.orEmpty()) }
            .ifEmpty { listOf(Catalog(CatalogParser.DEFAULT_CATALOG, listOf(CatalogPrompt("Prompt", plan.body.orEmpty())))) }
        builder.build(plan.runId, catalogs)
    }

    /**
     * Birleştirme düğümünün kaynakları: bağımlı düğümlerin çıktıları. Birleştirme KODLA yapılır
     * ([MergePlanner]); AI yalnızca aynı yolda farklı içerik çıkarsa çağrılır.
     */
    private suspend fun mergeSources(job: Job, all: List<Job>): List<MergePlanner.Source> {
        val sources: List<Pair<String, Job>> = when (job.kind) {
            JobKind.CATALOG_MERGE ->
                all.filter { it.kind == JobKind.WORK && it.catalog == job.catalog && it.state == JobState.DONE }
                    .filter { directivesOf(it).share } // [[ilet: hayır]] düğümleri birleştirmeye gitmez
                    .map { (it.title ?: "Prompt #${it.id}") to it }

            JobKind.FINAL_MERGE -> all.filter { it.kind == JobKind.CATALOG_MERGE }.flatMap { m ->
                if (m.state == JobState.DONE) {
                    listOf((m.catalog ?: "katalog") to m)
                } else { // katalog birleştirmesi başarısızsa o katalogun ham çıktıları kullanılır
                    all.filter { it.kind == JobKind.WORK && it.catalog == m.catalog && it.state == JobState.DONE }
                        .filter { directivesOf(it).share }
                        .map { "${m.catalog}/${it.title ?: it.id}" to it }
                }
            }

            else -> return emptyList()
        }
        return sources.map { (name, src) -> MergePlanner.Source(name, src.id, runs.readOutput(src.id).orEmpty()) }
    }

    private fun mergeHeader(job: Job): String = if (job.kind == JobKind.FINAL_MERGE) {
        "Bir yazılım işinin katalog bazında birleştirilmiş çıktılarında çakışmalar var."
    } else {
        "'${job.catalog}' kataloğundaki ayrı yapay zekaların çıktılarında çakışmalar var."
    }

    /**
     * WORK/PLAN metni: yönergeler çıkarılır, `{{...}}` değişkenleri ve çıktı başvuruları çözülür, `[[girdi:]]` ile
     * istenen önceki çıktıların arayüz özeti göreve eklenir. null: düğüm çalıştırılamaz (metin yok ya da
     * `[[kaynak: zorunlu]]` olup bir kaynak eksik/başarısız).
     * @param attempt `{{deneme}}` yerleşiği için; 1'den başlar.
     */
    private suspend fun workText(job: Job, all: List<Job>, attempt: Int = 1): String? {
        val raw = job.body ?: prompts.getItem(job.promptId)?.body ?: return null
        if (job.kind != JobKind.WORK) return raw
        val d = directivesOf(job)
        val deps = dependencySources(job, d, all)
        val loaded = HashMap<String, PromptTemplate.SourceOutput>()
        for (t in d.dependencies) {
            val key = FlowDirectivesParser.normalize(t)
            val src = deps.filter { FlowDirectivesParser.normalize(it.title) == key }.maxByOrNull { it.id }
            val text = if (src?.state == JobState.DONE) runs.readOutput(src.id) else null
            loaded[key] = PromptTemplate.SourceOutput(t, text)
        }
        if (d.requireSources && loaded.values.any { it.text == null }) return null

        val index = all.filter { it.kind == JobKind.WORK }.sortedBy { it.id }.indexOfFirst { it.id == job.id } + 1
        val ctx = PromptTemplate.Context(
            vars = variablesFor(job, all),
            builtins = PromptTemplate.builtins(job.runId, job.title, job.catalog, index, now(), attempt),
            output = { loaded[FlowDirectivesParser.normalize(it)] },
        )
        val rendered = PromptTemplate.render(d.body, ctx)
        if (d.requireSources && rendered.missing.isNotEmpty()) return null

        val sources = inputSources(job, d, all).filter { it.state == JobState.DONE }
        if (sources.isEmpty()) return rendered.text
        return buildString {
            appendLine(
                "Aşağıda bu görev için daha önce üretilmiş çıktıların ARAYÜZ ÖZETİ var (dosya yolları ve bildirim " +
                    "satırları; gövdeler çıkarıldı). Bunlarla tutarlı çalış (aynı adlar, aynı imzalar, aynı dosya " +
                    "yolları); özetlenen dosyaları yeniden yazma. Görevin kendisi en altta.",
            )
            appendLine()
            for (src in sources) {
                appendLine("=== ${src.title ?: "Prompt #${src.id}"} ===")
                appendLine(InterfaceSummarizer.summarizeOutput(runs.readOutput(src.id).orEmpty()))
                appendLine()
            }
            appendLine("=== GÖREV ===")
            append(rendered.text)
        }
    }

    private fun definitionSummary(d: FlowDirectives): String = buildString {
        appendLine("Değişkenler:")
        d.variables.forEach { appendLine("- ${it.name} = ${it.value}") }
    }.trimEnd()

    private sealed interface JobResult {
        data object Done : JobResult
        data object Failed : JobResult
        data class Wait(val untilMs: Long) : JobResult
    }

    private sealed interface Pick {
        data class Use(val account: AiAccount) : Pick
        data class WaitUntil(val untilMs: Long) : Pick
        data object None : Pick
    }

    private suspend fun processJob(job: Job, all: List<Job>): JobResult {
        // Birleştirme düğümleri: çakışmasız dosyalar kodla alınır; AI yalnız çakışan dosyalar için çağrılır.
        if (job.kind == JobKind.WORK && directivesOf(job).isDefinitionOnly) {
            // Yalnız `[[değişken:]]` tanımlayan düğüm: AI çağrılmaz, hesap/kota harcanmaz.
            runs.completeJob(job.id, definitionSummary(directivesOf(job)))
            return JobResult.Done
        }
        var finish: (String) -> String = { it }
        val text: String? = when (job.kind) {
            JobKind.CATALOG_MERGE, JobKind.FINAL_MERGE -> {
                val sources = mergeSources(job, all)
                if (sources.isEmpty()) {
                    null
                } else {
                    val plan = MergePlanner.plan(sources)
                    if (!plan.needsAi) {
                        // AI gerekmez: hesap/kota harcanmaz, hiçbir dosya yeniden yazılmadığı için düşme/değişme riski yok.
                        runs.completeJob(job.id, MergePlanner.assemble(plan, null).text)
                        return JobResult.Done
                    }
                    finish = { aiText -> MergePlanner.assemble(plan, aiText).text }
                    MergePlanner.conflictPrompt(plan, mergeHeader(job))
                }
            }

            else -> workText(job, all)
        }
        if (text.isNullOrBlank()) {
            runs.failJob(job.id)
            return JobResult.Failed
        }
        var item = PromptItem(id = job.promptId, setId = 0, title = job.title.orEmpty(), body = text, order = 0)
        val directives = if (job.kind == JobKind.WORK) directivesOf(job) else null
        val retry = directives?.retry ?: RetryRules()
        val rules = directives?.rules.orEmpty()
        var qualityTries = 0 // doğrulama kuralı yüzünden yapılan yeniden üretimler (bu çağrıda)
        // MANUAL atama yalnız hesap sağlıklıyken geçerlidir; hesap kapanır/düşerse iş kaybolmaz, havuza geçer.
        var manual = prompts.getAssignment(job.promptId)?.mode == AssignmentMode.MANUAL
        val tried = mutableSetOf<Long>()

        while (true) {
            val account = when (val pick = pickAccount(job, manual, tried)) {
                is Pick.Use -> pick.account

                is Pick.WaitUntil -> {
                    live?.note(job.id, waitNote(pick.untilMs))
                    runs.waitJob(job.id)
                    return JobResult.Wait(pick.untilMs)
                }

                Pick.None -> {
                    if (manual) { // atanan hesap yok/kapalı: otomatik havuza geç
                        manual = false
                        continue
                    }
                    // Kullanılabilir hesap kalmadı: iş BAŞARISIZ OLMAZ, kullanıcı hesap ekleyene/açana kadar bekler.
                    live?.note(
                        job.id,
                        "Kullanılabilir hesap yok: hesaplar kapalı ya da Hata durumunda. " +
                            "Hesap açılınca ya da onarılınca otomatik yeniden denenecek.",
                    )
                    runs.waitJob(job.id)
                    return JobResult.Wait(now() + NO_ACCOUNT_WAIT_MS)
                }
            }
            tried += account.id

            if (account.authType == AuthType.KIMI && kimiCodes != null) {
                return processKimiJob(job, account, item, finish)
            }

            val isWeb = account.authType == AuthType.WEB_SESSION
            val endpoint = if (isWeb) null else resolveEndpoint(account)
            val credential = if (isWeb) null else credentials.credentialFor(account)
            val unusable = if (isWeb) web == null else (endpoint == null || credential.isNullOrBlank())
            if (unusable) {
                accounts.markError(account.id)
                manual = false
                continue
            }

            if (job.accountId != account.id) runs.assignAccount(job.id, account.id)
            live?.clear(job.id) // eski bekleme notu canlı akışa karışmasın
            runs.startJob(job.id)
            val startedAt = now()
            val meter = TokenMeter()

            try {
                val assets = mutableListOf<WebAsset>()
                val text = if (isWeb) {
                    // Web sohbeti: PLAN düz metin ister; diğer düğümler dosya bloğu bekler.
                    web!!.generate(account, item.body, expectFiles = job.kind != JobKind.PLAN, onAsset = { assets += it })
                } else {
                    withContext(meter) { generator.generate(endpoint!!, account.modelId, credential!!, item.body, sinkFor(job)) }
                }
                if (text.isBlank()) {
                    recordUsage(account, job, UsageOutcome.FAILED, startedAt, meter = meter)
                    runs.failJob(job.id)
                    return JobResult.Failed
                }
                // Ham zip ve ikili dosyalar çıktıdan ÖNCE yazılır: çökme olursa iş DONE sayılmaz, yeniden üretilir.
                if (assets.isNotEmpty()) runCatching { runs.saveAssets(job.id, assets.map { it.path to it.bytes }) }
                if (rules.isNotEmpty()) {
                    val reasons = OutputRules.check(text, rules)
                    if (reasons.isNotEmpty()) {
                        // İstek başarılıydı (kota harcandı) ama çıktı kuralları sağlamıyor.
                        recordUsage(account, job, UsageOutcome.SUCCESS, startedAt, meter = meter, prompt = item.body, output = text)
                        if (qualityTries < retry.maxRetries) {
                            qualityTries++
                            if (retry.delayMs > 0) sleep(retry.delayMs)
                            val base = workText(job, runs.getJobs(job.runId), attempt = qualityTries + 1) ?: item.body
                            item = item.copy(
                                body = if (retry.feedback) base + OutputRules.feedback(reasons, qualityTries, retry.maxRetries) else base,
                            )
                            if (retry.switchAccount) {
                                manual = false
                                // Başka hesap yoksa aynı hesapla sürer (iş beklemeye düşmez).
                                if (pickAccount(job, false, tried) !is Pick.Use) tried -= account.id
                            } else {
                                tried -= account.id
                            }
                            runs.requeueJob(job.id)
                            continue
                        }
                        if (retry.onExhausted == OnExhausted.STOP) {
                            runs.failJob(job.id)
                            return JobResult.Failed
                        }
                        // CONTINUE: son çıktı kabul edilir.
                        runs.completeJob(job.id, finish(text))
                        return JobResult.Done
                    }
                }
                runs.completeJob(job.id, finish(text))
                recordUsage(account, job, UsageOutcome.SUCCESS, startedAt, meter = meter, prompt = item.body, output = text)
                return JobResult.Done
            } catch (e: AiGenerationException) {
                recordUsage(
                    account,
                    job,
                    when (e.kind) {
                        Kind.UNAUTHORIZED -> UsageOutcome.UNAUTHORIZED
                        Kind.RATE_LIMITED -> UsageOutcome.RATE_LIMITED
                        Kind.SERVER, Kind.NETWORK -> UsageOutcome.TRANSIENT
                        else -> UsageOutcome.FAILED
                    },
                    startedAt,
                    waitMs = if (e.kind == Kind.RATE_LIMITED) (e.waitUntil(now()) - now()).coerceAtLeast(0) else null,
                    meter = meter,
                )
                when (e.kind) {
                    Kind.UNAUTHORIZED -> {
                        // Hesap kapandı/oturum düştü: ERROR yap ve işi aynı turda havuzdaki diğer hesaba ver.
                        accounts.markError(account.id)
                        manual = false
                    }

                    Kind.RATE_LIMITED -> {
                        val until = e.waitUntil(now())
                        accounts.markWaiting(account.id, until)
                        if (manual) {
                            runs.waitJob(job.id)
                            return JobResult.Wait(until)
                        }
                    }

                    Kind.SERVER, Kind.NETWORK -> {
                        // Kalite yeniden denemeleri de `attempts` sayacını artırır; hata sınırından düşülür.
                        val attempts = ((runs.getJob(job.id)?.attempts ?: MAX_ATTEMPTS) - qualityTries).coerceAtLeast(0)
                        if (attempts >= (retry.maxErrorAttempts ?: MAX_ATTEMPTS)) {
                            runs.failJob(job.id)
                            return JobResult.Failed
                        }
                        runs.requeueJob(job.id)
                        // Önce havuzdaki başka hesabı dene (yedek model); hepsi denendiyse artan beklemeyle yeniden kuyruğa al.
                        if (!manual && pickAccount(job, false, tried) is Pick.Use) continue
                        return JobResult.Wait(now() + retryDelayMs(attempts))
                    }

                    else -> {
                        // Kalıcı hata (bağlam aşımı, kesik çıktı, geçersiz istek, model yok): bu hesapla tekrar denemek
                        // anlamsız, ama başka hesap/model başarabilir. Sıradaki hesaba geç; hepsi denendiyse işi düşür.
                        manual = false
                        when (val next = pickAccount(job, false, tried)) {
                            is Pick.Use -> {
                                runs.requeueJob(job.id)
                                continue
                            }

                            is Pick.WaitUntil -> {
                                runs.waitJob(job.id)
                                return JobResult.Wait(next.untilMs)
                            }

                            Pick.None -> {
                                runs.failJob(job.id)
                                return JobResult.Failed
                            }
                        }
                    }
                }
            } catch (_: IllegalArgumentException) {
                recordUsage(account, job, UsageOutcome.FAILED, startedAt, meter = meter)
                runs.failJob(job.id)
                return JobResult.Failed
            } finally {
                live?.clear(job.id) // kalıcı çıktı yazıldı (ya da iş düştü): canlı metin gerekmez
            }
        }
    }

    private suspend fun processKimiJob(job: Job, account: AiAccount, item: PromptItem, finish: (String) -> String): JobResult {
        val endpoint = resolveEndpoint(account)
        val credential = credentials.credentialFor(account)
        if (endpoint == null || credential.isNullOrBlank()) {
            accounts.markError(account.id)
            runs.failJob(job.id)
            return JobResult.Failed
        }

        if (job.accountId != account.id) runs.assignAccount(job.id, account.id)
        runs.startJob(job.id)
        val startedAt = now()
        val meter = TokenMeter()

        return try {
            val text = withContext(meter) { generator.generate(endpoint, account.modelId, credential, item.body, sinkFor(job)) }
            if (text.isBlank()) {
                recordUsage(account, job, UsageOutcome.FAILED, startedAt, meter = meter)
                runs.failJob(job.id)
                JobResult.Failed
            } else {
                val finalText = finish(text)
                kimiCodes?.collect(job.runId, finalText)
                runs.completeJob(job.id, finalText)
                recordUsage(account, job, UsageOutcome.SUCCESS, startedAt, meter = meter, prompt = item.body, output = text)
                JobResult.Done
            }
        } catch (e: AiGenerationException) {
            recordUsage(
                account,
                job,
                when (e.kind) {
                    Kind.UNAUTHORIZED -> UsageOutcome.UNAUTHORIZED
                    Kind.RATE_LIMITED -> UsageOutcome.RATE_LIMITED
                    Kind.SERVER, Kind.NETWORK -> UsageOutcome.TRANSIENT
                    else -> UsageOutcome.FAILED
                },
                startedAt,
                waitMs = if (e.kind == Kind.RATE_LIMITED) (e.waitUntil(now()) - now()).coerceAtLeast(0) else null,
                meter = meter,
            )
            when (e.kind) {
                Kind.RATE_LIMITED -> {
                    val until = e.waitUntil(now())
                    accounts.markWaiting(account.id, until)
                    runs.waitJob(job.id)
                    JobResult.Wait(until)
                }

                Kind.SERVER, Kind.NETWORK -> {
                    val attempts = runs.getJob(job.id)?.attempts ?: MAX_ATTEMPTS
                    if (attempts >= MAX_ATTEMPTS) {
                        runs.failJob(job.id)
                        JobResult.Failed
                    } else {
                        runs.requeueJob(job.id)
                        JobResult.Wait(now() + retryDelayMs(attempts))
                    }
                }

                else -> {
                    accounts.markError(account.id)
                    runs.failJob(job.id)
                    JobResult.Failed
                }
            }
        } catch (_: IllegalArgumentException) {
            recordUsage(account, job, UsageOutcome.FAILED, startedAt, meter = meter)
            runs.failJob(job.id)
            JobResult.Failed
        } finally {
            live?.clear(job.id)
        }
    }

    private suspend fun pickAccount(job: Job, manual: Boolean, tried: Set<Long>): Pick {
        val t = now()
        if (manual) {
            val id = job.accountId?.takeIf { it !in tried } ?: return Pick.None
            val account = accounts.get(id) ?: return Pick.None
            if (!account.enabled || account.status == AccountStatus.ERROR) return Pick.None
            val until = account.waitUntil
            if (account.status == AccountStatus.WAITING && until != null && until > t) return Pick.WaitUntil(until)
            // Günlük kota doldu ya da devre kesici açık: iş bu hesapta takılmaz, havuza geçer.
            if (AccountSelector.poolBlockUntil(account, usage?.healthSnapshot()?.get(id), t) != null) return Pick.None
            return Pick.Use(account)
        }

        val enabled = accounts.observeAll().first().filter { it.enabled }
        val health = usage?.healthSnapshot().orEmpty()
        // Grafik düğümlerinde planlama sırasında güce göre seçilen hesap önceliklidir; kullanılamıyorsa
        // (bekleme/kota/devre kesici) seçici en uygun hesaba ya da en erken açılma zamanına geçer.
        val preferred = if (job.kind != JobKind.WORK || job.catalog != null) job.accountId else null
        return when (val choice = AccountSelector.choose(enabled, health, tried, preferred, t)) {
            is AccountSelector.Choice.Use -> Pick.Use(choice.account)
            is AccountSelector.Choice.WaitUntil -> Pick.WaitUntil(choice.untilMs)
            AccountSelector.Choice.None -> Pick.None
        }
    }

    companion object {
        /**
         * Geçici (5xx/ağ) hatada bir düğüm için en çok deneme. Bekleme [RETRY_BASE_MS] * deneme ile artar ve
         * [MAX_RETRY_DELAY_MS]'de sınırlanır: 60 denemede toplam yaklaşık 7-8 saat; günlerce süren işte
         * kısa bir sunucu kesintisi düğümü kalıcı düşürmez.
         */
        const val MAX_ATTEMPTS = 60
        const val MAX_RETRY_DELAY_MS = 15L * 60 * 1000

        /** Ön plan koruması yokken bir çalışma diliminden sonra bekleme. */
        const val YIELD_DELAY_MS = 5_000L

        fun retryDelayMs(attempts: Int): Long = (RETRY_BASE_MS * attempts.coerceAtLeast(1)).coerceAtMost(MAX_RETRY_DELAY_MS)

        /** Hiç kullanılabilir hesap yokken işin yeniden denenme aralığı. */
        const val NO_ACCOUNT_WAIT_MS = 10L * 60 * 1000
        const val RETRY_BASE_MS = 15_000L
        private const val MIN_DELAY_MS = 1_000L
        private val OPEN_STATES = setOf(JobState.PENDING, JobState.RUNNING, JobState.WAITING)
    }
}
