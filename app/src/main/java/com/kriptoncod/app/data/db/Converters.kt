package com.kriptoncod.app.data.db

import androidx.room.TypeConverter
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AssignmentMode
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.domain.UsageOutcome

/** Enum'lar ad olarak saklanır; bilinmeyen değer (ör. yeni sürümden) güvenli varsayılana düşer. */
class Converters {
    @TypeConverter fun authTypeToString(v: AuthType): String = v.name

    @TypeConverter fun stringToAuthType(s: String): AuthType = enumOr(s, AuthType.API_KEY)

    @TypeConverter fun accountStatusToString(v: AccountStatus): String = v.name

    @TypeConverter fun stringToAccountStatus(s: String): AccountStatus = enumOr(s, AccountStatus.ERROR)

    @TypeConverter fun assignmentModeToString(v: AssignmentMode): String = v.name

    @TypeConverter fun stringToAssignmentMode(s: String): AssignmentMode = enumOr(s, AssignmentMode.MANUAL)

    @TypeConverter fun jobStateToString(v: JobState): String = v.name

    @TypeConverter fun stringToJobState(s: String): JobState = enumOr(s, JobState.FAILED)

    @TypeConverter fun jobKindToString(v: JobKind): String = v.name

    @TypeConverter fun stringToJobKind(s: String): JobKind = enumOr(s, JobKind.WORK)

    @TypeConverter fun runStatusToString(v: RunStatus): String = v.name

    @TypeConverter fun stringToRunStatus(s: String): RunStatus = enumOr(s, RunStatus.FAILED)

    @TypeConverter fun usageOutcomeToString(v: UsageOutcome): String = v.name

    @TypeConverter fun stringToUsageOutcome(s: String): UsageOutcome = enumOr(s, UsageOutcome.FAILED)
}

private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E = enumValues<E>().firstOrNull { it.name == name } ?: fallback
