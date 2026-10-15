package com.kriptoncod.app.data.db

import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AssignmentMode
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    private val c = Converters()

    @Test fun enumsRoundTrip() {
        AuthType.entries.forEach { assertEquals(it, c.stringToAuthType(c.authTypeToString(it))) }
        AccountStatus.entries.forEach { assertEquals(it, c.stringToAccountStatus(c.accountStatusToString(it))) }
        AssignmentMode.entries.forEach { assertEquals(it, c.stringToAssignmentMode(c.assignmentModeToString(it))) }
        JobState.entries.forEach { assertEquals(it, c.stringToJobState(c.jobStateToString(it))) }
        RunStatus.entries.forEach { assertEquals(it, c.stringToRunStatus(c.runStatusToString(it))) }
    }

    @Test fun unknownValuesFallBackSafely() {
        assertEquals(AccountStatus.ERROR, c.stringToAccountStatus("???"))
        assertEquals(JobState.FAILED, c.stringToJobState("???"))
        assertEquals(RunStatus.FAILED, c.stringToRunStatus("???"))
        assertEquals(AuthType.API_KEY, c.stringToAuthType("???"))
        assertEquals(AssignmentMode.MANUAL, c.stringToAssignmentMode("???"))
    }
}
