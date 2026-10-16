package com.kriptoncod.app.data.github

import com.kriptoncod.app.data.providers.ApiResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class GitHubUser(val login: String)

data class GitHubRepo(val fullName: String, val defaultBranch: String, val isPrivate: Boolean)

/** Commit'lenecek dosya. [path] depo köküne göre göreli, `/` ayraçlı olmalıdır. */
class GitHubFile(val path: String, val content: ByteArray) {
    constructor(path: String, text: String) : this(path, text.toByteArray(Charsets.UTF_8))
}

enum class CiOutcome { SUCCESS, FAILURE }

data class GitHubRun(
    val id: Long,
    val status: String,
    val conclusion: String?,
    val headSha: String,
    val htmlUrl: String,
) {
    val isCompleted: Boolean get() = status == "completed"

    /** Tamamlanmadıysa null; yalnız `success` başarıdır, diğer tüm sonuçlar (iptal, zaman aşımı…) başarısızdır. */
    val outcome: CiOutcome?
        get() = when {
            !isCompleted -> null
            conclusion == "success" -> CiOutcome.SUCCESS
            else -> CiOutcome.FAILURE
        }
}

/** [GitHubService.awaitRun] sonucu. */
sealed interface RunWait {
    data class Completed(val run: GitHubRun) : RunWait
    data class TimedOut(val lastRun: GitHubRun?) : RunWait
    data class Failed(val failure: ApiResult.Fail) : RunWait
}

/** Doğrulama workflow'u: `kriptoncod/` ile başlayan dallara push'ta çalışır. */
object VerifyWorkflow {
    const val PATH = ".github/workflows/kriptoncod-verify.yml"

    /** Varsa `.kriptoncod/verify.sh` çalıştırır; yoksa üretilen dosyaların boş olmadığını denetler. */
    val YAML: String = """
        name: kriptoncod-verify
        on:
          push:
            branches: ['kriptoncod/**']
          workflow_dispatch:
        permissions:
          contents: read
        jobs:
          verify:
            runs-on: ubuntu-latest
            steps:
              - uses: actions/checkout@v4
              - name: Verify
                shell: bash
                run: |
                  set -euo pipefail
                  if [ -f .kriptoncod/verify.sh ]; then
                    bash .kriptoncod/verify.sh
                  else
                    git ls-files -z | grep -zv '^\.github/' | xargs -0 -r -I{} test -s "{}"
                  fi
    """.trimIndent() + "\n"
}

// --- GitHub REST yanıt DTO'ları (yalnız bu paket) ---

@Serializable internal data class UserDto(val login: String)

@Serializable internal data class RepoPermsDto(val push: Boolean? = null)

@Serializable
internal data class RepoDto(
    @SerialName("full_name") val fullName: String,
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("private") val isPrivate: Boolean = false,
    val archived: Boolean = false,
    val disabled: Boolean = false,
    val permissions: RepoPermsDto? = null,
)

@Serializable internal data class ObjDto(val sha: String)

@Serializable internal data class RefDto(@SerialName("object") val obj: ObjDto)

@Serializable internal data class ShaDto(val sha: String)

@Serializable internal data class CommitDto(val sha: String, val tree: ShaDto)

@Serializable
internal data class RunDto(
    val id: Long,
    val status: String = "",
    val conclusion: String? = null,
    @SerialName("head_sha") val headSha: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
)

@Serializable internal data class RunsDto(@SerialName("workflow_runs") val workflowRuns: List<RunDto> = emptyList())
