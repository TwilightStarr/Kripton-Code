package com.kriptoncod.app.data.web

/**
 * Sağlayıcıya özel çıktı davranışı. Her sağlayıcı farklı bozulur; bu sınıf hangisine ne yapılacağını tek yerde tutar:
 *  - Claude: zip üretir; zip gelmezse kod blokları. Düşünme blokları sızmaz, kısaltma eğilimi düşük.
 *  - ChatGPT: zip üretir ama sık sık yer tutucuyla kısaltır ve "devam et" ister; onarım turu fazladır.
 *  - Kimi: zip üretmez (token'ı çabuk biter): düz kod + metin karışık gelir; çıktı temizlenir, yolsuz bloklar kurtarılır.
 *  - Gemini: web arayüzü yok (API anahtarı); bu politika kullanılmaz.
 */
data class WebOutputPolicy(
    val aggressiveThinking: Boolean,
    val specialTokens: Boolean,
    val toolSections: Boolean,
    /** Yanıt yarıda kesilirse "devam et" turu gönderilir. */
    val continueOnTruncated: Boolean,
    /** Yer tutucu/yolsuz blok/eksik blok için aynı sohbette en çok kaç onarım turu istenir. */
    val repairTurns: Int,
) {
    companion object {
        fun forProvider(provider: WebProvider): WebOutputPolicy = when (provider) {
            WebProvider.CLAUDE -> WebOutputPolicy(false, false, false, true, 2)
            WebProvider.CHATGPT -> WebOutputPolicy(false, false, false, true, 3)
            WebProvider.KIMI -> WebOutputPolicy(true, true, true, true, 3)
            WebProvider.GEMINI -> WebOutputPolicy(false, false, false, false, 0)
        }
    }
}
