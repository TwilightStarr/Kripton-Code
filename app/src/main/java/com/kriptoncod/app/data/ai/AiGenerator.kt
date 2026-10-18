package com.kriptoncod.app.data.ai

import com.kriptoncod.app.data.providers.ProviderEndpoint

/** Bir prompt'tan metin üretir. Hata durumunda [AiGenerationException] fırlatır. */
interface AiGenerator {
    /**
     * @param credential API anahtarı veya OAuth/oturum belirteci; yalnız KeyVault'tan okunmuş olmalı.
     *   Hiçbir hata mesajına, loga veya sonuca girmez.
     */
    suspend fun generate(
        endpoint: ProviderEndpoint,
        modelId: String,
        credential: String,
        prompt: String,
    ): String

    /**
     * Canlı akışlı üretim: metin geldikçe [sink]'e iletilir, dönüş değeri yine nihai metindir.
     * Akışı desteklemeyen üreticiler (ve test sahteleri) varsayılan davranışla akışsız çalışır.
     */
    suspend fun generate(
        endpoint: ProviderEndpoint,
        modelId: String,
        credential: String,
        prompt: String,
        sink: StreamSink,
    ): String = generate(endpoint, modelId, credential, prompt)
}

/**
 * Üretim hatası. Mesaj yalnız tür ve HTTP kodunu içerir; anahtar, prompt veya yanıt gövdesi içermez.
 *
 * Çağıranın (RunWorker) beklenen tepkisi:
 * - [Kind.UNAUTHORIZED]: hesabı ERROR yap, işi başka hesaba ver.
 * - [Kind.RATE_LIMITED]: hesabı [waitUntil] zamanına kadar WAITING yap, işi başka hesaba ver / bekle.
 * - [Kind.retryable] true (SERVER, NETWORK): işi yeniden kuyruğa al.
 * - Diğerleri: işi FAILED yap (aynı girdiyle tekrar denemek anlamsız).
 */
class AiGenerationException(
    val kind: Kind,
    val httpCode: Int? = null,
    /** Sağlayıcının `Retry-After` başlığından (ms); yoksa null. */
    val retryAfterMs: Long? = null,
) : Exception("AI üretimi başarısız: $kind" + (httpCode?.let { " (HTTP $it)" } ?: "")) {

    enum class Kind(val retryable: Boolean) {
        /** 401/403/402: anahtar/token geçersiz, yetkisiz veya bakiye yok. */
        UNAUTHORIZED(false),

        /** 429: kota/istek sınırı. */
        RATE_LIMITED(false),

        /** 5xx ve 408: sağlayıcı geçici olarak yanıt veremiyor. */
        SERVER(true),

        /** Ağ/zaman aşımı hatası. */
        NETWORK(true),

        /** 404: model veya uç nokta yok. */
        NOT_FOUND(false),

        /** Diğer 4xx: istek reddedildi (model adı, boyut vb.). */
        BAD_REQUEST(false),

        /** 2xx ama içerik yok/çözülemedi. */
        BAD_RESPONSE(false),

        /** Model çıktıyı uzunluk sınırında kesti; eksik kod kaydedilmez. */
        TRUNCATED(false),
    }

    /** [Kind.RATE_LIMITED] için hesabın bekleme bitiş zamanı (epoch ms). */
    fun waitUntil(nowMs: Long, defaultWaitMs: Long = DEFAULT_WAIT_MS): Long = (retryAfterMs ?: defaultWaitMs).coerceAtLeast(MIN_WAIT_MS).let { w ->
        if (w > Long.MAX_VALUE - nowMs) Long.MAX_VALUE else nowMs + w // üst sınır yok; yalnız taşma korunur
    }

    companion object {
        const val DEFAULT_WAIT_MS = 60_000L
        private const val MIN_WAIT_MS = 1_000L
    }
}
