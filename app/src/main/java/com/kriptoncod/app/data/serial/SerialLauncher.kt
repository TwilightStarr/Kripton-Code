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

    /** @return yeni çalışmanın kimliği. [InvalidSetup]: set boş. [SerialGitException]: repo/token sorunu. */
    suspend fun launch(setId: Long, setName: String, repo: String, accountId: Long): Long {
        val plan = SerialPlanner.plan(prompts.getItems(setId), now())
        if (plan.isEmpty()) throw InvalidSetup("Prompt setinde gönderilecek prompt yok.")
        val branch = git.defaultBranch(repo)
        val state = store.create { id ->
            SerialState(
                id = id,
                createdAt = now(),
                name = setName,
                repo = repo,
                branch = branch,
                accountId = accountId,
                prompts = plan,
                needsProject = true,
            )
        }
        store.log(state.id, "Başlatıldı: ${plan.size} prompt → $repo@$branch")
        start(state.id)
        return state.id
    }
}
