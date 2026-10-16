package com.kriptoncod.app.data.web

import com.kriptoncod.app.data.db.AiAccount

/** Bir hesabın açık web sohbeti. Yalnız tek iş bu oturumu kullanır; işi bitince [close] çağrılır. */
interface WebChatSession {
    /** Mesajı gönderir, yanıtın bitmesini bekler. Ağ/DOM hatasında [WebReply.Status.ERROR] döner. */
    suspend fun send(prompt: String): WebReply

    /** Oturum çerezleriyle dosyayı indirir; olmazsa null. */
    suspend fun download(url: String): WebFile?

    /** Çerezleri kaydeder ve kaynakları serbest bırakır. */
    suspend fun close()
}

/**
 * Hesap başına web sohbeti açar. Üretimde WebView tabanlı (`WebViewChatDriver`), testlerde sahte.
 * Oturum yoksa/düşmüşse [com.kriptoncod.app.data.ai.AiGenerationException] (UNAUTHORIZED) fırlatır.
 */
interface WebChatDriver {
    suspend fun open(account: AiAccount, provider: WebProvider): WebChatSession
}
