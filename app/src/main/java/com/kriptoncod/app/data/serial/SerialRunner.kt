package com.kriptoncod.app.data.serial

import com.kriptoncod.app.data.ai.AiGenerationException
import com.kriptoncod.app.data.ai.AiGenerationException.Kind
import com.kriptoncod.app.data.ai.normalize.OutputCleaner
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.github.GitHubFile
import com.kriptoncod.app.data.run.CodeBlockParser
import com.kriptoncod.app.data.run.PathSafety
import com.kriptoncod.app.data.run.PlaceholderDetector
import com.kriptoncod.app.data.web.ContinuationMerger
import com.kriptoncod.app.data.web.LimitParser
import com.kriptoncod.app.data.web.OutputStyle
import com.kriptoncod.app.data.web.WebChatDriver
import com.kriptoncod.app.data.web.WebChatSession
import com.kriptoncod.app.data.web.WebOutputCheck
import com.kriptoncod.app.data.web.WebOutputPolicy
import com.kriptoncod.app.data.web.WebProvider
import com.kriptoncod.app.data.web.WebReply
import com.kriptoncod.app.data.web.ZipBlocks
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Kullanıcıya bildirilecek seri mod olayları. */
enum class SerialEvent { DONE, NEEDS_USER, QUOTA_WAIT }

/**
 * Seri mod: TEK web AI hesabı, TEK sohbet, sıralı promptlar.
 *
 * Her prompt için: AI'a gönder → yanıt TAM gelene kadar "devam et" → dosyaları repoya commit'le → GitHub Actions'ı bekle →
 * hata/uyarı varsa günlüğü AYNI sohbete yapıştır, düzeltmeyi commit'le, tekrar bekle → temizse sonraki prompta geç.
 * Sohbet yalnız prompt `[[yeni-sohbet]]` yönergesi taşıyorsa değişir; o zaman projenin GÜNCEL hâli (repodan) yeni sohbete
 * dosya olarak verilir.
 *
 * Dayanıklılık: durumun tamamı diske yazılır; süreç ölse, sayfa yenilense, kota dolsa kaldığı yerden sürer. Sohbet
 * sayfası yenilendiğinde son yanıt okunur ([WebChatSession.peekLast]); yanıt zaten geldiyse prompt İKİNCİ KEZ gönderilmez.
 * Ağ/sunucu/kota hataları sınırsız yeniden denenir (kota için sağlayıcının söylediği süre beklenir). Yalnız kullanıcıdan
 * başka çare olmayan durumlar (oturum düştü, token izni yok, deneme sınırı doldu) [SerialStatus.WAITING_USER] olur.
 */
