package com.kriptoncod.app.data.web

import com.kriptoncod.app.data.ai.AiGenerationException
import com.kriptoncod.app.data.ai.AiGenerationException.Kind
import com.kriptoncod.app.data.db.AiAccount
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
    private val maxContinues: Int = 8,
) {

    /**
     * @param expectFiles true: çıktı dosya bloklarıdır (WORK/birleştirme); false: düz metin (PLAN) — sarmalama yapılmaz.
     */
    suspend fun generate(account: AiAccount, prompt: String, expectFiles: Boolean = true): String {
        val provider = WebProvider.fromId(account.providerId)
        if (provider == null || provider.output == OutputStyle.API_KEY) {
            throw AiGenerationException(Kind.BAD_REQUEST)
        }
        pacer.await(account.id)
        val session = driver.open(account, provider)
        try {
            if (!expectFiles) return plainText(session, prompt)
            return when (provider.output) {
                OutputStyle.ZIP_FILE -> zipFlow(session, provider, prompt)
                OutputStyle.LONG_CODE_BLOCK -> blockFlow(session, provider, prompt)
                OutputStyle.API_KEY -> throw AiGenerationException(Kind.BAD_REQUEST)
            }
        } finally {
            runCatching { session.close() }
        }
    }

    private suspend fun plainText(session: WebChatSession, prompt: String): String {
        val reply = turn(session, prompt)
        return reply.text.ifBlank { throw AiGenerationException(Kind.BAD_RESPONSE) }
    }

    private suspend fun zipFlow(session: WebChatSession, provider: WebProvider, prompt: String): String {
        val first = turn(session, PromptWrapper.wrap(provider, prompt, PromptWrapper.Mode.ZIP))
        filesText(session, first)?.let { return it }
        if (hasBlocks(first.text)) return finish(first.text)
        // Dosya üretilmedi ve kod bloğu da yok: aynı sohbette bloklarla yeniden iste.
        val second = turn(session, PromptWrapper.wrap(provider, prompt, PromptWrapper.Mode.CODE_BLOCKS))
        filesText(session, second)?.let { return it }
        return finish(second.text)
    }

    private suspend fun blockFlow(session: WebChatSession, provider: WebProvider, prompt: String): String {
        var reply = turn(session, PromptWrapper.wrap(provider, prompt, PromptWrapper.Mode.CODE_BLOCKS))
        filesText(session, reply)?.let { return it }
        val parts = mutableListOf(reply.text)
        var continues = 0
        while (continues < maxContinues && (reply.truncated || ContinuationMerger.endsInsideFence(merged(parts)))) {
            continues++
            reply = turn(session, PromptWrapper.continuation())
            if (reply.text.isBlank()) break
            parts += reply.text
        }
        val text = merged(parts)
        // Hâlâ yarımsa kesik kod kaydedilmez (projenin kuralı): yeniden denenebilir hata.
        if (ContinuationMerger.endsInsideFence(text)) throw AiGenerationException(Kind.TRUNCATED)
        return finish(text)
    }

    private fun merged(parts: List<String>) = ContinuationMerger.merge(parts)

    private fun finish(text: String): String =
        text.ifBlank { throw AiGenerationException(Kind.BAD_RESPONSE) }

    private fun hasBlocks(text: String) = CodeBlockParser.parse(text).isNotEmpty()

    /** Yanıttaki indirme bağlantılarını indirip bloklara çevirir; kullanılabilir dosya yoksa null. */
    private suspend fun filesText(session: WebChatSession, reply: WebReply): String? {
        if (reply.fileUrls.isEmpty()) return null
        val sb = StringBuilder()
        for (url in reply.fileUrls) {
            val file = session.download(url) ?: continue
            val converted = ZipBlocks.convert(file)
            if (converted.text.isNotBlank()) sb.append(converted.text).append("\n\n")
        }
        return sb.toString().trim().ifEmpty { null }
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

    private fun limited(text: String?): AiGenerationException =
        AiGenerationException(Kind.RATE_LIMITED, retryAfterMs = LimitParser.waitMs(text, now()))

    private companion object {
        const val SHORT_LIMIT_TEXT = 400
    }
}
