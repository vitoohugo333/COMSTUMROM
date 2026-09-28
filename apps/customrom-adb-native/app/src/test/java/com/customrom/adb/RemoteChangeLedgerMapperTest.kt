package com.customrom.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteChangeLedgerMapperTest {
    @Test
    fun mapsVerifiedPackageDisableWithObservedRollback() {
        val change = change(
            action = "package.disable",
            args = mapOf("package" to "com.spotify.music"),
            previous = "state=enabled",
            current = "state=disabled"
        )

        val record = RemoteChangeLedgerMapper.toRecord(change, "session-1", 100L)

        assertEquals("com.spotify.music", record?.packageName)
        assertEquals("disable", record?.action)
        assertEquals("pm enable com.spotify.music", record?.rollbackCommand)
    }

    @Test
    fun mapsVerifiedAnimationChangeWithExactObservedRollback() {
        val change = change(
            action = "settings.animations",
            args = mapOf("enabled" to "false"),
            previous = "window=null\ntransition=0.5\nanimator=1.0",
            current = "window=0\ntransition=0\nanimator=0"
        )

        val record = RemoteChangeLedgerMapper.toRecord(change, "session-2", 200L)

        assertEquals("android.settings.animations", record?.packageName)
        assertEquals("settings-animations", record?.action)
        assertEquals(change.previousState, record?.previousState)
        assertEquals(change.currentState, record?.newState)
        assertTrue(record?.rollbackCommand.orEmpty().contains("settings delete global window_animation_scale"))
        assertTrue(record?.rollbackCommand.orEmpty().contains("settings put global transition_animation_scale 0.5"))
    }

    @Test
    fun ignoresReadOnlyOrUnmappedActions() {
        val change = change(
            action = "diagnostic.memory",
            args = emptyMap(),
            previous = "",
            current = ""
        )

        assertNull(RemoteChangeLedgerMapper.toRecord(change, "session-3", 300L))
    }

    private fun change(
        action: String,
        args: Map<String, String>,
        previous: String,
        current: String
    ): VerifiedRemoteChange {
        val job = RemoteJob(
            schema = CustomromJobContract.SCHEMA,
            requestId = "cr-ledger-1",
            target = "taytech-primary",
            mode = RemoteJobMode.ACTION,
            action = action,
            args = args,
            timeoutSeconds = 60,
            allowChanges = true
        )
        val operation = RemoteOperationRegistry().resolve(job)
        return VerifiedRemoteChange(
            job = job,
            operation = operation,
            previousState = previous,
            currentState = current,
            outcome = RemoteShellOutcome("ok", "", 0, 5, null)
        )
    }
}
