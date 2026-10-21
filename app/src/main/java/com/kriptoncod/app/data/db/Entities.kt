package com.kriptoncod.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AssignmentMode
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.domain.UsageOutcome

/** AI hesabı. API anahtarı/token BU TABLODA YOKTUR; KeyVault'ta [vaultKeyId] ile saklanır. */
@Entity(tableName = "ai_accounts")
data class AiAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: String,
    val displayName: String,
    val authType: AuthType,
    val modelId: String,
    val powerRank: Int,
    val enabled: Boolean = true,
    val status: AccountStatus = AccountStatus.READY,
    /** WAITING durumunda bekleme bitiş zamanı (epoch ms). */
    val waitUntil: Long? = null,
    /**
     * Yalnız [AuthType.PROXY] hesaplarında: proxy taban adresi (ör. "http://192.168.1.5:8317/v1").
     * Gizli bilgi değildir; anahtar yine yalnız KeyVault'ta durur. Proxy hesabında [providerId]
     * yukarı akış sağlayıcısını (claude, gemini, openai, deepseek, kimi, other) tutar.
     */
    val baseUrl: String? = null,
    /** Kayan 24 saatteki en çok istek sayısı; null (ya da 0 ve altı) = sınırsız. Dolunca hesap havuzdan çıkar. */
    val dailyLimit: Int? = null,
    /** Yedek hesap: yalnız birincil hesapların hiçbiri kullanılamazken devreye girer. */
    @ColumnInfo(defaultValue = "0") val isBackup: Boolean = false,
    /** İsteğe bağlı fiyat (1 milyon girdi tokenı başına, kullanıcının para biriminde); maliyet tahmini içindir. */
    val priceInPerM: Double? = null,
    /** İsteğe bağlı fiyat (1 milyon çıktı tokenı başına). */
    val priceOutPerM: Double? = null,
) {
    companion object {
        fun vaultKeyId(accountId: Long): String = "ai-account-$accountId"
    }
}

@Entity(tableName = "prompt_sets")
data class PromptSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "prompt_items",
    foreignKeys = [
        ForeignKey(
            entity = PromptSet::class,
            parentColumns = ["id"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("setId")],
)
data class PromptItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val setId: Long,
    val title: String,
    val body: String,
    /** Virgülle ayrılmış hedef dosya yolları. */
    val targetFiles: String? = null,
    /** "order" SQL anahtar kelimesi olduğundan sütun adı "position". */
    @ColumnInfo(name = "position") val order: Int,
)

/** Bir prompt en fazla bir hesaba atanır (PK = promptId). */
@Entity(
    tableName = "assignments",
    primaryKeys = ["promptId"],
    foreignKeys = [
        ForeignKey(
            entity = PromptItem::class,
            parentColumns = ["id"],
            childColumns = ["promptId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AiAccount::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId")],
)
data class Assignment(
    val promptId: Long,
    val accountId: Long,
    val mode: AssignmentMode,
)

@Entity(tableName = "runs")
data class RunRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val status: RunStatus = RunStatus.PREPARING,
    /** filesDir'e göre göreli yol (ör. "zips/3.zip"). */
    val zipPath: String? = null,
    /** Listeden seçilen "sahip/depo". */
    val repo: String? = null,
    val ciAttempts: Int = 0,
)

/**
 * promptId'ye FK yok: prompt silinse de geçmiş işler korunur.
 * Hesap silinirse accountId NULL olur.
 */
@Entity(
    tableName = "jobs",
    foreignKeys = [
        ForeignKey(
            entity = RunRecord::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AiAccount::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("runId"), Index("accountId"), Index("promptId"), Index("state")],
)
data class Job(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val promptId: Long,
    val accountId: Long? = null,
    val state: JobState = JobState.PENDING,
    val attempts: Int = 0,
    /** filesDir'e göre göreli yol (ör. "outputs/3/7.txt"). */
    val outputPath: String? = null,
    val updatedAt: Long,
    /** Grafikteki düğüm türü; eski kayıtlar WORK'tür. */
    @ColumnInfo(defaultValue = "WORK") val kind: JobKind = JobKind.WORK,
    /** WORK ve CATALOG_MERGE düğümlerinin ait olduğu katalog adı. */
    val catalog: String? = null,
    /** Düğüm başlığı (prompt başlığı veya birleştirme adı). */
    val title: String? = null,
    /** WORK/PLAN düğümlerinin kendi prompt metni; null ise `promptId` ile prompt tablosundan okunur. */
    val body: String? = null,
)

/**
 * Hesap başına tek bir üretim isteğinin sonucu (kullanım/kota panosu). Gizli bilgi içermez.
 * Hesap silinirse olaylar da gider; eski olaylar [UsageEventDao.prune] ile temizlenir.
 */
@Entity(
    tableName = "usage_events",
    foreignKeys = [
        ForeignKey(
            entity = AiAccount::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId"), Index("at")],
)
data class UsageEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val jobId: Long? = null,
    /** İstek bitiş zamanı (epoch ms). */
    val at: Long,
    val outcome: UsageOutcome,
    val durationMs: Long = 0,
    /** RATE_LIMITED ise sağlayıcının istediği bekleme (ms). */
    val waitMs: Long? = null,
    /** Girdi tokenı; null = bilinmiyor (eski kayıt ya da başarısız istek). */
    val promptTokens: Int? = null,
    /** Çıktı tokenı; null = bilinmiyor. */
    val completionTokens: Int? = null,
    /** true: sağlayıcı kullanım bildirmedi, değerler karakter sayısından tahmin edildi. */
    @ColumnInfo(defaultValue = "0") val tokensEstimated: Boolean = false,
)
