package com.kriptoncod.app.data.providers

/** Sağlayıcıdan gelen sohbet modeli. [id], sohbet isteğinde kullanılacak adın aynısıdır. */
data class RemoteModel(val id: String, val contextLength: Long? = null)

data class RankedModel(val model: RemoteModel, val score: Int)

/** [models] puana göre azalan sırada; [recommended] kod yazmaya en uygun görülen model. */
data class ModelRanking(val models: List<RankedModel>, val recommended: RemoteModel?)

/** Model kimliği ve bağlam uzunluğundan, kod yazma uygunluğuna göre sezgisel puan üretir. */
object ModelScorer {
    private const val BASE = 50

    private val EXCLUDED_SUBSTRINGS = listOf(
        "embed", "whisper", "speech", "audio", "transcri", "moderat", "guard", "rerank",
        "imagen", "dall-e", "diffusion", "orpheus", "playai",
    )
    private val EXCLUDED_TOKENS = setOf("tts", "ocr", "live", "flux", "veo", "image", "aqa", "sora", "realtime")
    private val SMALL_TOKENS = setOf("mini", "ministral", "lite", "nano", "tiny", "instant", "small", "haiku")
    private val UNSTABLE_TOKENS = setOf("preview", "exp", "experimental", "beta", "alpha")

    private val CODE = Regex("coder|codestral|devstral|code")
    private val TOP = Regex(
        "gpt-5|gpt-oss-120b|opus|sonnet|gemini-[0-9.]+-pro|deepseek-(reasoner|r1|v3|chat)|kimi-k2|" +
            "mistral-large|qwen3-(235b|max)|405b|llama-4-maverick|glm-4\\.[5-9]|(^|[/-])o[134]([-:]|$)",
    )
    private val SIZE = Regex("(?<![a-z0-9])(\\d{1,4})b(?![a-z0-9])")
    private val SPLIT = Regex("[^a-z0-9]+")

    /** Sohbet/kod için uygun değilse (embedding, ses, görsel vb.) null. */
    fun score(model: RemoteModel): Int? {
        val id = model.id.lowercase().removePrefix("models/")
        if (id.isBlank() || EXCLUDED_SUBSTRINGS.any { it in id }) return null
        val tokens = id.split(SPLIT).filter { it.isNotEmpty() }.toSet()
        if (tokens.any { it in EXCLUDED_TOKENS }) return null

        var score = BASE
        score += when {
            CODE.containsMatchIn(id) -> 30
            TOP.containsMatchIn(id) -> 25
            else -> 0
        }
        SIZE.find(id)?.groupValues?.get(1)?.toIntOrNull()?.let { b ->
            score += if (b <= 9) -10 else minOf(b / 10, 20)
        }
        if (tokens.any { it in SMALL_TOKENS }) score -= 15
        if (tokens.any { it in UNSTABLE_TOKENS }) score -= 5
        if ("latest" in tokens) score += 3
        val ctx = model.contextLength ?: 0L
        score += when {
            ctx >= 128_000 -> 5
            ctx >= 32_000 -> 2
            else -> 0
        }
        return score
    }

    /**
     * Uygun modelleri puanlar ve sıralar (eşitlikte bağlam uzunluğu, sonra ad).
     * [freeMarker] verilmişse ve bu ekli modeller varsa öneri onlar arasından seçilir
     * (ücretsiz anahtarla ücretli model 402 döner); liste yine de tüm modelleri içerir.
     */
    fun rank(models: List<RemoteModel>, freeMarker: String? = null): ModelRanking {
        val ranked = models
            .mapNotNull { m -> score(m)?.let { RankedModel(m, it) } }
            .sortedWith(
                compareByDescending<RankedModel> { it.score }
                    .thenByDescending { it.model.contextLength ?: 0L }
                    .thenBy { it.model.id },
            )
        val freeOnly = if (freeMarker == null) {
            emptyList()
        } else {
            ranked.filter { it.model.id.endsWith(freeMarker, ignoreCase = true) }
        }
        val pool = if (freeOnly.isNotEmpty()) freeOnly else ranked
        return ModelRanking(ranked, pool.firstOrNull()?.model)
    }
}
