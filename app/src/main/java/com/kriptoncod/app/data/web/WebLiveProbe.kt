package com.kriptoncod.app.data.web

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** Sayfadan okunan anlık üretim durumu. */
data class LiveProbe(
    /** Durdur düğmesi görünüyor mu (AI yanıt üretiyor). */
    val generating: Boolean,
    /** Sayfadaki AI mesajı sayısı. */
    val messages: Int,
    /** Son AI mesajının uzunluğu (karakter). */
    val chars: Int,
    /** Son AI mesajının sonu. */
    val tail: String,
)

/**
 * Yanıt beklenirken yalnız OKUYAN hafif betik: sohbeti değiştirmez, `window.__kr` durumuna dokunmaz.
 * Sonuç "AI şu an çıktı veriyor mu, yanıt büyüyor mu" sorusunu canlı web ekranında yanıtlar.
 */
object WebLiveProbe {
    private val json = Json { ignoreUnknownKeys = true }
    private val lenient = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun script(sel: SiteSelectors): String {
        val cfg = json.encodeToString(sel)
        return "(function(cfg){try{" +
            "function all(s){for(var i=0;i<s.length;i++){try{var l=document.querySelectorAll(s[i]);if(l.length)return Array.prototype.slice.call(l);}catch(x){}}return [];}" +
            "function vis(e){var r=e.getBoundingClientRect();return r.width>0&&r.height>0;}" +
            "var g=all(cfg.stop).some(vis);" +
            "var a=all(cfg.assistant);" +
            "var t=a.length?(a[a.length-1].innerText||''):'';" +
            "return JSON.stringify({g:g,n:a.length,c:t.length,t:t.slice(-300)});" +
            "}catch(e){return '';}})($cfg)"
    }

    /** [raw]: `evaluateJavascript` çıktısı ya da [WebCookies.unquote] sonrası düz JSON; çözülemezse null. */
    fun parse(raw: String?): LiveProbe? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == "null") return null
        val obj: JsonObject = runCatching {
            var el = lenient.parseToJsonElement(text)
            if (el is JsonPrimitive && el.isString) el = lenient.parseToJsonElement(el.content)
            el.jsonObject
        }.getOrNull() ?: return null
        return LiveProbe(
            generating = (obj["g"] as? JsonPrimitive)?.booleanOrNull == true,
            messages = (obj["n"] as? JsonPrimitive)?.intOrNull ?: 0,
            chars = (obj["c"] as? JsonPrimitive)?.intOrNull ?: 0,
            tail = (obj["t"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
        )
    }
}
