package com.kriptoncod.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AiAccountDao {
    @Query("SELECT * FROM ai_accounts ORDER BY powerRank DESC, id ASC")
    fun observeAll(): Flow<List<AiAccount>>

    @Query("SELECT * FROM ai_accounts WHERE enabled = 1 ORDER BY powerRank DESC, id ASC")
    fun observeEnabled(): Flow<List<AiAccount>>

    @Query("SELECT * FROM ai_accounts WHERE id = :id")
    fun observe(id: Long): Flow<AiAccount?>

    @Query("SELECT * FROM ai_accounts WHERE id = :id")
    suspend fun get(id: Long): AiAccount?

    @Query("SELECT id FROM ai_accounts")
    suspend fun allIds(): List<Long>

    @Insert
    suspend fun insert(account: AiAccount): Long

    @Update
    suspend fun update(account: AiAccount): Int

    @Delete
    suspend fun delete(account: AiAccount): Int

    @Query("DELETE FROM ai_accounts WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("UPDATE ai_accounts SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean): Int

    @Query("UPDATE ai_accounts SET status = :status, waitUntil = :waitUntil WHERE id = :id")
    suspend fun setStatus(id: Long, status: AccountStatus, waitUntil: Long?): Int

    /** Bekleme süresi dolan hesapları READY yapar; etkilenen satır sayısını döndürür. */
    @Query(
        "UPDATE ai_accounts SET status = :ready, waitUntil = NULL " +
            "WHERE status = :waiting AND waitUntil IS NOT NULL AND waitUntil <= :now",
    )
    suspend fun releaseExpiredWaits(now: Long, waiting: AccountStatus, ready: AccountStatus): Int
}

@Dao
interface PromptSetDao {
    @Query("SELECT * FROM prompt_sets ORDER BY id ASC")
    fun observeAll(): Flow<List<PromptSet>>

    @Query("SELECT * FROM prompt_sets WHERE id = :id")
    fun observe(id: Long): Flow<PromptSet?>

    @Query("SELECT * FROM prompt_sets WHERE id = :id")
    suspend fun get(id: Long): PromptSet?

    @Insert
    suspend fun insert(set: PromptSet): Long

    @Update
    suspend fun update(set: PromptSet): Int

