package com.kriptoncod.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Bir "mod": run (akış), seri mod ve hesap havuzunun tüm ayarlanabilir değerleri tek pakette.
 * Daha önce bu değerler kodda sabitti (PromptDispatcher.MAX_PARALLEL, SerialRunner.MAX_FIX_ROUNDS ...).
 * Artık kullanıcı hazır bir modu tek dokunuşla seçer, kendi modunu kopyalayıp düzenler, dışa/içe aktarır.
 *
 * Süreler kullanıcıya uygun birimlerle (sn / dk) tutulur; çalışma zamanında ms'ye çevrilir.
 * Her alanın izinli aralığı [Limits]'tedir; [coerced] aralık dışı (elle düzenlenmiş/bozuk) değeri düzeltir.
 */
@Serializable
data class ModeProfile(
    val id: String,
    val name: String,
    val description: String = "",
    /** true: uygulamayla gelen hazır mod; silinemez/değiştirilemez (kopyalanıp düzenlenir). */
    val builtIn: Boolean = false,

    // ---- Run (akış): paralellik ve hata sabrı ----
    /** Aynı anda çalışan en çok düğüm. Gerçek sayı ayrıca açık/hatasız hesap sayısıyla sınırlanır. */
    val maxParallel: Int = 6,
    /** Geçici (5xx/ağ) hatada bir düğüm için en çok deneme. */
    val maxAttempts: Int = 60,
    /** İlk bekleme (sn); her denemede deneme sayısıyla çarpılır. */
    val retryBaseSec: Int = 15,
    /** Tek bir bekleme için üst sınır (dk). */
    val maxRetryDelayMin: Int = 15,
    /** Hiç kullanılabilir hesap yokken yeniden deneme aralığı (dk). */
    val noAccountWaitMin: Int = 10,

    // ---- Hesap havuzu ----
    /** Art arda bu kadar hesap/sağlayıcı arızasında devre kesici hesabı dinlendirir. */
    val breakerStreak: Int = 3,
    /** Devre kesici dinlendirme süresi (dk). */
    val breakerCooldownMin: Int = 10,
    /** Son sonuçlarda başarı oranı (%) bunun altındaysa hesap sona itilir. */
    val unhealthyBelowPct: Int = 50,
    /** Sağlık puanına giren sonuçların penceresi (dk). */
    val healthWindowMin: Int = 30,

    // ---- Seri mod ----
    /** CI hatası için en çok düzeltme turu (aşılırsa "Müdahale bekliyor"). */
    val serialMaxFixRounds: Int = 12,
    /** Yalnız UYARI nedeniyle en çok düzeltme turu (aşılırsa uyarılar kabul edilip devam edilir). */
    val serialMaxWarnRounds: Int = 3,
    /** Kesilen bir yanıt için en çok "devam et" turu. */
    val serialMaxContinues: Int = 60,
    /** Bitiş işareti görülmeyince "bitti mi" diye sorma sayısı. */
    val serialMaxUnmarkedAsks: Int = 2,
    /** Art arda GitHub hatasında müdahale istemeden önce deneme sayısı. */
    val serialMaxGitFailures: Int = 12,
    /** Oturum doğrulanamama sayısı sınırı. */
    val serialMaxUnauthorized: Int = 3,
    /** Sayfa (DOM) okunamama sayısı sınırı. */
    val serialMaxDomFailures: Int = 3,

    // ---- Anahtarlar ----
    /** Her katalog birleştirmesi bitince WIP dalına ara kayıt (telefon kaybolsa iş depoda durur). */
    val wipCheckpoint: Boolean = true,
    /** Push'tan önce projede test yoksa AI'a test yazdır. */
    val synthesizeTests: Boolean = true,
) {

    /** Tüm alanları izinli aralığa çeker; kimlik/ad boş kalmaz. */
    fun coerced(): ModeProfile = copy(
        id = id.ifBlank { newId() },
        name = name.trim().ifBlank { "Mod" }.take(Limits.NAME_MAX),
        description = description.trim().take(Limits.DESC_MAX),
        maxParallel = maxParallel.coerceIn(Limits.PARALLEL),
        maxAttempts = maxAttempts.coerceIn(Limits.ATTEMPTS),
        retryBaseSec = retryBaseSec.coerceIn(Limits.RETRY_BASE_SEC),
        maxRetryDelayMin = maxRetryDelayMin.coerceIn(Limits.RETRY_MAX_MIN),
        noAccountWaitMin = noAccountWaitMin.coerceIn(Limits.NO_ACCOUNT_MIN),
        breakerStreak = breakerStreak.coerceIn(Limits.BREAKER_STREAK),
        breakerCooldownMin = breakerCooldownMin.coerceIn(Limits.BREAKER_COOLDOWN_MIN),
        unhealthyBelowPct = unhealthyBelowPct.coerceIn(Limits.UNHEALTHY_PCT),
        healthWindowMin = healthWindowMin.coerceIn(Limits.HEALTH_WINDOW_MIN),
        serialMaxFixRounds = serialMaxFixRounds.coerceIn(Limits.FIX_ROUNDS),
        serialMaxWarnRounds = serialMaxWarnRounds.coerceIn(Limits.WARN_ROUNDS),
        serialMaxContinues = serialMaxContinues.coerceIn(Limits.CONTINUES),
        serialMaxUnmarkedAsks = serialMaxUnmarkedAsks.coerceIn(Limits.UNMARKED_ASKS),
        serialMaxGitFailures = serialMaxGitFailures.coerceIn(Limits.GIT_FAILURES),
        serialMaxUnauthorized = serialMaxUnauthorized.coerceIn(Limits.UNAUTHORIZED),
        serialMaxDomFailures = serialMaxDomFailures.coerceIn(Limits.DOM_FAILURES),
    )

    // ---- Çalışma zamanı değerleri (ms / oran) ----
    val retryBaseMs: Long get() = retryBaseSec * 1_000L
    val maxRetryDelayMs: Long get() = maxRetryDelayMin * 60_000L
    val noAccountWaitMs: Long get() = noAccountWaitMin * 60_000L
    val breakerCooldownMs: Long get() = breakerCooldownMin * 60_000L
    val healthWindowMs: Long get() = healthWindowMin * 60_000L
    val unhealthyBelow: Double get() = unhealthyBelowPct / 100.0

    /** Aynı ayarlar mı (ad/açıklama/kimlik hariç)? "Hangi hazır moddayım" tespiti için. */
    fun sameSettingsAs(other: ModeProfile): Boolean =
        copy(id = "", name = "", description = "", builtIn = false).coerced0() ==
            other.copy(id = "", name = "", description = "", builtIn = false).coerced0()

    private fun coerced0(): ModeProfile = coerced().copy(id = "", name = "", description = "")

    /** İzinli aralıklar; arayüzdeki kaydırıcılar da buradan beslenir (tek doğruluk kaynağı). */
    object Limits {
        const val NAME_MAX = 40
        const val DESC_MAX = 160
        val PARALLEL = 1..6
        val ATTEMPTS = 1..100
        val RETRY_BASE_SEC = 5..300
        val RETRY_MAX_MIN = 1..120
        val NO_ACCOUNT_MIN = 1..120
        val BREAKER_STREAK = 2..10
        val BREAKER_COOLDOWN_MIN = 1..240
        val UNHEALTHY_PCT = 10..90
        val HEALTH_WINDOW_MIN = 5..480
        val FIX_ROUNDS = 1..40
        val WARN_ROUNDS = 0..10
        val CONTINUES = 5..120
        val UNMARKED_ASKS = 0..5
        val GIT_FAILURES = 3..40
        val UNAUTHORIZED = 1..10
        val DOM_FAILURES = 1..10
        const val MAX_CUSTOM = 30
    }

    companion object {
        /** Yeni kullanıcı modu kimliği; hazır modların kimlikleriyle çakışmaz. */
        fun newId(): String = "u-" + java.util.UUID.randomUUID().toString().take(8)

        const val ID_BALANCED = "balanced"
        const val ID_FAST = "fast"
        const val ID_PATIENT = "patient"
        const val ID_SAVER = "saver"
        const val ID_STRICT = "strict"

        /** Varsayılan: eski sabit değerlerle BİREBİR aynı (mevcut davranış değişmez, testler bozulmaz). */
        val BALANCED = ModeProfile(
            id = ID_BALANCED,
            name = "Dengeli",
            description = "Eski varsayılan davranış: 6 paralel düğüm, sabırlı yeniden deneme, 12 düzeltme turu.",
            builtIn = true,
        )

        /** Çok hesap + hızlı sonuç: kısa beklemeler, az deneme, tam paralel. */
        val FAST = ModeProfile(
            id = ID_FAST,
            name = "Hızlı",
            description = "Tam paralel, kısa beklemeler. Hesaplar sağlamsa en çabuk sonuç; hata sabrı düşük.",
            builtIn = true,
            maxParallel = 6,
            maxAttempts = 15,
            retryBaseSec = 8,
            maxRetryDelayMin = 3,
            noAccountWaitMin = 3,
            breakerStreak = 4,
            breakerCooldownMin = 4,
            serialMaxFixRounds = 6,
            serialMaxWarnRounds = 1,
            serialMaxContinues = 30,
            serialMaxGitFailures = 6,
            synthesizeTests = false,
        )

        /** Gece boyu/günlerce süren işler: tek tek, çok sabırlı, uzun beklemeler. */
        val PATIENT = ModeProfile(
            id = ID_PATIENT,
            name = "Sabırlı (gece)",
            description = "Az paralel, çok uzun yeniden deneme. Kota/ağ kesintilerinde pes etmez; gece bırakmak için.",
            builtIn = true,
            maxParallel = 2,
            maxAttempts = 100,
            retryBaseSec = 30,
            maxRetryDelayMin = 30,
            noAccountWaitMin = 20,
            breakerStreak = 3,
            breakerCooldownMin = 30,
            healthWindowMin = 120,
            serialMaxFixRounds = 20,
            serialMaxWarnRounds = 5,
            serialMaxContinues = 100,
            serialMaxGitFailures = 30,
        )

        /** Ücretsiz kotayı koruyan: tek düğüm, hesabı çabuk dinlendirir, test yazdırmaz. */
        val SAVER = ModeProfile(
            id = ID_SAVER,
            name = "Kota tasarrufu",
            description = "Aynı anda tek düğüm, hatalı hesabı çabuk dinlendirir, ek AI çağrılarını (test, ara kayıt) kapatır.",
            builtIn = true,
            maxParallel = 1,
            maxAttempts = 30,
            retryBaseSec = 20,
            breakerStreak = 2,
            breakerCooldownMin = 20,
            unhealthyBelowPct = 60,
            serialMaxFixRounds = 8,
            serialMaxWarnRounds = 0,
            wipCheckpoint = false,
            synthesizeTests = false,
        )

        /** Kalite öncelikli: az paralel, testler zorunlu, uyarılar da giderilir. */
        val STRICT = ModeProfile(
            id = ID_STRICT,
            name = "Titiz (kalite)",
            description = "Testleri yazdırır, uyarıları da düzeltir, çok düzeltme turu dener. Yavaş ama temiz sonuç.",
            builtIn = true,
            maxParallel = 3,
            serialMaxFixRounds = 20,
            serialMaxWarnRounds = 8,
            serialMaxUnmarkedAsks = 3,
            wipCheckpoint = true,
            synthesizeTests = true,
        )

        val BUILT_INS: List<ModeProfile> = listOf(BALANCED, FAST, PATIENT, SAVER, STRICT)

        fun builtInById(id: String?): ModeProfile? = BUILT_INS.firstOrNull { it.id == id }
    }
}

