package com.kriptoncod.app.data.ai.normalize

import com.kriptoncod.app.data.providers.UpstreamProvider
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Yanıttaki ilk `choices[]` öğesinden çıkarılmış, henüz temizlenmemiş veri.
 * Sağlayıcıya özgü yapılar (Claude parçaları, `reasoning_content` vb.) burada düzleştirilir.
 */
data class RawChoice(
    /** Görünür yanıt metni (muhakeme/thinking parçaları hariç); yoksa null. */
    val visibleText: String?,
    /** Muhakeme metni; yanıt olarak ASLA kullanılmaz, yalnız tanılama içindir. */
    val reasoningText: String?,
    val finishReason: String?,
    val refusal: String?,
    val hasToolCalls: Boolean,
)

enum class Finish { COMPLETE, TRUNCATED, FILTERED }

/** [text] temizlenmiş yanıttır; boş olabilir (çağıran bunu bozuk yanıt sayar). */
data class NormalizedOutput(val text: String, val finish: Finish)

/** Bir model ailesinin ham yanıtını ortak, kod-bloğu ayrıştırmaya hazır biçime getirir. */
interface ProviderNormalizer {
    val family: UpstreamProvider
    fun normalize(raw: RawChoice): NormalizedOutput
}

/** OpenAI biçimli `choices[0]` nesnesini [RawChoice]'a çevirir; beklenmeyen alanları yok sayar. */
object ChoiceReader {
    private val THINKING_TYPES = setOf(
        "thinking", "redacted_thinking", "reasoning", "reasoning_text", "thought", "summary_text",
    )
    private val TEXT_TYPES = setOf("text", "output_text")
    private val TOOL_TYPES = setOf("tool_use", "tool_call", "function_call")

    fun read(choice: JsonObject): RawChoice {
        val message = choice["message"] as? JsonObject
        val visible = StringBuilder()
        val reasoning = StringBuilder()
        var tools = false

        fun addContent(content: JsonElement?) {
            when (content) {
                null, JsonNull -> Unit
                is JsonPrimitive -> content.contentOrNull?.let { visible.append(it) }
                is JsonObject -> addPart(content, visible, reasoning)?.let { tools = tools || it }
                is JsonArray -> content.forEach { part ->
                    val obj = part as? JsonObject ?: return@forEach
                    addPart(obj, visible, reasoning)?.let { tools = tools || it }
                }
            }
        }

        if (message != null) addContent(message["content"]) else addContent(choice["text"])

        listOf("reasoning_content", "reasoning", "thinking").forEach { key ->
            (message?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                if (reasoning.isNotEmpty()) reasoning.append('\n')
                reasoning.append(it)
            }
        }

        val toolCalls = (message?.get("tool_calls") as? JsonArray)?.isNotEmpty() == true ||
            (message?.get("function_call").let { it != null && it !is JsonNull })

        val finish = listOf(choice["finish_reason"], choice["stop_reason"], message?.get("stop_reason"))
            .firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }

        return RawChoice(
            visibleText = visible.toString().ifEmpty { null },
            reasoningText = reasoning.toString().ifBlank { null },
            finishReason = finish,
            refusal = (message?.get("refusal") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
            hasToolCalls = tools || toolCalls,
        )
    }

    /** Parçayı görünür/muhakeme tamponuna ekler; araç çağrısı parçasıysa true, aksi halde null döner. */
    private fun addPart(part: JsonObject, visible: StringBuilder, reasoning: StringBuilder): Boolean? {
        val type = (part["type"] as? JsonPrimitive)?.contentOrNull
        val isThought = (part["thought"] as? JsonPrimitive)?.contentOrNull == "true"
        fun str(key: String) = (part[key] as? JsonPrimitive)?.contentOrNull
        return when {
            type in TOOL_TYPES -> true
            type in THINKING_TYPES || isThought -> {
                (str("thinking") ?: str("reasoning") ?: str("text"))?.let { reasoning.append(it) }
                null
            }
            type == null || type in TEXT_TYPES -> {
                str("text")?.let { visible.append(it) }
                null
            }
            else -> null // image, refusal vb.: yok sayılır
        }
    }
}
