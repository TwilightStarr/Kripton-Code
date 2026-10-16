package com.kriptoncod.app.data.ai

import com.kriptoncod.app.data.ai.AiGenerationException.Kind
import com.kriptoncod.app.data.providers.ApiKey
import com.kriptoncod.app.data.providers.ProviderEndpoint
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryingAiGeneratorTest {
    /** Her çağrıda sıradaki adımı uygular: String → döndür, AiGenerationException → fırlat. */
    private class Script(private vararg val steps: Any) : AiGenerator {
        val prompts = mutableListOf<String>()
        override suspend fun generate(endpoint: ProviderEndpoint, modelId: String, credential: String, prompt: String): String {
            prompts += prompt
            return when (val step = steps[minOf(prompts.size - 1, steps.size - 1)]) {
                is AiGenerationException -> throw step
                else -> step as String
            }
        }
    }

    private val base = "http://127.0.0.1:1/v1".toHttpUrl()
    private val endpoint = ProviderEndpoint(
        providerId = "claude",
        baseUrl = base,
        modelsUrl = base.newBuilder().addPathSegment("models").build(),
        chatUrl = base.newBuilder().addPathSegments("chat/completions").build(),
        validationUrl = base.newBuilder().addPathSegment("models").build(),
        authMethod = ApiKey(),
    )

    private val waits = mutableListOf<Int>()
    private fun retrying(delegate: AiGenerator, attempts: Int = 3) =
        RetryingAiGenerator(delegate, attempts, backoff = { waits += it })

    private fun gen(g: AiGenerator, prompt: String = "Yaz") =
        runBlocking { g.generate(endpoint, "claude-sonnet-5-5", "k", prompt) }

    private fun failure(block: () -> Unit): AiGenerationException =
        runCatching(block).exceptionOrNull() as AiGenerationException

    @Test fun goodOutputIsReturnedWithoutRetry() {
        val s = Script("tamam")
        assertEquals("tamam", gen(retrying(s)))
        assertEquals(1, s.prompts.size)
    }

    @Test fun badResponseIsRetriedWithCorrectionNote() {
        waits.clear()
        val s = Script(AiGenerationException(Kind.BAD_RESPONSE), "tamam")
        assertEquals("tamam", gen(retrying(s), "Yaz"))
        assertEquals(2, s.prompts.size)
        assertEquals("Yaz", s.prompts[0])
        assertTrue(s.prompts[1].startsWith("Yaz"))
        assertTrue(s.prompts[1].contains("Not:"))
        assertEquals(listOf(1), waits)
    }

    @Test fun truncatedIsRetriedAskingForCompleteShorterOutput() {
        val s = Script(AiGenerationException(Kind.TRUNCATED), "tamam")
        assertEquals("tamam", gen(retrying(s)))
        assertTrue(s.prompts[1].contains("kesildi"))
    }

    @Test fun unclosedCodeBlockIsTreatedAsBrokenAndRetried() {
        val s = Script("```a.txt\nyarım", "```a.txt\ntam\n```")
        assertEquals("```a.txt\ntam\n```", gen(retrying(s)))
        assertEquals(2, s.prompts.size)
    }

    @Test fun missingCodeBlocksAreRetriedOnlyWhenThePromptRequiresThem() {
        val required = "Birleştir. Dosyaları ```tam/dosya/yolu biçiminde ver."
        val s = Script("sadece metin", "```a.txt\nx\n```")
        assertEquals("```a.txt\nx\n```", gen(retrying(s), required))
        assertEquals(2, s.prompts.size)
        assertTrue(s.prompts[1].contains("kod bloğu yoktu"))

        val plain = Script("sadece metin")
        assertEquals("sadece metin", gen(retrying(plain), "Açıkla"))
        assertEquals(1, plain.prompts.size)
    }

    @Test fun otherFailuresAreNotRetried() {
        for (kind in listOf(Kind.UNAUTHORIZED, Kind.RATE_LIMITED, Kind.SERVER, Kind.NETWORK, Kind.NOT_FOUND, Kind.BAD_REQUEST)) {
            val s = Script(AiGenerationException(kind, 500))
            assertEquals(kind, failure { gen(retrying(s)) }.kind)
            assertEquals("$kind tekrar denenmemeli", 1, s.prompts.size)
        }
    }

    @Test fun exhaustionKeepsTheLastProblemKind() {
        val bad = Script(AiGenerationException(Kind.BAD_RESPONSE))
        assertEquals(Kind.BAD_RESPONSE, failure { gen(retrying(bad)) }.kind)
        assertEquals(3, bad.prompts.size)

        val cut = Script(AiGenerationException(Kind.TRUNCATED))
        assertEquals(Kind.TRUNCATED, failure { gen(retrying(cut)) }.kind)

        val unclosed = Script("```a.txt\nyarım")
        assertEquals(Kind.TRUNCATED, failure { gen(retrying(unclosed)) }.kind)

        val empty = Script("   ")
        assertEquals(Kind.BAD_RESPONSE, failure { gen(retrying(empty)) }.kind)
    }

    @Test fun incompleteOutputIsNeverReturnedAfterExhaustion() {
        val s = Script("```a.txt\nyarım")
        val r = runCatching { gen(retrying(s, attempts = 2)) }
        assertTrue(r.isFailure)
        assertEquals(2, s.prompts.size)
    }

    @Test fun singleAttemptMeansNoRetry() {
        val s = Script(AiGenerationException(Kind.BAD_RESPONSE), "tamam")
        assertEquals(Kind.BAD_RESPONSE, failure { gen(retrying(s, attempts = 1)) }.kind)
        assertEquals(1, s.prompts.size)
        assertTrue(runCatching { RetryingAiGenerator(s, 0) }.exceptionOrNull() is IllegalArgumentException)
    }
}
