package com.kriptoncod.app.data.ai

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Sağlayıcının bildirdiği (ya da tahmin edilen) token sayısı. */
data class TokenUsage(val prompt: Int, val completion: Int) {
    val total: Int get() = prompt + completion
}

/**
 * Bir üretim çağrısının harcadığı tokenları toplayan sayaç. [AiGenerator] arayüzünün imzası (ve tüm sahteleri)
 * değişmesin diye coroutine bağlamıyla taşınır: çağıran `withContext(meter) { generator.generate(...) }` der,
 * üretici bağlamdan sayacı bulup yanıttaki `usage` bilgisini ekler. Sayaç yoksa üretici hiçbir şey yapmaz.
 *
 * Bir çağrı birden çok HTTP turu yapabilir (kesik çıktıda "devam et"); her turun kullanımı toplanır.
 */
class TokenMeter : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TokenMeter>

    private val prompt = AtomicLong()
    private val completion = AtomicLong()
    private val reported = AtomicBoolean(false)

    fun add(usage: TokenUsage) {
        prompt.addAndGet(usage.prompt.toLong())
        completion.addAndGet(usage.completion.toLong())
        reported.set(true)
    }

    /** Sağlayıcı en az bir kez gerçek kullanım bildirdiyse toplamı, bildirmediyse null (tahmin gerekir). */
    fun reported(): TokenUsage? = if (reported.get()) TokenUsage(prompt.get().coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), completion.get().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) else null
}

/** Sağlayıcı kullanım bildirmediğinde kaba tahmin (yaklaşık 4 karakter = 1 token). Her zaman "tahmini" diye işaretlenir. */
object TokenEstimator {
    fun estimate(text: String): Int = (text.length + 3) / 4

    fun estimate(prompt: String, output: String) = TokenUsage(estimate(prompt), estimate(output))
}

/** OpenAI biçimli `usage` nesnesi (ve `input_tokens`/`output_tokens` diyen uyumlu sunucular). */
internal object UsageParser {
    fun read(element: JsonElement?): TokenUsage? {
        val o = element as? JsonObject ?: return null
        val p = int(o["prompt_tokens"]) ?: int(o["input_tokens"])
        val c = int(o["completion_tokens"]) ?: int(o["output_tokens"])
        if (p == null && c == null) return null
        return TokenUsage((p ?: 0).coerceAtLeast(0), (c ?: 0).coerceAtLeast(0))
    }

    private fun int(e: JsonElement?): Int? = (e as? JsonPrimitive)?.intOrNull
}
