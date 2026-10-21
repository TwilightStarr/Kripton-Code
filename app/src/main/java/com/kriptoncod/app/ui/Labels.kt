package com.kriptoncod.app.ui

import androidx.annotation.StringRes
import com.kriptoncod.app.R
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus

/** Durum ve tür etiketleri tek yerde, string kaynağı olarak (ekran, bildirim ve widget aynı metni kullanır). */
@StringRes
fun RunStatus.labelRes(): Int = when (this) {
    RunStatus.PREPARING -> R.string.run_status_preparing
    RunStatus.RUNNING -> R.string.run_status_running
    RunStatus.PUSHING -> R.string.run_status_pushing
    RunStatus.CI_WAITING -> R.string.run_status_ci_waiting
    RunStatus.FIXING -> R.string.run_status_fixing
    RunStatus.PAUSED -> R.string.run_status_paused
    RunStatus.DONE -> R.string.run_status_done
    RunStatus.PARTIAL -> R.string.run_status_partial
    RunStatus.FAILED -> R.string.run_status_failed
    RunStatus.CANCELLED -> R.string.run_status_cancelled
}

@StringRes
fun JobState.labelRes(): Int = when (this) {
    JobState.PENDING -> R.string.job_state_pending
    JobState.RUNNING -> R.string.job_state_running
    JobState.WAITING -> R.string.job_state_waiting
    JobState.DONE -> R.string.job_state_done
    JobState.FAILED -> R.string.job_state_failed
}

@StringRes
fun JobKind.labelRes(): Int = when (this) {
    JobKind.PLAN -> R.string.job_kind_plan
    JobKind.WORK -> R.string.job_kind_work
    JobKind.CATALOG_MERGE -> R.string.job_kind_catalog_merge
    JobKind.FINAL_MERGE -> R.string.job_kind_final_merge
}
