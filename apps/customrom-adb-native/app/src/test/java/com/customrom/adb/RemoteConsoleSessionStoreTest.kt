package com.customrom.adb

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteConsoleSessionStoreTest {
    @Test
    fun firstSessionCommandMustStartAtSequenceOne() {
        val store = RemoteConsoleSessionStore(tempFile())

        assertEquals(
            ConsoleSequenceDecision.ACCEPT,
            store.evaluate(job("console-a", 1L, "req-1"))
        )
        assertEquals(
            ConsoleSequenceDecision.WAIT,
            store.evaluate(job("console-a", 2L, "req-2"))
        )
    }

    @Test
    fun deliveredReceiptUnlocksExactlyTheNextSequenceAndPersistsAcrossRestart() {
        val file = tempFile()
        RemoteConsoleSessionStore(file).markDelivered("console-a", 1L, "req-1", RemoteJobState.COMPLETED)

        val recovered = RemoteConsoleSessionStore(file)

        assertEquals(1L, recovered.get("console-a")?.lastDeliveredSequence)
        assertEquals(ConsoleSequenceDecision.ACCEPT, recovered.evaluate(job("console-a", 2L, "req-2")))
        assertEquals(ConsoleSequenceDecision.REJECT_STALE, recovered.evaluate(job("console-a", 1L, "different-request")))
        assertEquals(ConsoleSequenceDecision.WAIT, recovered.evaluate(job("console-a", 3L, "req-3")))
    }

    @Test
    fun sessionHistoryDoesNotSilentlyForgetOlderSessions() {
        val file = tempFile()
        val store = RemoteConsoleSessionStore(file)
        for (index in 1..201) {
            store.markDelivered(
                sessionId = "console-" + index,
                sequence = 1L,
                requestId = "req-" + index,
                state = RemoteJobState.COMPLETED
            )
        }

        val recovered = RemoteConsoleSessionStore(file)

        assertEquals(1L, recovered.get("console-1")?.lastDeliveredSequence)
        assertEquals(
            ConsoleSequenceDecision.REJECT_STALE,
            recovered.evaluate(job("console-1", 1L, "different-request"))
        )
    }

    private fun job(sessionId: String, sequence: Long, requestId: String) = RemoteJob(
        schema = CustomromJobContract.SCHEMA,
        requestId = requestId,
        sessionId = sessionId,
        sequence = sequence,
        target = "taytech-primary",
        mode = RemoteJobMode.SHELL,
        command = "getprop ro.product.model"
    )

    private fun tempFile(): File =
        Files.createTempDirectory("customrom-console-session").resolve("sessions.json").toFile()
}