class SerialRunner(
    private val store: SerialStore,
    private val account: suspend (Long) -> AiAccount?,
    private val driver: WebChatDriver,
    private val git: SerialGit,
    private val notify: (SerialState, SerialEvent) -> Unit = { _, _ -> },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val now: () -> Long = System::currentTimeMillis,
    private val random: () -> Int = { (Math.random() * Int.MAX_VALUE).toInt() },
    private val errorDelayMs: () -> Long = { 60_000L + (Math.random() * 60_000L).toLong() },
) {

    /** Duraklatıldı/iptal edildi/kayıt silindi: döngüden temiz çıkış. */
    private class StopRun : Exception()

    /** Kullanıcıdan başka çare yok. */
    private class NeedUser(message: String) : Exception(message)

    private class Ctx(val id: Long) {
        var session: WebChatSession? = null
    }

    /** [id] çalışmasını durumuna göre sürdürür; bitince, duraklayınca ya da müdahale gerekince döner. */
    suspend fun run(id: Long) {
        val ctx = Ctx(id)
        try {
            loop(ctx)
        } catch (_: StopRun) {
            // Durum zaten yazılı.
        } catch (e: NeedUser) {
            store.update(id) { it.copy(status = SerialStatus.WAITING_USER, message = e.message.orEmpty()) }
            store.log(id, "Müdahale gerekiyor: ${e.message}")
            store.get(id)?.let { notify(it, SerialEvent.NEEDS_USER) }
        } finally {
            closeSession(ctx)
        }
    }

    // ------------------------------------------------------------------------------------------------ döngü

    private suspend fun loop(ctx: Ctx) {
        val id = ctx.id
        while (true) {
            val st = store.get(id) ?: return
            if (st.status != SerialStatus.RUNNING) return
            if (st.index >= st.total) {
                finish(id)
                return
            }
            when (st.phase) {
                SerialPhase.PROMPT, SerialPhase.FIX -> exchangePhase(ctx)
                SerialPhase.COMMIT -> commitPhase(ctx)
                SerialPhase.CI -> ciPhase(ctx)
            }
        }
    }

    private fun finish(id: Long) {
        store.update(id) { it.copy(status = SerialStatus.DONE, message = "Tüm promptlar tamamlandı.") }
        store.log(id, "Tamamlandı.")
        store.get(id)?.let { notify(it, SerialEvent.DONE) }
    }

    /** Sonraki prompta geçer; yeni sohbet isteyen prompt için projenin güncel hâlinin verilmesini işaretler. */
    private fun advance(id: Long) {
        store.clearStep(id)
        store.clearFiles(id)
        store.update(id) {
            val next = it.index + 1
            it.copy(
                index = next,
                phase = SerialPhase.PROMPT,
                headSha = null,
                fixRound = 0,
                warnRound = 0,
                pendingLog = null,
                pendingKind = null,
                nonce = null,
                needsProject = it.prompts.getOrNull(next)?.newChat == true,
                chatUrl = if (it.prompts.getOrNull(next)?.newChat == true) null else it.chatUrl,
                message = "",
            )
        }
    }

    // ------------------------------------------------------------------------------- prompt / düzeltme alışverişi

    private suspend fun exchangePhase(ctx: Ctx) {
        val id = ctx.id
        val st = requireState(id)
        val acc = account(st.accountId) ?: throw NeedUser("Seçilen AI hesabı silinmiş.")
        val provider = WebProvider.fromId(acc.providerId)?.takeIf { it.output != OutputStyle.API_KEY }
            ?: throw NeedUser("Bu hesap web sohbeti için uygun değil.")
        val policy = WebOutputPolicy.forProvider(provider)
        val fix = st.phase == SerialPhase.FIX
        val prompt = st.current ?: return
        val expectFiles = fix || prompt.expectFiles
        if (fix && st.fixRound > MAX_FIX_ROUNDS) {
            throw NeedUser("$MAX_FIX_ROUNDS düzeltme turunda build temizlenmedi. Günlüğe bakıp Devam et'e basın.")
        }

        val priorNonce = st.nonce
        val nonce = priorNonce ?: SerialMarkers.newNonce(random).also { n ->
            requireUpdate(id) { it.copy(nonce = n) }
        }
        var step = store.loadStep(id)
        val binaries = store.loadFiles(id)
        var delivered = step.parts.isNotEmpty() || step.zipParts.isNotEmpty() || step.markerSeen
        var maybeSent = priorNonce != null
        var reconcile = priorNonce != null
        var unauthorized = 0

        store.update(id) {
            it.copy(message = if (fix) "Düzeltme AI'a gönderiliyor…" else "AI yanıtı bekleniyor…")
        }

        while (true) {
            checkControl(id)
            try {
                val session = obtainSession(ctx, acc, provider)

                // 1) Sayfa yenilendiyse/süreç öldüyse: sohbette bu alışverişin yanıtı zaten var mı?
                if (reconcile) {
                    reconcile = false
                    val peek = session.peekLast()
                    if (peek != null && peek.status == WebReply.Status.OK && peek.text.isNotBlank()) {
                        val cleaned = clean(policy, peek.text)
                        val known = step.parts.any { it.trim() == cleaned.trim() }
                        if (!known && (delivered || SerialMarkers.belongs(cleaned, nonce))) {
                            step = accept(session, policy, nonce, id, step, peek, binaries)
                            delivered = true
                        }
                    }
                }

                // 2) İlk mesaj (yeni sohbetteyse projenin güncel hâliyle birlikte).
                if (!delivered) {
                    maybeSent = true
                    val message = firstMessage(session, provider, requireState(id), prompt, fix, nonce, expectFiles)
                    val reply = turn(session, message)
                    step = accept(session, policy, nonce, id, step, reply, binaries)
                    delivered = true
                }

                // 3) Yanıt gerçekten bitene kadar (bitiş işareti + kapanmış kod blokları).
                step = complete(session, policy, nonce, id, step, binaries)

                // 4) Dosya eksikse/yer tutucu varsa aynı sohbette onarım iste.
                var extracted = extract(step, binaries, nonce)
                if (expectFiles) {
                    while (true) {
                        val problem = inspect(extracted)
                        if (problem.ok || step.repairs >= policy.repairTurns) break
                        checkControl(id)
                        step = step.copy(repairs = step.repairs + 1, markerSeen = false, unmarkedAsks = 0, truncated = false)
                        store.saveStep(id, step)
                        store.log(id, "Eksik/yer tutuculu çıktı: onarım isteniyor (${step.repairs}/${policy.repairTurns})")
                        val reply = turn(session, SerialMarkers.wrap(provider, "", WebOutputCheck.repairPrompt(problem), nonce, true))
                        step = accept(session, policy, nonce, id, step, reply, binaries)
                        step = complete(session, policy, nonce, id, step, binaries)
                        extracted = extract(step, binaries, nonce)
                    }
                }

                finishExchange(ctx, fix, expectFiles, extracted)
                return
            } catch (e: AiGenerationException) {
                closeSession(ctx)
                reconcile = maybeSent || delivered
                when (e.kind) {
                    Kind.RATE_LIMITED -> {
                        unauthorized = 0
                        val wait = (e.retryAfterMs ?: LimitParser.DEFAULT_WAIT_MS).coerceIn(MIN_WAIT_MS, MAX_QUOTA_WAIT_MS)
                        store.update(id) { it.copy(message = "Kota doldu; yaklaşık ${minutes(wait)} dk sonra kaldığı yerden devam edilecek.") }
                        store.log(id, "Kota/sınır: ${minutes(wait)} dk bekleniyor")
                        if (wait >= LONG_WAIT_MS) store.get(id)?.let { notify(it, SerialEvent.QUOTA_WAIT) }
                        sleepChecked(id, wait)
                        store.update(id) { it.copy(message = "") }
                    }

                    Kind.UNAUTHORIZED -> {
                        unauthorized++
                        if (unauthorized >= MAX_UNAUTHORIZED) {
                            throw NeedUser("Hesabın oturumu düştü. Yapay Zekâlar bölümünden yeniden giriş yapıp Devam et'e basın.")
                        }
                        store.log(id, "Oturum doğrulanamadı; yeniden denenecek ($unauthorized/$MAX_UNAUTHORIZED)")
                        sleepChecked(id, errorDelayMs())
                    }

                    else -> {
                        unauthorized = 0
                        val d = errorDelayMs()
                        store.update(id) { it.copy(message = "Hata (${e.kind}); ${d / 1000} sn sonra sayfa yenilenip yeniden denenecek.") }
                        store.log(id, "Hata (${e.kind}); ${d / 1000} sn sonra yeniden denenecek")
                        sleepChecked(id, d)
                    }
                }
            } catch (e: SerialGitException) {
                if (e.fatal) throw NeedUser(e.message.orEmpty())
                val d = errorDelayMs()
                store.log(id, "${e.message} ${d / 1000} sn sonra yeniden denenecek")
                sleepChecked(id, d)
            }
        }
    }

    /** Alışveriş bitti: dosyalar diske yazılır ve aşama COMMIT'e geçer (dosya yoksa uygun yere). */
    private suspend fun finishExchange(ctx: Ctx, fix: Boolean, expectFiles: Boolean, files: Map<String, ByteArray>) {
        val id = ctx.id
        if (files.isEmpty()) {
            store.clearFiles(id)
            store.clearStep(id)
            if (fix) {
                store.log(id, "Düzeltme yanıtında dosya yok; düzeltme yeniden istenecek")
                store.update(id) { it.copy(nonce = null, fixRound = it.fixRound + 1) }
            } else {
                if (expectFiles) store.log(id, "Uyarı: bu adımdan dosya gelmedi; commit atlandı")
                advance(id)
            }
            releaseIfChatKnown(ctx)
            return
        }
        store.saveFiles(id, files)
        store.update(id) { it.copy(phase = SerialPhase.COMMIT, nonce = null, message = "") }
        store.clearStep(id)
        releaseIfChatKnown(ctx)
    }

    /** Sohbetin adresi biliniyorsa WebView serbest bırakılır (sonra aynı sohbet adresiyle yeniden açılır). */
    private suspend fun releaseIfChatKnown(ctx: Ctx) {
        if (store.get(ctx.id)?.chatUrl != null) closeSession(ctx)
    }

    private suspend fun obtainSession(ctx: Ctx, acc: AiAccount, provider: WebProvider): WebChatSession {
        ctx.session?.let { return it }
        var st = requireState(ctx.id)
        if (!st.needsProject && st.chatUrl == null) {
            // Sohbet adresi bilinmiyor: yeni sohbet açılır; bağlam kaybolmasın diye projenin güncel hâli yeniden verilir.
            st = requireUpdate(ctx.id) { it.copy(needsProject = true) }
            store.log(ctx.id, "Sohbet adresi bilinmiyor: yeni sohbet açılıyor, proje yeniden verilecek")
        }
        val url = st.chatUrl
        val s = if (st.needsProject || url == null) {
            if (url != null) requireUpdate(ctx.id) { it.copy(chatUrl = null) }
            driver.open(acc, provider)
        } else {
            driver.resume(acc, provider, url)
        }
        ctx.session = s
        return s
    }

    private suspend fun closeSession(ctx: Ctx) {
        val s = ctx.session ?: return
        ctx.session = null
        // İptal sırasında da çalışmalı: aksi hâlde hesap kilidi açılmaz.
        withContext(NonCancellable) { runCatching { s.close() } }
    }

    /** Sohbete giden İLK mesaj. Yeni sohbetteyse projenin güncel hâli (zip ya da metin) eklenir. */
    private suspend fun firstMessage(
        session: WebChatSession,
        provider: WebProvider,
        st: SerialState,
        prompt: SerialPrompt,
        fix: Boolean,
        nonce: String,
        expectFiles: Boolean,
    ): String {
        var header = ""
        if (st.needsProject) {
            val project = git.snapshot(st.repo, st.branch)
            val attached = provider.output == OutputStyle.ZIP_FILE && project.isNotEmpty() &&
                session.attach("project.zip", RepoZip.toZip(project))
            header = if (attached) HANDOFF_ATTACHED else HANDOFF_TEXT + "\n\n" + RepoZip.dump(project)
            store.log(
                st.id,
                if (attached) "Projenin güncel hâli zip olarak sohbete eklendi (${project.size} dosya)" else "Projenin güncel hâli metin olarak sohbete verilecek (${project.size} dosya)",
            )
        }
        if (!fix) return SerialMarkers.wrap(provider, header, prompt.body, nonce, expectFiles)
        val kind = st.pendingKind ?: "E"
        val head = listOf(header, SerialMarkers.fixHead(kind)).filter { it.isNotBlank() }.joinToString("\n\n")
        return SerialMarkers.wrap(provider, head, SerialMarkers.fixBody(kind, st.pendingLog.orEmpty()), nonce, true)
    }

    /** Yanıt bitene kadar "devam et" / "bitti mi" turları gönderir. */
    private suspend fun complete(
        session: WebChatSession,
        policy: WebOutputPolicy,
        nonce: String,
        id: Long,
        stepIn: SerialStep,
        binaries: LinkedHashMap<String, ByteArray>,
    ): SerialStep {
        var step = stepIn
        var guard = 0
        while (!isFinished(step)) {
            checkControl(id)
            guard++
            if (guard > MAX_CONTINUES) throw NeedUser("Yanıt $MAX_CONTINUES devam turunda tamamlanamadı.")
            val cut = step.truncated || ContinuationMerger.endsInsideFence(ContinuationMerger.merge(step.parts))
            val message = if (cut) {
                SerialMarkers.continuation(nonce)
            } else {
                step = step.copy(unmarkedAsks = step.unmarkedAsks + 1)
                store.saveStep(id, step)
                SerialMarkers.finishCheck(nonce)
            }
            val reply = turn(session, message)
            step = accept(session, policy, nonce, id, step, reply, binaries)
        }
        return step
    }

    private fun isFinished(step: SerialStep): Boolean {
        if (step.truncated) return false
        if (ContinuationMerger.endsInsideFence(ContinuationMerger.merge(step.parts))) return false
        return step.markerSeen || step.unmarkedAsks >= MAX_UNMARKED_ASKS
    }

    /** Bir yanıtı alışverişe ekler: metni, indirilen zip'leri ve ikili dosyaları; her şey diske yazılır. */
    private suspend fun accept(
        session: WebChatSession,
        policy: WebOutputPolicy,
        nonce: String,
        id: Long,
        step: SerialStep,
        reply: WebReply,
        binaries: LinkedHashMap<String, ByteArray>,
    ): SerialStep {
        val text = clean(policy, reply.text)
        val zipParts = ArrayList(step.zipParts)
        var binariesChanged = false
        for (url in reply.fileUrls) {
            val file = session.download(url) ?: continue
            val converted = ZipBlocks.convert(file)
            if (converted.text.isNotBlank()) zipParts += converted.text
            for ((path, bytes) in ZipBlocks.binaries(file)) {
                binaries[path] = bytes
                binariesChanged = true
            }
        }
        val next = step.copy(
            parts = if (text.isBlank()) step.parts else step.parts + text,
            zipParts = zipParts,
            markerSeen = step.markerSeen || SerialMarkers.isComplete(text, nonce),
            truncated = reply.truncated,
        )
        if (binariesChanged) store.saveFiles(id, binaries)
        store.saveStep(id, next)
        if (requireState(id).needsProject) captureChatUrl(session, id)
        return next
    }

    /** İlk yanıt geldikten sonra sohbetin adresini saklar; sonraki her açılışta AYNI sohbete dönülür. */
    private suspend fun captureChatUrl(session: WebChatSession, id: Long) {
        var found: String? = null
        repeat(3) {
            if (found != null) return@repeat
            found = session.chatUrl()
            if (found == null) sleep(2_000L)
        }
        val url = found
        store.update(id) { it.copy(needsProject = false, chatUrl = url ?: it.chatUrl) }
        if (url == null) store.log(id, "Uyarı: sohbet adresi okunamadı; oturum açık tutulacak")
    }

    private suspend fun turn(session: WebChatSession, message: String): WebReply {
        val reply = session.send(message)
        return when (reply.status) {
            WebReply.Status.OK -> {
                if (reply.text.length < SHORT_LIMIT_TEXT && LimitParser.looksLikeLimit(reply.text)) throw limited(reply.text)
                reply
            }

            WebReply.Status.RATE_LIMITED -> throw limited(reply.limitText)

            WebReply.Status.NEEDS_LOGIN -> throw AiGenerationException(Kind.UNAUTHORIZED)

            WebReply.Status.TIMEOUT, WebReply.Status.ERROR -> throw AiGenerationException(Kind.NETWORK)
        }
    }

    private fun limited(text: String?) = AiGenerationException(Kind.RATE_LIMITED, retryAfterMs = LimitParser.waitMs(text, now()))

    private fun clean(policy: WebOutputPolicy, text: String): String = OutputCleaner.clean(text, policy.aggressiveThinking, policy.specialTokens, policy.toolSections)

    // ------------------------------------------------------------------------------------------- çıktı çıkarma

    /** Alışverişten dosyalar (yol → bayt): önce ikili dosyalar, sonra sohbet blokları, en son zip blokları (sonraki öncekini ezer). */
    internal fun extract(step: SerialStep, binaries: Map<String, ByteArray>, nonce: String): LinkedHashMap<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        out.putAll(binaries)
        val chat = SerialMarkers.strip(ContinuationMerger.merge(step.parts), nonce)
        for (source in listOf(chat) + step.zipParts) {
            for (b in CodeBlockParser.analyze(source).blocks) {
                val raw = b.path ?: continue
                val safe = (PathSafety.validate(raw) as? PathSafety.Result.Safe)?.normalized ?: continue
                if (safe.split('/').any { it == ".git" }) continue
                if (b.content.isBlank()) continue
                out[safe] = (b.content.trimEnd('\n') + "\n").toByteArray(Charsets.UTF_8)
            }
        }
        return out
    }

    private fun inspect(files: Map<String, ByteArray>): WebOutputCheck.Inspection {
        val blocks = files.mapNotNull { (path, bytes) ->
            if (bytes.any { it == 0.toByte() }) null else CodeBlockParser.Block(path, String(bytes, Charsets.UTF_8))
        }
        val placeholders = PlaceholderDetector.find(blocks).map { it.path }.distinct()
        return WebOutputCheck.Inspection(noBlocks = files.isEmpty(), placeholderFiles = placeholders, pathlessCount = 0)
    }

    // ----------------------------------------------------------------------------------------------- commit / CI

    private suspend fun commitPhase(ctx: Ctx) {
        val id = ctx.id
        val st = requireState(id)
        val files = store.loadFiles(id)
        if (files.isEmpty()) {
            store.log(id, "Commit edilecek dosya yok; sonraki prompta geçiliyor")
            advance(id)
            return
        }
        val title = st.current?.title.orEmpty().take(60)
        val fixNote = if (st.fixRound > 0) " (CI düzeltmesi ${st.fixRound})" else ""
        val message = "Kripton Seri ${st.index + 1}/${st.total}: $title$fixNote"
        store.update(id) { it.copy(message = "Repoya commit atılıyor (${files.size} dosya)…") }
        val ghFiles = files.map { (path, bytes) -> GitHubFile(path, bytes) }
        val sha = retryGit(id, "commit") { git.commit(st.repo, st.branch, message, ghFiles) }
        store.clearFiles(id)
        store.update(id) { it.copy(phase = SerialPhase.CI, headSha = sha, message = "") }
        store.log(id, "Commit ${sha.take(7)}: ${files.size} dosya → ${st.repo}@${st.branch}")
    }

    private suspend fun ciPhase(ctx: Ctx) {
        val id = ctx.id
        val st = requireState(id)
        val sha = st.headSha
        if (sha == null) {
            advance(id)
            return
        }
        store.update(id) { it.copy(message = "GitHub Actions bekleniyor…") }
        val result = retryGit(id, "Actions") { git.awaitCi(st.repo, sha) { !isRunning(id) } }
        checkControl(id)
        when (result) {
            CiResult.Stopped -> checkControl(id)

            CiResult.NoRuns -> {
                store.log(id, "Bu commit için Actions çalışmadı; sonraki prompta geçiliyor")
                advance(id)
            }

            CiResult.TimedOut -> throw NeedUser("GitHub Actions beklenen sürede bitmedi. Actions sayfasına bakıp Devam et'e basın.")

            is CiResult.Passed -> when {
                result.warnings.isEmpty() -> {
                    store.log(id, "Build temiz: hata ve uyarı yok")
                    advance(id)
                }

                st.warnRound >= MAX_WARN_ROUNDS -> {
                    store.log(id, "${result.warnings.size} uyarı $MAX_WARN_ROUNDS turda giderilemedi; kabul edilip devam ediliyor")
                    advance(id)
                }

                else -> startFix(id, "W", result.warnings)
            }

            is CiResult.Failed -> {
                if (st.fixRound >= MAX_FIX_ROUNDS) {
                    throw NeedUser("$MAX_FIX_ROUNDS düzeltme turunda build temizlenmedi. Günlüğe bakıp Devam et'e basın.")
                }
                startFix(id, "E", result.errors + result.warnings.take(30))
            }
        }
    }

    /** Hata/uyarı günlüğünü AYNI sohbete gönderilmek üzere hazırlar. */
    private fun startFix(id: Long, kind: String, lines: List<String>) {
        val text = CiLogScan.render(lines)
        store.clearStep(id)
        store.clearFiles(id)
        store.update(id) {
            it.copy(
                phase = SerialPhase.FIX,
                pendingLog = text,
                pendingKind = kind,
                nonce = null,
                fixRound = it.fixRound + 1,
                warnRound = if (kind == "W") it.warnRound + 1 else it.warnRound,
                message = "",
            )
        }
        store.log(id, "CI ${if (kind == "W") "uyarıları" else "hataları"} bulundu (${lines.size} satır); aynı sohbete gönderilecek")
    }

    /** Geçici GitHub hatalarında bekleyip yeniden dener; kalıcı hatada kullanıcıya bırakır. */
    private suspend fun <T> retryGit(id: Long, what: String, block: suspend () -> T): T {
        var failures = 0
        while (true) {
            checkControl(id)
            try {
                return block()
            } catch (e: SerialGitException) {
                if (e.fatal) throw NeedUser(e.message.orEmpty())
                failures++
                if (failures >= MAX_GIT_FAILURES) throw NeedUser("${e.message} ($failures deneme)")
                val d = minOf(errorDelayMs() * failures, MAX_GIT_BACKOFF_MS)
                store.log(id, "$what: ${e.message} ${d / 1000} sn sonra yeniden denenecek")
                sleepChecked(id, d)
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ yardımcılar

    private fun requireState(id: Long): SerialState = store.get(id) ?: throw StopRun()

    private fun requireUpdate(id: Long, transform: (SerialState) -> SerialState): SerialState = store.update(id, transform) ?: throw StopRun()

    private fun isRunning(id: Long): Boolean = store.get(id)?.status == SerialStatus.RUNNING

    private fun checkControl(id: Long) {
        if (!isRunning(id)) throw StopRun()
    }

    /** [ms] boyunca bekler; her dilimde duraklat/iptal kontrol edilir. */
    private suspend fun sleepChecked(id: Long, ms: Long) {
        val end = now() + ms
        while (true) {
            checkControl(id)
            val left = end - now()
            if (left <= 0) return
            sleep(minOf(left, SLICE_MS))
        }
    }

    private fun minutes(ms: Long): Long = (ms + 59_999L) / 60_000L

    companion object {
        const val MAX_CONTINUES = 60
        const val MAX_UNMARKED_ASKS = 2
        const val MAX_FIX_ROUNDS = 12
        const val MAX_WARN_ROUNDS = 3
        const val MAX_GIT_FAILURES = 12
        const val MAX_UNAUTHORIZED = 3
        private const val SHORT_LIMIT_TEXT = 400
        private const val MIN_WAIT_MS = 60_000L
        private const val MAX_QUOTA_WAIT_MS = 24L * 3_600_000L
        private const val LONG_WAIT_MS = 10L * 60_000L
        private const val MAX_GIT_BACKOFF_MS = 10L * 60_000L
        private const val SLICE_MS = 3_000L

        const val HANDOFF_ATTACHED =
            "[Kripton] This chat starts with the CURRENT state of the whole project, attached as project.zip. " +
                "Unzip it and treat it as the single source of truth. Keep working in THIS chat from now on."

        const val HANDOFF_TEXT =
            "[Kripton] This chat starts with the CURRENT state of the whole project, given below as fenced file blocks. " +
                "Treat it as the single source of truth. Keep working in THIS chat from now on."
    }
}
