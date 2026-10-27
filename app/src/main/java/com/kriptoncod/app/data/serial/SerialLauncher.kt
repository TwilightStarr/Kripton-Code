package com.kriptoncod.app.data.serial

import com.kriptoncod.app.data.repo.PromptRepository

/** Bir prompt setinden seri çalışma başlatır. */
class SerialLauncher(
    private val prompts: PromptRepository,
    private val store: SerialStore,
    private val git: SerialGit,
    private val start: (Long) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    class InvalidSetup(message: String) : Exception(message)

    /**
     * Setten çalışma planını (önizleme için) üretir; hiçbir şey başlatmaz.
     * [InvalidSetup]: set boş.
     */
    suspend fun plan(setId: Long): List<SerialPrompt> {
        val plan = SerialPlanner.plan(prompts.getItems(setId), now())
        if (plan.isEmpty()) throw InvalidSetup("Prompt setinde gönderilecek prompt yok.")
        return plan
    }

    /** @return yeni çalışmanın kimliği. [InvalidSetup]: set boş. [SerialGitException]: repo/token sorunu. */
    suspend fun launch(setId: Long, setName: String, repo: String, accountId: Long): Long =
        launchPlan(setName, repo, accountId, plan(setId))

    /**
     * Hazır (önizlemede düzenlenmiş) planı başlatır. [InvalidSetup]: plan boş.
     * @return yeni çalışmanın kimliği.
     */
    suspend fun launchPlan(name: String, repo: String, accountId: Long, plan: List<SerialPrompt>): Long {
        val clean = plan.filter { it.body.isNotBlank() }
        if (clean.isEmpty()) throw InvalidSetup("Gönderilecek prompt yok.")
        val branch = git.defaultBranch(repo)
        val state = store.create { id ->
            SerialState(
                id = id,
                createdAt = now(),
                name = name,
                repo = repo,
                branch = branch,
                accountId = accountId,
                prompts = clean,
                needsProject = true,
            )
        }
        store.log(state.id, "Başlatıldı: ${clean.size} prompt → $repo@$branch")
        start(state.id)
        return state.id
    }
}
