package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.ai.AiGenerationException
import com.kriptoncod.app.data.ai.AiGenerator
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.kimi.KimiCodeRepository
import com.kriptoncod.app.data.providers.ProviderCatalog
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.data.repo.RunRepository
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first

class FinalAiAssembler(
    private val runs: RunRepository,
    private val accounts: AiAccountRepository,
    private val credentials: CredentialProvider,
    private val generator: AiGenerator,
    private val kimiCodes: KimiCodeRepository,
    private val resolveEndpoint: (AiAccount) -> com.kriptoncod.app.data.providers.ProviderEndpoint? =
        { ProviderCatalog.byId(it.providerId)?.resolve() },
) {

    suspend fun assemble(runId: Long): Boolean {
        if (!kimiCodes.hasOutputs(runId)) return false

        val doneJobs = runs.getJobs(runId).filter { it.state == JobState.DONE }
        val accCache = mutableMapOf<Long, AiAccount?>()
        fun authTypeOf(accountId: Long?) =
            accountId?.let { id -> accCache.getOrPut(id) { accounts.get(id) }?.authType }
        if (doneJobs.isEmpty() || doneJobs.any { authTypeOf(it.accountId) != AuthType.KIMI }) {
            return false
        }

        val blocks = kimiCodes.listBlocks(runId)
        if (blocks.isEmpty()) return false

        val finalAccount = accounts.observeAll().first()
            .filter { it.enabled && it.status == AccountStatus.READY && it.authType == AuthType.API_KEY }
            .maxByOrNull { it.powerRank } ?: return false
        val endpoint = resolveEndpoint(finalAccount) ?: return false
        val credential = credentials.credentialFor(finalAccount) ?: return false

        val prompt = buildString {
            appendLine("Aşağıdaki dosyalar bir AI (Kimi) tarafından üretildi. " +
                "Eksikleri tamamla, çakışan/uyumsuz kısımları çöz, derlenir ve tutarlı " +
                "tek bir paket hâline getir. Yalnızca nihai dosyaları " +
                "```tam/dosya/yolu biçiminde kod bloklarıyla döndür; açıklama yazma.")
            appendLine()
            blocks.forEach { b ->
                appendLine("### ${b.path}")
                appendLine("```${b.path}")
                appendLine(b.content)
                appendLine("```")
                appendLine()
            }
        }

        val text = try {
            generator.generate(endpoint, finalAccount.modelId, credential, prompt)
        } catch (e: AiGenerationException) {
            return false
        }

        val finalBlocks = CodeBlockParser.parse(text).mapNotNull { b ->
            when (val r = PathSafety.validate(b.path)) {
                is PathSafety.Result.Safe -> b.copy(path = r.normalized)
                is PathSafety.Result.Rejected -> null
            }
        }
        if (finalBlocks.isEmpty()) return false

        runs.attachZip(runId) { os ->
            ZipOutputStream(os).use { zos ->
                for (b in finalBlocks) {
                    zos.putNextEntry(ZipEntry(b.path))
                    zos.write(b.content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
        }
        runs.setRunStatus(runId, RunStatus.DONE)
        runs.saveHistory(runId)
        return true
    }
}
