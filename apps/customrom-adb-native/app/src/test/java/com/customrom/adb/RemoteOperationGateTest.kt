package com.customrom.adb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteOperationGateTest {
    @Test
    fun localWorkIsBlockedForTheWholeClaimedAndRunningRemoteWindow() {
        val gate = RemoteOperationGate()

        assertTrue(gate.canStartLocal())

        gate.onRemoteState(RemoteJobState.CLAIMED)
        assertFalse(gate.canStartLocal())

        gate.onRemoteState(RemoteJobState.RUNNING)
        assertFalse(gate.canStartLocal())

        gate.onRemoteState(RemoteJobState.COMPLETED)
        assertTrue(gate.canStartLocal())
    }

    @Test
    fun terminalAndUncertainRemoteStatesReleaseLocalWork() {
        val gate = RemoteOperationGate()

        for (terminal in listOf(
            RemoteJobState.COMPLETED,
            RemoteJobState.FAILED,
            RemoteJobState.REJECTED,
            RemoteJobState.UNCERTAIN
        )) {
            gate.onRemoteState(RemoteJobState.CLAIMED)
            assertFalse(gate.canStartLocal())
            gate.onRemoteState(terminal)
            assertTrue(gate.canStartLocal())
        }
    }
}