    @Query("DELETE FROM prompt_sets WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

@Dao
interface PromptItemDao {
    @Query("SELECT * FROM prompt_items WHERE setId = :setId ORDER BY position ASC, id ASC")
    fun observeBySet(setId: Long): Flow<List<PromptItem>>

    @Query("SELECT * FROM prompt_items WHERE setId = :setId ORDER BY position ASC, id ASC")
    suspend fun getBySet(setId: Long): List<PromptItem>

    @Query("SELECT * FROM prompt_items WHERE id = :id")
    suspend fun get(id: Long): PromptItem?

    @Query("SELECT COALESCE(MAX(position), -1) FROM prompt_items WHERE setId = :setId")
    suspend fun maxPosition(setId: Long): Int

    @Insert
    suspend fun insert(item: PromptItem): Long

    @Update
    suspend fun update(item: PromptItem): Int

    @Query("UPDATE prompt_items SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int): Int

    @Query("DELETE FROM prompt_items WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

@Dao
interface AssignmentDao {
    @Query(
        "SELECT a.* FROM assignments a INNER JOIN prompt_items p ON p.id = a.promptId " +
            "WHERE p.setId = :setId ORDER BY p.position ASC, p.id ASC",
    )
    fun observeForSet(setId: Long): Flow<List<Assignment>>

    @Query("SELECT * FROM assignments WHERE promptId = :promptId")
    suspend fun get(promptId: Long): Assignment?

    @Upsert
    suspend fun upsert(assignment: Assignment)

    @Query("DELETE FROM assignments WHERE promptId = :promptId")
    suspend fun deleteForPrompt(promptId: Long): Int
}

@Dao
interface RunDao {
    @Query("SELECT * FROM runs ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<RunRecord>>

    @Query("SELECT * FROM runs WHERE id = :id")
    fun observe(id: Long): Flow<RunRecord?>

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun get(id: Long): RunRecord?

    @Insert
    suspend fun insert(run: RunRecord): Long

    @Update
    suspend fun update(run: RunRecord): Int

    @Query("UPDATE runs SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: RunStatus): Int

    @Query("UPDATE runs SET repo = :repo WHERE id = :id")
    suspend fun setRepo(id: Long, repo: String?): Int

    @Query("UPDATE runs SET zipPath = :zipPath WHERE id = :id")
    suspend fun setZip(id: Long, zipPath: String?): Int

    @Query("UPDATE runs SET ciAttempts = ciAttempts + 1 WHERE id = :id")
    suspend fun incrementCiAttempts(id: Long): Int

    @Query("DELETE FROM runs WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs WHERE runId = :runId ORDER BY id ASC")
    fun observeByRun(runId: Long): Flow<List<Job>>

    @Query("SELECT * FROM jobs WHERE runId = :runId ORDER BY id ASC")
    suspend fun getByRun(runId: Long): List<Job>

    @Query("SELECT * FROM jobs WHERE state = :state ORDER BY id ASC")
    suspend fun getByState(state: JobState): List<Job>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun get(id: Long): Job?

    @Insert
    suspend fun insert(job: Job): Long

    @Insert
    suspend fun insertAll(jobs: List<Job>): List<Long>

    @Update
    suspend fun update(job: Job): Int

    @Query("UPDATE jobs SET state = :state, updatedAt = :now WHERE id = :id")
    suspend fun setState(id: Long, state: JobState, now: Long): Int

    /** İş başlarken: durum + attempts artışı. */
    @Query("UPDATE jobs SET state = :state, attempts = attempts + 1, updatedAt = :now WHERE id = :id")
    suspend fun markStarted(id: Long, state: JobState, now: Long): Int

    @Query("UPDATE jobs SET state = :state, outputPath = :outputPath, updatedAt = :now WHERE id = :id")
    suspend fun setResult(id: Long, state: JobState, outputPath: String?, now: Long): Int

    @Query("UPDATE jobs SET accountId = :accountId, updatedAt = :now WHERE id = :id")
    suspend fun setAccount(id: Long, accountId: Long?, now: Long): Int

    /** Yeniden deneme: işi sıfırdan başlatır (çıktı yolu temizlenir, deneme sayacı sıfırlanır). */
    @Query("UPDATE jobs SET state = :state, outputPath = NULL, attempts = 0, updatedAt = :now WHERE id = :id")
    suspend fun resetForRetry(id: Long, state: JobState, now: Long): Int
}

/** Hesap başına toplanmış kullanım (bir zaman penceresi için). */
data class AccountUsage(
    val accountId: Long,
    val requests: Int,
    val successes: Int,
    val rateLimited: Int,
    val errors: Int,
    val totalWaitMs: Long,
    val lastAt: Long,
)

@Dao
interface UsageEventDao {
    @Insert
    suspend fun insert(event: UsageEvent): Long

    @Query("DELETE FROM usage_events WHERE at < :cutoff")
    suspend fun prune(cutoff: Long): Int

    /** `outcome` sütunu enum adı olarak saklanır (bkz. Converters). */
    @Query(
        "SELECT accountId, COUNT(*) AS requests, " +
            "SUM(CASE WHEN outcome = 'SUCCESS' THEN 1 ELSE 0 END) AS successes, " +
            "SUM(CASE WHEN outcome = 'RATE_LIMITED' THEN 1 ELSE 0 END) AS rateLimited, " +
            "SUM(CASE WHEN outcome != 'SUCCESS' AND outcome != 'RATE_LIMITED' THEN 1 ELSE 0 END) AS errors, " +
            "COALESCE(SUM(waitMs), 0) AS totalWaitMs, MAX(at) AS lastAt " +
            "FROM usage_events WHERE at >= :since GROUP BY accountId",
    )
    fun observeSummary(since: Long): Flow<List<AccountUsage>>
}