/**
 * Çalışma zamanının okuduğu "şu an etkin mod". Dağıtıcı, hesap seçici ve seri koşucu bunu okur; böylece
 * kurucu imzaları ve mevcut testler değişmez. ModeStore etkin mod değişince [current]'ı günceller.
 * Varsayılanı [ModeProfile.BALANCED] = eski sabitler.
 */
object ModeRuntime {
    @Volatile
    var current: ModeProfile = ModeProfile.BALANCED
        private set

    fun set(profile: ModeProfile) {
        current = profile.coerced()
    }

    /** Testler için: varsayılana döner. */
    fun reset() {
        current = ModeProfile.BALANCED
    }
}

/** Mod listesini ve etkin seçimi metne çevirir (dışa/içe aktarma ve DataStore kaydı). Bozuk girdi çökmez. */
object ModeCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val compact = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class Bundle(val kripton: String = "modes", val version: Int = 1, val modes: List<ModeProfile> = emptyList())

    /** Kullanıcı modlarını kaydetmek için (küçük). */
    fun encodeList(list: List<ModeProfile>): String = compact.encodeToString(list.map { it.copy(builtIn = false) })

    /** Bozuk/boş kayıt boş liste verir; her mod aralığa çekilir, hazır mod kimliğiyle çakışanlar atılır. */
    fun decodeList(text: String?): List<ModeProfile> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            compact.decodeFromString<List<ModeProfile>>(text).sanitized()
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    /** Paylaşılabilir metin (tek ya da çok mod). Hazır modlar da dışa aktarılabilir; içe alınırken kopya olur. */
    fun export(modes: List<ModeProfile>): String = json.encodeToString(Bundle(modes = modes.map { it.copy(builtIn = false) }))

    sealed interface ImportResult {
        data class Ok(val modes: List<ModeProfile>) : ImportResult
        data class Invalid(val reason: String) : ImportResult
    }

    /**
     * Dışa aktarılmış metni okur. Gelen her mod YENİ kimlik alır (mevcutları ezmez), builtIn=false olur,
     * değerleri aralığa çekilir. Paket ya da tek mod nesnesi kabul edilir.
     */
    fun import(text: String): ImportResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ImportResult.Invalid("Metin boş.")
        val parsed: List<ModeProfile> = try {
            if (trimmed.startsWith("[")) {
                compact.decodeFromString<List<ModeProfile>>(trimmed)
            } else {
                val bundle = runCatching { compact.decodeFromString<Bundle>(trimmed) }.getOrNull()
                if (bundle != null && bundle.modes.isNotEmpty()) bundle.modes else listOf(compact.decodeFromString<ModeProfile>(trimmed))
            }
        } catch (e: SerializationException) {
            return ImportResult.Invalid("Mod metni okunamadı.")
        } catch (e: IllegalArgumentException) {
            return ImportResult.Invalid("Mod metni okunamadı.")
        }
        if (parsed.isEmpty()) return ImportResult.Invalid("İçinde mod yok.")
        val fresh = parsed.take(ModeProfile.Limits.MAX_CUSTOM).map {
            it.copy(id = ModeProfile.newId(), builtIn = false).coerced()
        }
        return ImportResult.Ok(fresh)
    }

    private fun List<ModeProfile>.sanitized(): List<ModeProfile> {
        val seen = HashSet<String>()
        return mapNotNull { raw ->
            val p = raw.copy(builtIn = false).coerced()
            if (ModeProfile.builtInById(p.id) != null) null else if (seen.add(p.id)) p else null
        }.take(ModeProfile.Limits.MAX_CUSTOM)
    }
}
