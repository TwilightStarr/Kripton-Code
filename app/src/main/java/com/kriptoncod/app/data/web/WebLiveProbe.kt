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
            "var last=a.length?a[a.length-1]:null;" +
            "var t=last?(last.innerText||''):'';" +
            // Düğme, simge ve ekran-okuyucu metinleri AI'ın yazdığı yazı değildir; sayıdan düşülür (iç içe olanlar bir kez sayılır).
            "var drop=0;if(last){var ch=Array.prototype.slice.call(last.querySelectorAll('button,[role=button],[aria-hidden=true],[class*=sr-only]'));" +
            "ch.forEach(function(x){var p=x.parentElement,nested=false;while(p&&p!==last){if(ch.indexOf(p)>=0){nested=true;break;}p=p.parentElement;}" +
            "if(!nested)drop+=(x.innerText||'').length;});}" +
            "var c=Math.max(0,t.length-drop);" +
            "return JSON.stringify({g:g,n:a.length,c:c,t:t.slice(-300)});" +
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
