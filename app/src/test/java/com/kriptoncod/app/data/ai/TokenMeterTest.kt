package com.kriptoncod.app.data.ai

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenMeterTest {
    @Test fun freshMeterReportsNothing() {
        assertNull(TokenMeter().reported())
    }

    @Test fun accumulatesAcrossTurnsAndTravelsInCoroutineContext() = runBlocking {
        val meter = TokenMeter()
        withContext(meter) {
            coroutineContext[TokenMeter]!!.add(TokenUsage(10, 5))
            coroutineContext[TokenMeter]!!.add(TokenUsage(20, 1))
        }
        assertEquals(TokenUsage(30, 6), meter.reported())
        assertEquals(36, meter.reported()!!.total)
    }

    @Test fun estimatorIsAboutFourCharsPerToken() {
        assertEquals(0, TokenEstimator.estimate(""))
        assertEquals(1, TokenEstimator.estimate("abcd"))
        assertEquals(2, TokenEstimator.estimate("abcde"))
        assertEquals(TokenUsage(2, 1), TokenEstimator.estimate("gövde", "ok"))
    }

    private fun parse(json: String) = UsageParser.read(Json.parseToJsonElement(json).jsonObject["usage"])

    @Test fun parsesOpenAiAndCompatibleShapes() {
        assertEquals(TokenUsage(12, 34), parse("""{"usage":{"prompt_tokens":12,"completion_tokens":34,"total_tokens":46}}"""))
        assertEquals(TokenUsage(7, 8), parse("""{"usage":{"input_tokens":7,"output_tokens":8}}"""))
        assertEquals(TokenUsage(0, 9), parse("""{"usage":{"completion_tokens":9}}"""))
    }

    @Test fun ignoresMissingOrMalformedUsage() {
        assertNull(parse("""{"choices":[]}"""))
        assertNull(parse("""{"usage":null}"""))
        assertNull(parse("""{"usage":{"prompt_tokens":"x"}}"""))
        assertNull(parse("""{"usage":{}}"""))
    }
}
