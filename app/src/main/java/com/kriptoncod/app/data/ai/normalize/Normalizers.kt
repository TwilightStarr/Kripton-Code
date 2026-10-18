package com.kriptoncod.app.data.ai.normalize

import com.kriptoncod.app.data.providers.UpstreamProvider

/**
 * Ortak akış: görünür metni temizle (muhakeme, özel belirteçler, sarmalayıcı çit) → `finish_reason`'ı sınıfla.
 * Aileler yalnız farklılıklarını bildirir; yeni bir aile eklemek için bu sınıfı genişletmek yeter.
 */
open class BaseNormalizer(
    final override val family: UpstreamProvider,
    extraTruncated: Set<String> = emptySet(),
    extraFiltered: Set<String> = emptySet(),
    private val aggressiveThinking: Boolean = false,
    private val specialTokens: Boolean = false,
    private val toolSections: Boolean = false,
) : ProviderNormalizer {

    private val truncated = COMMON_TRUNCATED + extraTruncated
    private val filtered = COMMON_FILTERED + extraFiltered

    override fun normalize(raw: RawChoice): NormalizedOutput {
        val text = OutputCleaner.clean(raw.visibleText.orEmpty(), aggressiveThinking, specialTokens, toolSections)
        val finish = when {
            text.isEmpty() && raw.refusal != null -> Finish.FILTERED
            else -> classify(raw.finishReason)
        }
        return NormalizedOutput(text, finish)
    }

    /** `finish_reason` büyük/küçük harfe duyarsızdır (Gemini `MAX_TOKENS`, `SAFETY` döndürür). */
    protected fun classify(reason: String?): Finish {
        val r = reason?.trim()?.lowercase().orEmpty()
        return when {
            r in truncated -> Finish.TRUNCATED
            r in filtered -> Finish.FILTERED
            else -> Finish.COMPLETE
        }
    }

    private companion object {
        val COMMON_TRUNCATED = setOf("length", "max_tokens", "max_output_tokens", "model_length", "pause_turn")
        val COMMON_FILTERED = setOf("content_filter", "safety", "refusal", "recitation", "blocklist", "prohibited_content", "spii")
    }
}

/** Claude: parça listesi + thinking parçaları [ChoiceReader]'da ayrılır; `max_tokens`/`pause_turn` kesik, `refusal` reddedilmiş. */
object ClaudeNormalizer : BaseNormalizer(UpstreamProvider.CLAUDE)

/** Gemini: `MAX_TOKENS`, `SAFETY`, `RECITATION` vb. büyük harfli sonlanma nedenleri; `<thought>` blokları. */
object GeminiNormalizer : BaseNormalizer(UpstreamProvider.GEMINI)

/** OpenAI (GPT/o-serisi/Codex): `length`, `content_filter`, `refusal` alanı. */
object OpenAiNormalizer : BaseNormalizer(UpstreamProvider.OPENAI)

/**
 * DeepSeek: `reasoning_content` ayrı alandır; bazı sunucular açılışsız `</think>` ve `<｜…｜>` özel belirteçleri sızdırır;
 * `insufficient_system_resource` yanıtın yarıda kesildiği anlamına gelir.
 */
object DeepSeekNormalizer : BaseNormalizer(
    UpstreamProvider.DEEPSEEK,
    extraTruncated = setOf("insufficient_system_resource"),
    aggressiveThinking = true,
    specialTokens = true,
)

/** Kimi (Moonshot): `<think>` blokları, `<|…|>` özel belirteçleri ve araç çağrısı bölümleri yanıttan atılır. */
object KimiNormalizer : BaseNormalizer(
    UpstreamProvider.KIMI,
    aggressiveThinking = true,
    specialTokens = true,
    toolSections = true,
)

/** Tanınmayan modeller (Llama, Qwen, Grok vb.): yalnız ortak temizlik. */
object GenericNormalizer : BaseNormalizer(UpstreamProvider.OTHER)

object Normalizers {
    /**
     * Normalizer'ı **model kimliğine** göre seçer; kimlik tanınmazsa hesabın sağlayıcısına ([hint]),
     * o da yoksa [GenericNormalizer]'a düşer. (Proxy arkasında "claude" hesabı gemini modeli seçse bile
     * model kazanır.)
     */
    fun forModel(modelId: String, hint: UpstreamProvider? = null): ProviderNormalizer = when (UpstreamProvider.fromModelId(modelId) ?: hint ?: UpstreamProvider.OTHER) {
        UpstreamProvider.CLAUDE -> ClaudeNormalizer
        UpstreamProvider.GEMINI -> GeminiNormalizer
        UpstreamProvider.OPENAI -> OpenAiNormalizer
        UpstreamProvider.DEEPSEEK -> DeepSeekNormalizer
        UpstreamProvider.KIMI -> KimiNormalizer
        UpstreamProvider.OTHER -> GenericNormalizer
    }
}
