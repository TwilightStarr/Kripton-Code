package com.kriptoncod.app.data.serial

import kotlinx.serialization.Serializable

/** Seri mod çalışmasının genel durumu. */
@Serializable
enum class SerialStatus {
    RUNNING,
    PAUSED,

    /** Kullanıcı müdahalesi gerekiyor (oturum düştü, token izni yok, deneme sınırı doldu...). [SerialState.message] nedenini söyler. */
    WAITING_USER,
    DONE,
    FAILED,
    CANCELLED,
    ;

    val isFinished: Boolean get() = this == DONE || this == FAILED || this == CANCELLED
}

/** Bir prompt adımının içinde bulunduğu aşama. */
@Serializable
enum class SerialPhase {
    /** Prompt AI'a gönderilir, yanıt tam gelene kadar beklenir. */
    PROMPT,

    /** Gelen dosyalar repoya commit'lenir. */
    COMMIT,

    /** GitHub Actions sonucu beklenir. */
    CI,

    /** CI hata/uyarı günlüğü AYNI sohbete gönderilir, düzeltme alınır. */
    FIX,
}

/**
 * Seri moddaki tek prompt. [newChat]: bu adım yeni bir sohbette başlar (projenin güncel hâli dosya olarak verilir).
 * [expectFiles]: false ise yanıttan dosya beklenmez (ör. yalnız plan/inceleme istenen prompt).
 */
@Serializable
data class SerialPrompt(
    val title: String,
    val body: String,
    val newChat: Boolean = false,
    val expectFiles: Boolean = true,
)

/** Seri çalışmanın diske yazılan TÜM durumu; süreç ölse bile kaldığı yerden sürer. */
@Serializable
data class SerialState(
    val id: Long,
    val createdAt: Long,
    val name: String,
    val repo: String,
    val branch: String,
    val accountId: Long,
    val prompts: List<SerialPrompt>,
    val index: Int = 0,
    val phase: SerialPhase = SerialPhase.PROMPT,
    val status: SerialStatus = SerialStatus.RUNNING,
    /** Sohbetin adresi; null: sohbet henüz oluşmadı. */
    val chatUrl: String? = null,
    /** true: sıradaki ilk mesajla birlikte sohbete projenin GÜNCEL hâli verilir (ilk adım ve yeni sohbet). */
    val needsProject: Boolean = true,
    /** Son commit'in SHA'sı (CI aşaması bunu izler). */
    val headSha: String? = null,
    /** Bu adımda yapılan CI düzeltme turu sayısı. */
    val fixRound: Int = 0,
    /** Bu adımda yalnız UYARI nedeniyle yapılan düzeltme turu sayısı. */
    val warnRound: Int = 0,
    /** FIX aşamasında AI'a gönderilecek günlük metni. */
    val pendingLog: String? = null,
    /** "E": hata, "W": uyarı. */
    val pendingKind: String? = null,
    /** Süren alışverişin (prompt ya da düzeltme) işareti; yanıtın bu alışverişe ait olduğunu anlamak için. */
    val nonce: String? = null,
    val message: String = "",
    val log: List<String> = emptyList(),
    val updatedAt: Long = createdAt,
) {
    val current: SerialPrompt? get() = prompts.getOrNull(index)
    val total: Int get() = prompts.size
}

/** Bir alışverişte (prompt ya da düzeltme) o ana kadar alınanlar; her tur sonrası diske yazılır. */
@Serializable
data class SerialStep(
    /** Sohbetten gelen ham yanıt metinleri (sırayla; "devam et" parçaları dahil). */
    val parts: List<String> = emptyList(),
    /** İndirilen zip'lerin kod bloğuna çevrilmiş metinleri. */
    val zipParts: List<String> = emptyList(),
    /** Bu alışverişin bitiş işareti görüldü mü. */
    val markerSeen: Boolean = false,
    /** Son yanıt sağlayıcı tarafından kesildi mi. */
    val truncated: Boolean = false,
    /** Eksik/yer tutucu için yapılan onarım turu sayısı. */
    val repairs: Int = 0,
    /** Bitiş işareti olmadan "bitti mi" diye sorulan tur sayısı. */
    val unmarkedAsks: Int = 0,
)
