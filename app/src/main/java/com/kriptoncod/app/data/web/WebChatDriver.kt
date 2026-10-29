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

    /**
     * Açık sohbetin adresi (ör. `https://chatgpt.com/c/...`); sohbet henüz oluşmadıysa ya da desteklenmiyorsa null.
     * Seri modda aynı sohbete sayfa yenilendikten sonra da dönebilmek için saklanır.
     */
    suspend fun chatUrl(): String? = null

    /**
     * Bir SONRAKİ mesaja dosya ekler (ör. projenin zip'i). Sayfa dosyayı kabul edip yükleme bitmişse true;
     * desteklenmiyorsa ya da başarısızsa false (çağıran metin olarak göndermeye döner).
     */
    suspend fun attach(name: String, bytes: ByteArray): Boolean = false

    /**
     * Yeni mesaj GÖNDERMEDEN sohbetteki son asistan yanıtını okur (üretim sürüyorsa bitmesini bekler).
     * Sayfa yenilendiği için kaybolan yanıtı kurtarmak içindir. Sohbette asistan yanıtı yoksa metni boş [WebReply] döner;
     * desteklenmiyorsa null.
     */
    suspend fun peekLast(): WebReply? = null
}

/**
 * Hesap başına web sohbeti açar. Üretimde WebView tabanlı (`WebViewChatDriver`), testlerde sahte.
 * Oturum yoksa/düşmüşse [com.kriptoncod.app.data.ai.AiGenerationException] (UNAUTHORIZED) fırlatır.
 */
interface WebChatDriver {
    suspend fun open(account: AiAccount, provider: WebProvider): WebChatSession

    /**
     * [chatUrl] verilmişse yeni sohbet yerine O sohbeti açar (aynı sohbette devam). Adres sağlayıcıya ait değilse ya da
     * desteklenmiyorsa [open] gibi yeni sohbet açılır.
     */
    suspend fun resume(account: AiAccount, provider: WebProvider, chatUrl: String?): WebChatSession = open(account, provider)

    /**
     * Kullanıcının elle gezeceği oturum ("AI sohbetleri"): kayıtlı oturum yoksa ya da düşmüşse hata fırlatmaz, giriş
     * sayfası açılır ve kullanıcı orada giriş yapabilir. Desteklenmiyorsa [open] gibi davranır.
     */
    suspend fun openInteractive(account: AiAccount, provider: WebProvider): WebChatSession = open(account, provider)
}
