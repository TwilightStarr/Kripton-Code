package com.kriptoncod.app.data.web

/** Bir web sohbet turunun sonucu. Metin [text]; indirilebilir dosya bağlantıları [fileUrls]. */
data class WebReply(
    val status: Status,
    val text: String = "",
    val fileUrls: List<String> = emptyList(),
    /** Sayfadaki sınır/kota metni (varsa) — bekleme süresi buradan çözülür. */
    val limitText: String? = null,
    /** Sağlayıcı yanıtı uzunluk/araç sınırında kesti. */
    val truncated: Boolean = false,
    /** Hata nedeni kodu (no_input, not_started, lost, exception...); yalnız ERROR/TIMEOUT'ta dolu olabilir. */
    val reason: String? = null,
    /** Mesaj sayfaya gerçekten gönderildi mi (gönder tıklandı ya da Enter basıldı). Yeniden gönderimi önlemek içindir. */
    val sent: Boolean = false,
    /** Seçici eşleşme sayıları, adres ve sayfa metninin sonu; yalnız tanı içindir. */
    val diag: String? = null,
) {
    enum class Status { OK, NEEDS_LOGIN, RATE_LIMITED, TIMEOUT, ERROR }
}

/** İndirilmiş dosya (zip veya tek dosya). */
class WebFile(val name: String, val bytes: ByteArray)
