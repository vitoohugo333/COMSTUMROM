// Durable replay and rollback regression contract.
package com.customrom.adb

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteDurabilityRegressionTest {
    @Test
    fun verifiedChangeUsesRollbackCalculatedFromObservedPreviousState() {
        val job = RemoteJob(
            schema = CustomromJobContract.SCHEMA,
            requestId = "cr-durability-rollback",
            target = "taytech-primary",
            mode = RemoteJobMode.ACTION,
            action = "package.disable",
            args = mapOf("package" to "com.spotify.music"),
            timeoutSeconds = 60,
            allowChanges = true
        )
        val operation = RemoteOperationRegistry().resolve(job)
        val change = VerifiedRemoteChange(
            job = job,
            operation = operation,
            previousState = "state=disabled",
            currentState = "state=disabled",
            outcome = RemoteShellOutcome("already disabled", "", 0, 5, null)
        )

        assertEquals("", change.rollbackCommand)
    }

    @Test
    fun replayIdentityRemainsKnownAfterMoreThanFiveHundredLaterJobs() {
        val store = IssueJobStore(tempFile())
        store.markClaimed("cr-history-0000", "digest-original", effectful = false)
        store.markTerminal("cr-history-0000", RemoteJobState.COMPLETED, "receipt-original")

        for (index in 1..505) {
            val id = "cr-history-" + index.toString().padStart(4, '0')
            store.markClaimed(id, "digest-" + index, effectful = false)
            store.markTerminal(id, RemoteJobState.COMPLETED, "receipt-" + index)
        }

        assertEquals(ReplayDecision.REUSE_TERMINAL, store.check("cr-history-0000", "digest-original"))
        assertEquals(ReplayDecision.CONFLICT, store.check("cr-history-0000", "different-digest"))
    }

    private fun tempFile(): File =
        Files.createTempDirectory("customrom-durability-regression").resolve("jobs.json").toFile()
}
