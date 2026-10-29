package com.kriptoncod.app.data.web

import com.kriptoncod.app.data.ai.AiGenerationException
import com.kriptoncod.app.data.ai.AiGenerationException.Kind
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.files.RawReplyLog
import com.kriptoncod.app.data.run.OutputTriage
import com.kriptoncod.app.data.ai.normalize.OutputCleaner
import com.kriptoncod.app.data.run.CodeBlockParser

/**
 * [com.kriptoncod.app.domain.AuthType.WEB_SESSION] hesapları için üretici: promptu sağlayıcının
 * çıktı biçimine göre sarar, web sohbetinden yanıtı alır ve projenin standart kod bloğu metnine çevirir.
 *
 *  - ZIP_FILE (Claude, ChatGPT): önce zip istenir; indirilen zip bloklara açılır. Dosya gelmezse
 *    aynı sohbette kod bloğu istenir.
 *  - LONG_CODE_BLOCK (Kimi): kod bloğu istenir; kesilirse "devam et" gönderilip parçalar birleştirilir.
 *  - API_KEY (Gemini): web arayüzü kullanılmaz; bu üreticiye gelmemelidir.
 *
 * Hatalar [AiGenerationException]'a çevrilir; böylece [com.kriptoncod.app.data.run.PromptDispatcher]
 * bekleme (RATE_LIMITED), hesap değiştirme (UNAUTHORIZED) ve yeniden deneme (NETWORK) kurallarını aynen uygular.
 */
