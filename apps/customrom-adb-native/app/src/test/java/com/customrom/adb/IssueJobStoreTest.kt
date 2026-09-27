package com.customrom.adb

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class IssueJobStoreTest {
    private fun tempFile(): File = Files.createTempDirectory("customrom-job-store").resolve("jobs.json").toFile()

    @Test
    fun completedSameContractIsReusedWithoutReplay() {
        val file = tempFile()
        val store = IssueJobStore(file)
        store.markClaimed("cr-20260927-0100", "digest-a", effectful = false)
        store.markRunning("cr-20260927-0100")
        store.markTerminal("cr-20260927-0100", RemoteJobState.COMPLETED, "receipt-a")
        assertEquals(ReplayDecision.REUSE_TERMINAL, store.check("cr-20260927-0100", "digest-a"))
        assertEquals("receipt-a", store.get("cr-20260927-0100")?.receipt)
    }

    @Test
    fun changedContractWithSameRequestIdIsConflict() {
        val store = IssueJobStore(tempFile())
        store.markClaimed("cr-20260927-0101", "digest-a", effectful = false)
        assertEquals(ReplayDecision.CONFLICT, store.check("cr-20260927-0101", "digest-b"))
    }

    @Test
    fun runningJobRecoversAsUncertainAfterRestart() {
        val file = tempFile()
        IssueJobStore(file).apply {
            markClaimed("cr-20260927-0102", "digest-a", effectful = true)
            markRunning("cr-20260927-0102")
        }
        val recovered = IssueJobStore(file)
        assertEquals(RemoteJobState.UNCERTAIN, recovered.get("cr-20260927-0102")?.state)
        assertEquals(ReplayDecision.UNCERTAIN, recovered.check("cr-20260927-0102", "digest-a"))
    }

    @Test
    fun terminalReceiptSurvivesProcessRestart() {
        val file = tempFile()
        IssueJobStore(file).apply {
            markClaimed("cr-20260927-0103", "digest-a", effectful = true)
            markRunning("cr-20260927-0103")
            markTerminal("cr-20260927-0103", RemoteJobState.FAILED, "receipt-failed")
        }
        val stored = IssueJobStore(file).get("cr-20260927-0103")
        assertNotNull(stored)
        assertEquals(RemoteJobState.FAILED, stored?.state)
        assertEquals("receipt-failed", stored?.receipt)
    }
}
