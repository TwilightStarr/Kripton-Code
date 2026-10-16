package com.kriptoncod.app.data.ai

import com.kriptoncod.app.data.ai.AiGenerationException.Kind
import com.kriptoncod.app.data.providers.ProviderEndpoint
import kotlinx.coroutines.delay

/**
 * Eksik veya bozuk çıktıyı aynı hesapla otomatik yeniden dener.
 *
 * Yeniden denenenler (en çok [maxAttempts] istek):
 *  - [Kind.BAD_RESPONSE]: 2xx ama içerik yok/çözülemedi/reddedildi,
 *  - [Kind.TRUNCATED]: çıktı uzunluk sınırında kesildi,
 *  - başarılı ama [OutputValidator]'dan geçmeyen metin (kapanmamış kod bloğu, istenen kod bloklarının yokluğu).
 * Sonraki denemelerde prompt'un sonuna ne eksik olduğunu söyleyen kısa bir düzeltme notu eklenir.
 *
 * Bilerek dokunulmayanlar: [Kind.UNAUTHORIZED]/[Kind.RATE_LIMITED] (hesap değiştirme/bekleme kararı
 * dağıtıcınındır), [Kind.SERVER]/[Kind.NETWORK] (dağıtıcı iş düzeyinde artan beklemeyle yeniden kuyruğa alır).
 *
 * Tükenince son sorunun türüyle fırlatır: kesik/kapanmamış → [Kind.TRUNCATED], diğerleri → [Kind.BAD_RESPONSE];
 * eksik kod ASLA döndürülmez.
 */
class RetryingAiGenerator(
    private val delegate: AiGenerator,
    private val maxAttempts: Int = DEFAULT_ATTEMPTS,
    /** [attempt] 1'den başlar (1. denemeden sonraki bekleme). Testlerde bekleme kapatılır. */
    private val backoff: suspend (attempt: Int) -> Unit = { delay(it * BACKOFF_STEP_MS) },
) : AiGenerator {

    init {
        require(maxAttempts >= 1) { "maxAttempts en az 1 olmalı" }
    }

    override suspend fun generate(
        endpoint: ProviderEndpoint,
        modelId: String,
        credential: String,
        prompt: String,
    ): String {
        val expectCode = OutputValidator.expectsCodeBlocks(prompt)
        var effectivePrompt = prompt
        var lastKind = Kind.BAD_RESPONSE
        var lastCause: AiGenerationException? = null

        for (attempt in 1..maxAttempts) {
            val problem: Problem = try {
                val text = delegate.generate(endpoint, modelId, credential, effectivePrompt)
                val bad = OutputValidator.check(text, expectCode) ?: return text
                Problem.Validation(bad)
            } catch (e: AiGenerationException) {
                if (e.kind != Kind.BAD_RESPONSE && e.kind != Kind.TRUNCATED) throw e
                Problem.Failure(e)
            }

            lastKind = problem.kind
            lastCause = (problem as? Problem.Failure)?.error
            effectivePrompt = prompt + correction(problem)
            if (attempt < maxAttempts) backoff(attempt)
        }
        throw AiGenerationException(lastKind, lastCause?.httpCode, lastCause?.retryAfterMs)
    }

    private sealed interface Problem {
        val kind: Kind

        data class Failure(val error: AiGenerationException) : Problem {
            override val kind: Kind get() = error.kind
        }

        data class Validation(val reason: OutputValidator.Problem) : Problem {
            override val kind: Kind
                get() = if (reason == OutputValidator.Problem.UNCLOSED_FENCE) Kind.TRUNCATED else Kind.BAD_RESPONSE
        }
    }

    private fun correction(problem: Problem): String = "\n\n" + when {
        problem.kind == Kind.TRUNCATED ->
            "[Not: Önceki yanıt uzunluk sınırında yarıda kesildi. Bu sefer gereksiz açıklamayı çıkar ve " +
                "istenen tüm dosyaları EKSİKSİZ, her kod bloğu kapatılmış olarak ver.]"
        problem is Problem.Validation && problem.reason == OutputValidator.Problem.MISSING_CODE_BLOCKS ->
            "[Not: Önceki yanıtta ```tam/dosya/yolu biçiminde kod bloğu yoktu. Dosyaları bu biçimde " +
                "kod bloklarıyla, açıklama yazmadan döndür.]"
        else ->
            "[Not: Önceki yanıt boş veya okunamaz geldi. Lütfen isteği eksiksiz biçimde yeniden yanıtla.]"
    }

    companion object {
        const val DEFAULT_ATTEMPTS = 3
        private const val BACKOFF_STEP_MS = 2_000L
    }
}
