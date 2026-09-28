package com.customrom.adb

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSafetyRegressionTest {
    @Test
    fun mutatingOrStructuralShellSurfacesAreNeverGreen() {
        assertEquals("VERMELHO", PremiumSafetyPolicy.classify("echo 1 > /sys/class/example/control"))
        assertEquals("VERMELHO", PremiumSafetyPolicy.classify("su -c id"))
        assertEquals("VERMELHO", PremiumSafetyPolicy.classify("service call vehicle 1 i32 1"))
        assertEquals("AMARELO", PremiumSafetyPolicy.classify("pm grant com.example android.permission.WRITE_SECURE_SETTINGS"))
        assertEquals("AMARELO", PremiumSafetyPolicy.classify("settings delete global animator_duration_scale"))
    }

    @Test
    fun protectedAutomotiveMutationThroughShellIsRedWhileInspectionStaysGreen() {
        val registry = RemoteOperationRegistry()

        val mutation = registry.resolve(shellJob("pm disable-user --user 0 com.jancar.launcher", allowChanges = true))
        val inspection = registry.resolve(shellJob("dumpsys package com.jancar.launcher"))

        assertEquals("VERMELHO", mutation.risk)
        assertEquals("VERDE", inspection.risk)
    }

    @Test
    fun packageRollbackOnlyRestoresAStateThatActuallyChanged() {
        val registry = RemoteOperationRegistry()
        val disable = registry.resolve(actionJob("package.disable", mapOf("package" to "com.spotify.music"), true))
        val enable = registry.resolve(actionJob("package.enable", mapOf("package" to "com.spotify.music"), true))

        assertEquals("pm enable com.spotify.music", disable.rollbackCommandFor("state=enabled"))
        assertEquals("", disable.rollbackCommandFor("state=disabled"))
        assertEquals("pm disable-user --user 0 com.spotify.music", enable.rollbackCommandFor("state=disabled"))
        assertEquals("", enable.rollbackCommandFor("state=enabled"))
    }

    @Test
    fun animationRollbackRestoresAbsentSettingsInsteadOfLosingPreviousState() {
        val registry = RemoteOperationRegistry()
        val operation = registry.resolve(
            actionJob("settings.animations", mapOf("enabled" to "false"), true)
        )

        val rollback = operation.rollbackCommandFor(
            "window=null\ntransition=0.5\nanimator=null"
        )

        assertTrue(rollback.contains("settings delete global window_animation_scale"))
        assertTrue(rollback.contains("settings put global transition_animation_scale 0.5"))
        assertTrue(rollback.contains("settings delete global animator_duration_scale"))
    }

    @Test
    fun uncertainReceiptRetryNeverReexecutesAdbAndClosesIssueAfterSuccessfulPublication() {
        val store = IssueJobStore(tempFile())
        val port = FakeCommandPort(
            ArrayDeque(
                listOf(
                    RemoteShellOutcome("state=enabled", "", 0, 5, null),
                    RemoteShellOutcome("", "", -1, 10, IllegalStateException("link lost"))
                )
            )
        )
        val publisher = FakePublisher(failComments = 1)
        val coordinator = RemoteJobCoordinator(
            config = GitHubControlConfig.defaults().copy(enabled = true),
            registry = RemoteOperationRegistry(),
            store = store,
            commandPort = port,
            publisher = publisher
        )
        val issue = actionIssue(
            requestId = "cr-20260927-safety1",
            action = "package.disable",
            args = mapOf("package" to "com.spotify.music"),
            allowChanges = true
        )

        coordinator.handle(issue)
        assertEquals(RemoteJobState.UNCERTAIN, store.get("cr-20260927-safety1")?.state)
        val adbCount = port.commands.size
        assertTrue(publisher.closed.isEmpty())

        val replay = coordinator.handle(issue)

        assertEquals(RemoteHandleResult.REPLAYED, replay)
        assertEquals(adbCount, port.commands.size)
        assertEquals(2, publisher.commentAttempts)
        assertEquals(listOf(issue.number), publisher.closed)
    }

    private fun shellJob(command: String, allowChanges: Boolean = false) = RemoteJob(
        schema = CustomromJobContract.SCHEMA,
        requestId = "cr-20260927-shell1",
        target = "taytech-primary",
        mode = RemoteJobMode.SHELL,
        command = command,
        timeoutSeconds = 60,
        allowChanges = allowChanges
    )

    private fun actionJob(action: String, args: Map<String, String>, allowChanges: Boolean) = RemoteJob(
        schema = CustomromJobContract.SCHEMA,
        requestId = "cr-20260927-action1",
        target = "taytech-primary",
        mode = RemoteJobMode.ACTION,
        action = action,
        args = args,
        timeoutSeconds = 60,
        allowChanges = allowChanges
    )

    private fun actionIssue(
        requestId: String,
        action: String,
        args: Map<String, String>,
        allowChanges: Boolean
    ): RemoteGitHubIssue {
        val argsJson = org.json.JSONObject(args).toString()
        return RemoteGitHubIssue(
            number = 990L,
            title = "[CUSTOMROM JOB] safety regression",
            body = """
                {
                  "schema":"customrom.adb.job.v1",
                  "requestId":"$requestId",
                  "target":"taytech-primary",
                  "mode":"action",
                  "action":"$action",
                  "args":$argsJson,
                  "timeoutSeconds":60,
                  "allowChanges":$allowChanges
                }
            """.trimIndent(),
            authorLogin = "viluadmcontas2-dot",
            htmlUrl = "https://github.com/viluadmcontas2-dot/AgentRed/issues/990"
        )
    }

    private fun tempFile(): File =
        Files.createTempDirectory("customrom-safety-regression").resolve("jobs.json").toFile()

    private class FakeCommandPort(
        private val outcomes: ArrayDeque<RemoteShellOutcome>
    ) : RemoteCommandPort {
        val commands = mutableListOf<String>()

        override fun isAvailable(): Boolean = true

        override fun execute(
            command: String,
            timeoutMs: Long,
            callback: (RemoteShellOutcome) -> Unit
        ): Boolean {
            commands += command
            callback(outcomes.removeFirst())
            return true
        }
    }

    private class FakePublisher(
        private var failComments: Int
    ) : RemoteReceiptPublisher {
        val closed = mutableListOf<Long>()
        var commentAttempts = 0

        override fun comment(issueNumber: Long, body: String) {
            commentAttempts++
            if (failComments > 0) {
                failComments--
                throw IllegalStateException("github unavailable")
            }
        }

        override fun close(issueNumber: Long) {
            closed += issueNumber
        }
    }
}