class WebSessionGenerator(
    private val driver: WebChatDriver,
    private val pacer: WebPacer = WebPacer(),
    private val now: () -> Long = System::currentTimeMillis,
    private val maxContinues: Int = 40,
    /** AI yanıtlarının ilk durağı: yanıt gelir gelmez ham haliyle yazılır; üretim bitince yalnız kod arşivi kalır. null: saklama yok. */
    private val rawLog: RawReplyLog? = null,
) {

    /**
     * @param expectFiles true: çıktı dosya bloklarıdır (WORK/birleştirme); false: düz metin (PLAN) — sarmalama yapılmaz.
     * @param onAsset indirilen ham dosyalar (orijinal zip) ve metne çevrilemeyen ikili dosyalar için geri çağrı.
     */
    suspend fun generate(
        account: AiAccount,
        prompt: String,
        expectFiles: Boolean = true,
        onAsset: (WebAsset) -> Unit = {},
    ): String {
        val provider = WebProvider.fromId(account.providerId)
        if (provider == null || provider.output == OutputStyle.API_KEY) {
            throw AiGenerationException(Kind.BAD_REQUEST)
        }
        val policy = WebOutputPolicy.forProvider(provider)
        pacer.await(account.id)
        val tag = "web-${account.id}-${now()}"
        val opened = driver.open(account, provider)
        val session = if (rawLog == null) opened else ArchivingSession(opened, rawLog, tag)
        try {
            if (!expectFiles) return plainText(session, policy, prompt).also { finishArchive(tag, keepProse = true, polished = null, onAsset = onAsset) }
            val raw = when (provider.output) {
                OutputStyle.ZIP_FILE -> zipFlow(session, provider, policy, prompt, onAsset)
                OutputStyle.LONG_CODE_BLOCK -> blockFlow(session, provider, policy, prompt, onAsset)
                OutputStyle.API_KEY -> throw AiGenerationException(Kind.BAD_REQUEST)
            }
            val polished = polish(session, policy, raw)
            finishArchive(tag, keepProse = false, polished = polished, onAsset = onAsset)
            return polished
        } finally {
            runCatching { session.close() }
        }
    }

    /**
     * Üretim başarıyla bitti: ham yanıtlar sıkıştırılır (kod korunur) ve geçici klasör kaldırılır; kalıcı kopya çalışmanın
     * kendi çıktısındadır (`_kripton/raw/` varlıkları). Hata olursa ham dosyalar KALIR ([RawReplyLog.sweep] sonra ayıklar).
     */
    private fun finishArchive(tag: String, keepProse: Boolean, polished: String?, onAsset: (WebAsset) -> Unit) {
        val log = rawLog ?: return
        runCatching {
            log.compact(tag, keepProse)
            if (!keepProse && polished != null) {
                // Çalışma metnine giren bloklardan fazlası varsa (eski sürümler, yinelenenler, onarım öncesi kısa sürümler)
                // ham yanıtlardaki TÜM kod blokları birebir arşive konur; kod hiçbir koşulda kaybolmaz.
                val all = log.files(tag).filter { it.name.endsWith(".code.md") }.joinToString("\n\n") { it.readText(Charsets.UTF_8) }
                if (OutputTriage.blockCount(all) > OutputTriage.blockCount(polished)) {
                    onAsset(WebAsset("_kripton/raw/replies-code.md", all.toByteArray(Charsets.UTF_8)))
                }
            }
            // Ham dosya doğrulanamadığı için kalmışsa klasör silinmez (sweep sonra ayıklar).
            if (log.pending(tag) == 0) log.delete(tag)
        }
    }

    /** Her yanıtı işlenmeden ÖNCE diske yazan, indirilen özgün dosyaları da saklayan sarmalayıcı. */
    private class ArchivingSession(
        private val inner: WebChatSession,
        private val log: RawReplyLog,
        private val tag: String,
    ) : WebChatSession by inner {
        override suspend fun send(prompt: String): WebReply {
            val reply = inner.send(prompt)
            runCatching { log.save(tag, reply.text) }
            return reply
        }

        override suspend fun download(url: String): WebFile? =
            inner.download(url)?.also { f -> runCatching { log.saveFile(tag, f.name, f.bytes) } }
    }

    private fun clean(policy: WebOutputPolicy, text: String): String = OutputCleaner.clean(text, policy.aggressiveThinking, policy.specialTokens, policy.toolSections)

    private suspend fun plainText(session: WebChatSession, policy: WebOutputPolicy, prompt: String): String {
        val reply = turn(session, prompt)
        return clean(policy, reply.text).ifBlank { throw AiGenerationException(Kind.BAD_RESPONSE) }
    }

    private suspend fun zipFlow(
        session: WebChatSession,
        provider: WebProvider,
        policy: WebOutputPolicy,
        prompt: String,
        onAsset: (WebAsset) -> Unit,
    ): String {
        val first = turn(session, PromptWrapper.wrap(provider, prompt, PromptWrapper.Mode.ZIP))
        filesText(session, first, onAsset)?.let { return it }
        if (hasBlocks(first.text)) return completeBlocks(session, policy, first)
        // Dosya üretilmedi ve kod bloğu da yok: aynı sohbette bloklarla yeniden iste.
        val second = turn(session, PromptWrapper.wrap(provider, prompt, PromptWrapper.Mode.CODE_BLOCKS))
        filesText(session, second, onAsset)?.let { return it }
        return completeBlocks(session, policy, second)
    }

    private suspend fun blockFlow(
        session: WebChatSession,
        provider: WebProvider,
        policy: WebOutputPolicy,
        prompt: String,
        onAsset: (WebAsset) -> Unit,
    ): String {
        val reply = turn(session, PromptWrapper.wrap(provider, prompt, PromptWrapper.Mode.CODE_BLOCKS))
        filesText(session, reply, onAsset)?.let { return it }
        return completeBlocks(session, policy, reply)
    }

    /** Kesilen yanıtı "devam et" turlarıyla tamamlar; hâlâ yarımsa kesik kod kaydedilmez (TRUNCATED). */
    private suspend fun completeBlocks(session: WebChatSession, policy: WebOutputPolicy, first: WebReply): String {
        var reply = first
        val parts = mutableListOf(clean(policy, reply.text))
        var continues = 0
        while (policy.continueOnTruncated && continues < maxContinues &&
            (reply.truncated || ContinuationMerger.endsInsideFence(merged(parts)))
        ) {
            continues++
            reply = turn(session, PromptWrapper.continuation())
            val piece = clean(policy, reply.text)
            if (piece.isBlank()) break
            parts += piece
        }
        val text = merged(parts)
        if (ContinuationMerger.endsInsideFence(text)) throw AiGenerationException(Kind.TRUNCATED)
        return text.ifBlank { throw AiGenerationException(Kind.BAD_RESPONSE) }
    }

    /**
     * Yanıtı standart dosya-bloğu biçimine getirir. Yer tutucu veya blok eksikliği varsa aynı sohbette
     * sağlayıcıya özel sayıda onarım turu ister; tükenirse eldeki en iyi metin döner (uyarı rapora düşer).
     */
    private suspend fun polish(session: WebChatSession, policy: WebOutputPolicy, raw: String): String {
        var text = WebOutputCheck.canonical(raw)
        var repairs = 0
        while (repairs < policy.repairTurns) {
            val issue = WebOutputCheck.inspect(text)
            if (issue.ok) break
            repairs++
            val reply = try {
                turn(session, WebOutputCheck.repairPrompt(issue))
            } catch (e: AiGenerationException) {
                if (e.kind == Kind.RATE_LIMITED || e.kind == Kind.UNAUTHORIZED) throw e
                break
            }
            val more = try {
                completeBlocks(session, policy, reply)
            } catch (e: AiGenerationException) {
                if (e.kind == Kind.TRUNCATED || e.kind == Kind.BAD_RESPONSE) break
                throw e
            }
            // Onarılmış dosyalar aynı yolun eski (kısaltılmış) sürümünün yerine geçer.
            text = WebOutputCheck.canonical(text + "\n\n" + more)
        }
        return text
    }

    private fun merged(parts: List<String>) = ContinuationMerger.merge(parts)

    private fun hasBlocks(text: String) = CodeBlockParser.analyze(text).blocks.isNotEmpty()

    /**
     * Yanıttaki indirme bağlantılarını indirip bloklara çevirir; kullanılabilir dosya yoksa null.
     * Orijinal zip `_kripton/raw/` altına, metne çevrilemeyen ikili dosyalar özgün yollarına [onAsset] ile verilir.
     */
    private suspend fun filesText(session: WebChatSession, reply: WebReply, onAsset: (WebAsset) -> Unit): String? {
        if (reply.fileUrls.isEmpty()) return null
        val sb = StringBuilder()
        for (url in reply.fileUrls) {
            val file = session.download(url) ?: continue
            val converted = ZipBlocks.convert(file)
            if (converted.text.isNotBlank()) sb.append(converted.text).append("\n\n")
            rawName(file.name)?.let { onAsset(WebAsset("_kripton/raw/$it", file.bytes)) }
            ZipBlocks.binaries(file).forEach { (path, bytes) -> onAsset(WebAsset(path, bytes)) }
        }
        return sb.toString().trim().ifEmpty { null }
    }

    private fun rawName(name: String): String? {
        val n = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9_.-]"), "_").trim('.', '_')
        return n.takeIf { it.isNotEmpty() }
    }

    /** Bir turu gönderir ve durum kodlarını [AiGenerationException]'a çevirir. */
    private suspend fun turn(session: WebChatSession, prompt: String): WebReply {
        val reply = session.send(prompt)
        when (reply.status) {
            WebReply.Status.OK -> {
                if (reply.text.length < SHORT_LIMIT_TEXT && LimitParser.looksLikeLimit(reply.text)) {
                    throw limited(reply.text)
                }
                return reply
            }

            WebReply.Status.RATE_LIMITED -> throw limited(reply.limitText)

            WebReply.Status.NEEDS_LOGIN -> throw AiGenerationException(Kind.UNAUTHORIZED)

            WebReply.Status.TIMEOUT, WebReply.Status.ERROR -> throw AiGenerationException(Kind.NETWORK)
        }
    }

    private fun limited(text: String?): AiGenerationException = AiGenerationException(Kind.RATE_LIMITED, retryAfterMs = LimitParser.waitMs(text, now()))

    private companion object {
        const val SHORT_LIMIT_TEXT = 400
    }
}
