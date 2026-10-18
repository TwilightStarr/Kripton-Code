package com.kriptoncod.app.data.web

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Sayfa betiğinin döndürdüğü sonucu çözer. `evaluateJavascript` çıktısı JSON metni olarak
 * tırnaklanmış gelir; hem tırnaklı (iç içe) hem düz nesne kabul edilir.
 */
object ReplyParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(raw: String?): WebReply {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == "null") return WebReply(WebReply.Status.ERROR)
        val obj = runCatching {
            var el = json.parseToJsonElement(text)
            if (el is JsonPrimitive && el.isString) el = json.parseToJsonElement(el.content)
            el.jsonObject
        }.getOrNull() ?: return WebReply(WebReply.Status.ERROR)
        return fromObject(obj)
    }

    private fun fromObject(o: JsonObject): WebReply {
        val status = when ((o["status"] as? JsonPrimitive)?.contentOrNull?.lowercase()) {
            "ok" -> WebReply.Status.OK
            "needs_login" -> WebReply.Status.NEEDS_LOGIN
            "rate_limited" -> WebReply.Status.RATE_LIMITED
            "timeout" -> WebReply.Status.TIMEOUT
            else -> WebReply.Status.ERROR
        }
        val files = (o["files"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        return WebReply(
            status = status,
            text = (o["text"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            fileUrls = files.distinct(),
            limitText = (o["limit"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
            truncated = (o["truncated"] as? JsonPrimitive)?.booleanOrNull == true,
        )
    }
}
