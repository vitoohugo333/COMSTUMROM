package com.customrom.adb

import java.io.File
import java.nio.file.Files
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteJobCoordinatorTest {
    @Test
    fun greenJobExecutesAndPersistsTerminalBeforePublishing() {
        val store = store()
        val executor = FakeCommandPort()
        val publisher = FakePublisher(
            onComment = {
                assertEquals(RemoteJobState.COMPLETED, store.get("cr-20260927-0400")?.state)
            }
        )
        val coordinator = coordinator(store, executor, publisher)
        val result = coordinator.handle(issue("cr-20260927-0400", shell = "getprop ro.product.model"))
        assertEquals(RemoteHandleResult.STARTED, result)
        assertEquals(1, executor.commands.size)
        assertEquals(RemoteJobState.COMPLETED, store.get("cr-20260927-0400")?.state)
        assertEquals(1, publisher.comments.size)
        assertEquals(1, publisher.closed.size)
    }

    @Test
    fun rejectedAdmissionIsClosedSoItDoesNotSpamEveryPoll() {
        val store = store()
        val executor = FakeCommandPort()
        val publisher = FakePublisher()
        val coordinator = coordinator(store, executor, publisher)
        val badIssue = issue("cr-20260927-0490", shell = "getprop").copy(authorLogin = "intruder")

        val result = coordinator.handle(badIssue)

        assertEquals(RemoteHandleResult.REJECTED, result)
        assertTrue(executor.commands.isEmpty())
        assertEquals(1, publisher.commentAttempts)
        assertEquals(listOf(badIssue.number), publisher.closed)
    }

    @Test
    fun yellowJobWithoutAllowChangesIsRejectedWithoutAdb() {
        val store = store()
        val executor = FakeCommandPort()
        val publisher = FakePublisher()
        val coordinator = coordinator(store, executor, publisher)
        val result = coordinator.handle(
            issue("cr-20260927-0401", shell = "pm disable-user --user 0 com.spotify.music", allowChanges = false)
        )
        assertEquals(RemoteHandleResult.REJECTED, result)
        assertTrue(executor.commands.isEmpty())
        assertEquals(RemoteJobState.REJECTED, store.get("cr-20260927-0401")?.state)
    }

    @Test
    fun redJobIsBlockedEvenWhenChangesWereRequested() {
        val store = store()
        val executor = FakeCommandPort()
        val publisher = FakePublisher()
        val coordinator = coordinator(store, executor, publisher)
        val result = coordinator.handle(
            issue("cr-20260927-0402", shell = "pm uninstall com.spotify.music", allowChanges = true)
        )
        assertEquals(RemoteHandleResult.REJECTED, result)
        assertTrue(executor.commands.isEmpty())
        assertEquals(RemoteJobState.REJECTED, store.get("cr-20260927-0402")?.state)
    }

    @Test
    fun effectfulMainCommandIsMarkedRunningBeforeExecution() {
        val store = store()
        val observedStates = mutableListOf<RemoteJobState?>()
        val executor = FakeCommandPort(
            onExecute = { command ->
                if (command.startsWith("pm disable-user")) {
                    observedStates += store.get("cr-20260927-0403")?.state
                }
            }
        )
        val coordinator = coordinator(store, executor, FakePublisher())
        coordinator.handle(
            actionIssue(
                requestId = "cr-20260927-0403",
                action = "package.disable",
                args = """{"package":"com.spotify.music"}""",
                allowChanges = true
            )
        )
        assertEquals(listOf(RemoteJobState.RUNNING), observedStates)
        assertEquals(RemoteJobState.COMPLETED, store.get("cr-20260927-0403")?.state)
    }

    @Test
    fun successfulTypedChangeRunsVerificationBeforeTerminalReceipt() {
        val store = store()
        val executor = FakeCommandPort(
            outcomes = ArrayDeque(
                listOf(
                    ok("state=enabled"),
                    ok("Package disabled"),
                    ok("state=disabled")
                )
            )
        )
        val publisher = FakePublisher()
        val coordinator = coordinator(store, executor, publisher)
        coordinator.handle(
            actionIssue(
                requestId = "cr-20260927-0407",
                action = "package.disable",
                args = """{"package":"com.spotify.music"}""",
                allowChanges = true
            )
        )

        assertEquals(3, executor.commands.size)
        assertTrue(executor.commands[2].contains("pm list packages -d"))
        assertTrue(executor.commands[2].contains("echo state=disabled"))
        assertEquals(RemoteJobState.COMPLETED, store.get("cr-20260927-0407")?.state)
        val receipt = store.get("cr-20260927-0407")?.receipt.orEmpty()
        assertTrue(receipt.contains("Estado anterior: state=enabled"))
        assertTrue(receipt.contains("Estado atual: state=disabled"))
    }

    @Test
    fun verificationTransportFailureAfterEffectIsUncertain() {
        val store = store()
        val executor = FakeCommandPort(
            outcomes = ArrayDeque(
                listOf(
                    ok("state=enabled"),
                    ok("Package disabled"),
                    RemoteShellOutcome("", "", -1, 80, IllegalStateException("verify link lost"))
                )
            )
        )
        val coordinator = coordinator(store, executor, FakePublisher())
        coordinator.handle(
            actionIssue(
                requestId = "cr-20260927-0408",
                action = "package.disable",
                args = """{"package":"com.spotify.music"}""",
                allowChanges = true
            )
        )

        assertEquals(3, executor.commands.size)
        assertEquals(RemoteJobState.UNCERTAIN, store.get("cr-20260927-0408")?.state)
    }

    @Test
    fun receiptFailureDoesNotReplayAdbAndNextPollRetriesOnlyReceipt() {
        val store = store()
        val executor = FakeCommandPort()
        val publisher = FakePublisher(failComments = 1)
        val coordinator = coordinator(store, executor, publisher)
        val jobIssue = issue("cr-20260927-0404", shell = "getprop ro.product.model")
        coordinator.handle(jobIssue)
        assertEquals(1, executor.commands.size)
        assertEquals(RemoteJobState.COMPLETED, store.get("cr-20260927-0404")?.state)
        val second = coordinator.handle(jobIssue)
        assertEquals(RemoteHandleResult.REPLAYED, second)
        assertEquals(1, executor.commands.size)
        assertEquals(2, publisher.commentAttempts)
        assertEquals(1, publisher.comments.size)
    }

    @Test
    fun effectfulTransportFailureIsUncertainAndNeverBlindlyReplayed() {
        val store = store()
        val executor = FakeCommandPort(
            outcomes = ArrayDeque(
                listOf(
                    ok("state=enabled"),
                    RemoteShellOutcome("", "", -1, 120, IllegalStateException("session dropped"))
                )
            )
        )
        val coordinator = coordinator(store, executor, FakePublisher())
        val jobIssue = actionIssue(
            requestId = "cr-20260927-0405",
            action = "package.disable",
            args = """{"package":"com.spotify.music"}""",
            allowChanges = true
        )
        coordinator.handle(jobIssue)
        assertEquals(RemoteJobState.UNCERTAIN, store.get("cr-20260927-0405")?.state)
        val count = executor.commands.size
        val again = coordinator.handle(jobIssue)
        assertEquals(RemoteHandleResult.REPLAYED, again)
        assertEquals(count, executor.commands.size)
    }

    @Test
    fun successfulEffectRunsVerificationAndPublishesObservedCurrentState() {
        val store = store()
        val executor = FakeCommandPort(
            outcomes = ArrayDeque(
                listOf(
                    ok("state=enabled"),
                    ok("Package com.spotify.music new state: disabled-user"),
                    ok("state=disabled")
                )
            )
        )
        val coordinator = coordinator(store, executor, FakePublisher())
        val jobIssue = actionIssue(
            requestId = "cr-20260927-0407",
            action = "package.disable",
            args = """{"package":"com.spotify.music"}""",
            allowChanges = true
        )

        coordinator.handle(jobIssue)

        assertEquals(3, executor.commands.size)
        assertTrue(executor.commands.last().contains("pm list packages -d"))
        val stored = store.get("cr-20260927-0407")
        assertEquals(RemoteJobState.COMPLETED, stored?.state)
        assertTrue(stored?.receipt.orEmpty().contains("Estado atual: state=disabled"))
    }

    @Test
    fun verificationTransportFailureAfterEffectBecomesUncertain() {
        val store = store()
        val executor = FakeCommandPort(
            outcomes = ArrayDeque(
                listOf(
                    ok("state=enabled"),
                    ok("Package com.spotify.music new state: disabled-user"),
                    RemoteShellOutcome("", "", -1, 25, IllegalStateException("verification link dropped"))
                )
            )
        )
        val coordinator = coordinator(store, executor, FakePublisher())

        coordinator.handle(
            actionIssue(
                requestId = "cr-20260927-0408",
                action = "package.disable",
                args = """{"package":"com.spotify.music"}""",
                allowChanges = true
            )
        )

        assertEquals(3, executor.commands.size)
        assertEquals(RemoteJobState.UNCERTAIN, store.get("cr-20260927-0408")?.state)
    }

    @Test
    fun verifiedEffectEmitsChangeRecordDataOnce() {
        val store = store()
        val executor = FakeCommandPort(
            outcomes = ArrayDeque(
                listOf(
                    ok("state=enabled"),
                    ok("Package disabled"),
                    ok("state=disabled")
                )
            )
        )
        val changes = mutableListOf<VerifiedRemoteChange>()
        val coordinator = RemoteJobCoordinator(
            config = GitHubControlConfig.defaults().copy(enabled = true),
            registry = RemoteOperationRegistry(),
            store = store,
            commandPort = executor,
            publisher = FakePublisher(),
            onVerifiedChange = { changes += it }
        )

        coordinator.handle(
            actionIssue(
                requestId = "cr-20260927-0410",
                action = "package.disable",
                args = """{"package":"com.spotify.music"}""",
                allowChanges = true
            )
        )

        assertEquals(1, changes.size)
        val change = changes.single()
        assertEquals("package.disable", change.job.action)
        assertEquals("com.spotify.music", change.job.args["package"])
        assertEquals("state=enabled", change.previousState)
        assertEquals("state=disabled", change.currentState)
        assertEquals("pm enable com.spotify.music", change.operation.rollbackCommand)
        assertEquals(0, change.outcome.exitCode)
    }

    @Test
    fun busyCommandPortDefersIssueWithoutConsumingIt() {
        val store = store()
        val executor = FakeCommandPort().apply { available = false }
        val publisher = FakePublisher()
        val coordinator = coordinator(store, executor, publisher)
        val jobIssue = issue("cr-20260927-0409", shell = "getprop ro.product.model")

        val result = coordinator.handle(jobIssue)

        assertEquals(RemoteHandleResult.BUSY, result)
        assertEquals(ReplayDecision.NEW, store.check("cr-20260927-0409", CustomromJobContract.canonicalDigest(CustomromJobContract.parse(jobIssue.body))))
        assertTrue(executor.commands.isEmpty())
        assertEquals(0, publisher.commentAttempts)
        assertTrue(publisher.closed.isEmpty())
    }

    @Test
    fun claimedBeforeEffectRecoversAsNewAfterProcessRestart() {
        val file = tempFile()
        IssueJobStore(file).markClaimed("cr-20260927-0406", "digest-a", effectful = true)
        val recovered = IssueJobStore(file)
        assertEquals(ReplayDecision.NEW, recovered.check("cr-20260927-0406", "digest-a"))
    }

    private fun coordinator(
        store: IssueJobStore,
        executor: FakeCommandPort,
        publisher: FakePublisher
    ) = RemoteJobCoordinator(
        config = GitHubControlConfig.defaults().copy(enabled = true),
        registry = RemoteOperationRegistry(),
        store = store,
        commandPort = executor,
        publisher = publisher
    )

    private fun store(): IssueJobStore = IssueJobStore(tempFile())

    private fun tempFile(): File =
        Files.createTempDirectory("customrom-coordinator").resolve("jobs.json").toFile()

    private fun issue(
        requestId: String,
        shell: String,
        allowChanges: Boolean = false
    ): RemoteGitHubIssue = RemoteGitHubIssue(
        number = requestId.takeLast(2).toLongOrNull() ?: 80L,
        title = "[CUSTOMROM JOB] $requestId",
        body = """
            {
              "schema":"customrom.adb.job.v1",
              "requestId":"$requestId",
              "target":"taytech-primary",
              "mode":"shell",
              "command":${jsonString(shell)},
              "timeoutSeconds":60,
              "allowChanges":$allowChanges
            }
        """.trimIndent(),
        authorLogin = "viluadmcontas2-dot",
        htmlUrl = "https://github.com/viluadmcontas2-dot/AgentRed/issues/80"
    )

    private fun actionIssue(
        requestId: String,
        action: String,
        args: String,
        allowChanges: Boolean
    ): RemoteGitHubIssue = RemoteGitHubIssue(
        number = 81L,
        title = "[CUSTOMROM JOB] $requestId",
        body = """
            {
              "schema":"customrom.adb.job.v1",
              "requestId":"$requestId",
              "target":"taytech-primary",
              "mode":"action",
              "action":"$action",
              "args":$args,
              "timeoutSeconds":60,
              "allowChanges":$allowChanges
            }
        """.trimIndent(),
        authorLogin = "viluadmcontas2-dot",
        htmlUrl = "https://github.com/viluadmcontas2-dot/AgentRed/issues/81"
    )

    private fun jsonString(value: String): String =
        org.json.JSONObject.quote(value)

    private fun ok(stdout: String = "ok") =
        RemoteShellOutcome(stdout, "", 0, 10, null)

    private class FakeCommandPort(
        private val outcomes: ArrayDeque<RemoteShellOutcome> = ArrayDeque(),
        private val onExecute: (String) -> Unit = {}
    ) : RemoteCommandPort {
        val commands = mutableListOf<String>()
        var available = true

        override fun isAvailable(): Boolean = available

        override fun execute(
            command: String,
            timeoutMs: Long,
            callback: (RemoteShellOutcome) -> Unit
        ): Boolean {
            if (!available) return false
            commands += command
            onExecute(command)
            callback(if (outcomes.isEmpty()) RemoteShellOutcome("ok", "", 0, 10, null) else outcomes.removeFirst())
            return true
        }
    }

    private class FakePublisher(
        private var failComments: Int = 0,
        private val onComment: (String) -> Unit = {}
    ) : RemoteReceiptPublisher {
        val comments = mutableListOf<String>()
        val closed = mutableListOf<Long>()
        var commentAttempts = 0

        override fun comment(issueNumber: Long, body: String) {
            commentAttempts++
            if (failComments > 0) {
                failComments--
                throw IllegalStateException("github unavailable")
            }
            onComment(body)
            comments += body
        }

        override fun close(issueNumber: Long) {
            closed += issueNumber
        }
    }
}
