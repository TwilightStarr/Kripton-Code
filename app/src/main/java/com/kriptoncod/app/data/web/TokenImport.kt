package com.kriptoncod.app.data.web

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Elle oturum aktarma: kullanıcı telefonunun tarayıcısında (Google/telefonla) giriş yapar, sayfadaki
 * `...token...` localStorage kayıtlarını JSON olarak kopyalar ve uygulamaya yapıştırır.
 * Gömülü WebView'da giriş yapılamayan sağlayıcılar (ör. Kimi: yalnız telefon/Google/SSO) için yedek yoldur.
 */
object TokenImport {

    /** Tarayıcı adres çubuğuna yazılacak betik: adında "token" geçen tüm localStorage kayıtlarını gösterir. */
    const val BOOKMARKLET =
        "javascript:(function(){var o={};for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);" +
            "if(/token/i.test(k))o[k]=localStorage.getItem(k)}prompt('Tümünü seçip kopyala:',JSON.stringify(o))})()"

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    /** Yapıştırılan metinden {anahtar: değer} çıkarır; ilk `{` ile son `}` arası alınır. Geçersizse null. */
    fun parse(raw: String): Map<String, String>? {
        val a = raw.indexOf('{')
        val b = raw.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        val obj = runCatching { json.parseToJsonElement(raw.substring(a, b + 1)) as? JsonObject }.getOrNull() ?: return null
        val map = obj.mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { k to it }
        }.toMap()
        return map.takeIf { it.isNotEmpty() }
    }
}
