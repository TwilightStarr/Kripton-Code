package com.kriptoncod.app.data.web

import java.time.Instant
import java.time.ZoneId

/**
 * Web sohbetlerindeki kota/sınır iletilerini tanır ve bekleme süresini çözer
 * (İngilizce, Türkçe, Çince). Süre çözülemezse makul bir varsayılan döner.
 */
object LimitParser {

    const val DEFAULT_WAIT_MS = 30L * 60 * 1000
    private const val MIN_WAIT_MS = 60_000L
    private const val BUFFER_MS = 60_000L

    private val LIMIT_WORDS = listOf(
        "usage limit", "message limit", "rate limit", "too many requests", "reached your limit",
        "reached the limit", "limit reached", "hit your limit", "you've hit", "daily limit",
        "out of messages", "out of free messages", "capacity", "try again later", "come back",
        "kota", "limitine ulaştın", "limit doldu", "çok fazla istek", "daha sonra tekrar dene",
        "已达上限", "额度", "请求过于频繁", "稍后再试", "使用上限",
    )

    fun looksLikeLimit(text: String?): Boolean {
        val t = text?.lowercase()?.takeIf { it.isNotBlank() } ?: return false
        return LIMIT_WORDS.any { it in t }
    }

    private val HOURS = Regex("""(\d+(?:[.,]\d+)?)\s*(?:hours?|hrs?|h\b|saat|小时|時間)""", RegexOption.IGNORE_CASE)
    private val MINUTES = Regex("""(\d+)\s*(?:minutes?|mins?|m\b|dakika|dk\b|分钟|分)""", RegexOption.IGNORE_CASE)
    private val SECONDS = Regex("""(\d+)\s*(?:seconds?|secs?|s\b|saniye|sn\b|秒)""", RegexOption.IGNORE_CASE)
    private val DAYS = Regex("""(\d+)\s*(?:days?|gün|天)""", RegexOption.IGNORE_CASE)
    private val CLOCK = Regex("""(?:at|until|after|by|saat|在)\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""", RegexOption.IGNORE_CASE)

    /**
     * [text] içindeki beklemeyi (ms) çözer; "2 hours 15 minutes", "in 45 min", "resets at 3 PM",
     * "2 saat 10 dakika sonra" gibi. Sonuç en az 1 dk'dır; üst sınır yoktur.
     */
    fun waitMs(
        text: String?,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        defaultMs: Long = DEFAULT_WAIT_MS,
    ): Long {
        val t = text.orEmpty()
        val rel = relativeMs(t)
        val ms = rel ?: absoluteMs(t, nowMs, zone) ?: defaultMs
        return if (ms > Long.MAX_VALUE - BUFFER_MS) Long.MAX_VALUE else (ms + BUFFER_MS).coerceAtLeast(MIN_WAIT_MS)
    }

    private fun relativeMs(t: String): Long? {
        var total = 0.0
        var found = false
        DAYS.find(t)?.let { total += it.groupValues[1].toDouble() * 86_400_000; found = true }
        HOURS.find(t)?.let {
            total += it.groupValues[1].replace(',', '.').toDouble() * 3_600_000; found = true
        }
        MINUTES.find(t)?.let { total += it.groupValues[1].toDouble() * 60_000; found = true }
        if (!found) SECONDS.find(t)?.let { total += it.groupValues[1].toDouble() * 1_000; found = true }
        return if (found) total.toLong() else null
    }

    private fun absoluteMs(t: String, nowMs: Long, zone: ZoneId): Long? {
        val m = CLOCK.find(t) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        val ampm = m.groupValues[3].lowercase()
        if (ampm == "pm" && h < 12) h += 12
        if (ampm == "am" && h == 12) h = 0
        if (h > 23 || min > 59) return null
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        var target = now.withHour(h).withMinute(min).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target.toInstant().toEpochMilli() - nowMs
    }
}
