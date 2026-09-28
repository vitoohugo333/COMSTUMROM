// Contract tests for the GitHub-to-ADB bridge.
package com.customrom.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomromJobContractTest {
    @Test
    fun parsesTypedActionContract() {
        val job = CustomromJobContract.parse(
            """
            {
              "schema":"customrom.adb.job.v1",
              "requestId":"cr-20260927-0001",
              "target":"taytech-primary",
              "mode":"action",
              "action":"diagnostic.memory",
              "args":{"package":"com.spotify.music"},
              "timeoutSeconds":60,
              "allowChanges":false
            }
            """.trimIndent()
        )
        assertEquals(RemoteJobMode.ACTION, job.mode)
        assertEquals("diagnostic.memory", job.action)
        assertEquals("com.spotify.music", job.args["package"])
        assertFalse(job.allowChanges)
    }

    @Test
    fun parsesExplicitShellContract() {
        val job = CustomromJobContract.parse(
            """
            {
              "schema":"customrom.adb.job.v1",
              "requestId":"cr-20260927-0002",
              "target":"taytech-primary",
              "mode":"shell",
              "command":"dumpsys activity services",
              "timeoutSeconds":45,
              "allowChanges":false
            }
            """.trimIndent()
        )
        assertEquals(RemoteJobMode.SHELL, job.mode)
        assertEquals("dumpsys activity services", job.command)
        assertEquals(45, job.timeoutSeconds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownSchema() {
        CustomromJobContract.parse(
            """{"schema":"customrom.adb.job.v2","requestId":"cr-20260927-0003","target":"taytech-primary","mode":"shell","command":"getprop","timeoutSeconds":30,"allowChanges":false}"""
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedRequestId() {
        CustomromJobContract.parse(
            """{"schema":"customrom.adb.job.v1","requestId":"../../oops","target":"taytech-primary","mode":"shell","command":"getprop","timeoutSeconds":30,"allowChanges":false}"""
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownMaterialField() {
        CustomromJobContract.parse(
            """{"schema":"customrom.adb.job.v1","requestId":"cr-20260927-0004","target":"taytech-primary","mode":"shell","command":"getprop","timeoutSeconds":30,"allowChanges":false,"magic":true}"""
        )
    }

    @Test
    fun canonicalDigestIsStableAcrossArgumentOrderAndChangesWithContract() {
        val first = CustomromJobContract.parse(
            """{"schema":"customrom.adb.job.v1","requestId":"cr-20260927-0005","target":"taytech-primary","mode":"action","action":"package.inspect","args":{"b":"2","a":"1"},"timeoutSeconds":60,"allowChanges":false}"""
        )
        val reordered = CustomromJobContract.parse(
            """{"allowChanges":false,"timeoutSeconds":60,"args":{"a":"1","b":"2"},"action":"package.inspect","mode":"action","target":"taytech-primary","requestId":"cr-20260927-0005","schema":"customrom.adb.job.v1"}"""
        )
        val changed = reordered.copy(action = "diagnostic.memory")
        assertEquals(CustomromJobContract.canonicalDigest(first), CustomromJobContract.canonicalDigest(reordered))
        assertNotEquals(CustomromJobContract.canonicalDigest(first), CustomromJobContract.canonicalDigest(changed))
        assertTrue(CustomromJobContract.canonicalDigest(first).matches(Regex("[0-9a-f]{64}")))
    }
    @Test
    fun parsesPersistentConsoleSessionMetadata() {
        val job = CustomromJobContract.parse(
            """{"schema":"customrom.adb.job.v1","requestId":"cr-console-0001","sessionId":"console-20260928","sequence":1,"target":"taytech-primary","mode":"shell","command":"getprop ro.product.model","timeoutSeconds":30,"allowChanges":false}"""
        )

        assertEquals("console-20260928", job.sessionId)
        assertEquals(1L, job.sequence)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSessionWithoutPositiveSequence() {
        CustomromJobContract.parse(
            """{"schema":"customrom.adb.job.v1","requestId":"cr-console-0002","sessionId":"console-20260928","target":"taytech-primary","mode":"shell","command":"getprop","timeoutSeconds":30,"allowChanges":false}"""
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSequenceWithoutSessionId() {
        CustomromJobContract.parse(
            """{"schema":"customrom.adb.job.v1","requestId":"cr-console-0003","sequence":1,"target":"taytech-primary","mode":"shell","command":"getprop","timeoutSeconds":30,"allowChanges":false}"""
        )
    }

}
