package com.customrom.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubIssueReceiverTest {
    @Test
    fun pollOnceDispatchesEveryIssueSequentially() {
        val issues = listOf(issue(1), issue(2))
        val source = FakeSource(issues)
        val handler = FakeHandler()
        val states = mutableListOf<String>()
        val receiver = GitHubIssueReceiver(source, handler, pollSeconds = 5) { states += it }

        val count = receiver.pollOnce()

        assertEquals(2, count)
        assertEquals(listOf(1L, 2L), handler.handled)
        assertTrue(states.any { it.contains("2") })
        receiver.close()
    }

    @Test
    fun oneBrokenIssueDoesNotPreventNextIssue() {
        val source = FakeSource(listOf(issue(10), issue(11)))
        val handler = FakeHandler(failOn = 10L)
        val receiver = GitHubIssueReceiver(source, handler, pollSeconds = 5)

        val count = receiver.pollOnce()

        assertEquals(2, count)
        assertEquals(listOf(10L, 11L), handler.handled)
        receiver.close()
    }

    private fun issue(number: Long) = RemoteGitHubIssue(
        number = number,
        title = "[CUSTOMROM JOB] Teste",
        body = "{}",
        authorLogin = "viluadmcontas2-dot",
        htmlUrl = "https://github.com/x/y/issues/" + number
    )

    private class FakeSource(private val issues: List<RemoteGitHubIssue>) : RemoteIssueSource {
        override fun listOpenJobs(): List<RemoteGitHubIssue> = issues
    }

    private class FakeHandler(private val failOn: Long? = null) : RemoteIssueHandler {
        val handled = mutableListOf<Long>()
        override fun handle(issue: RemoteGitHubIssue): RemoteHandleResult {
            handled += issue.number
            if (issue.number == failOn) throw IllegalStateException("boom")
            return RemoteHandleResult.STARTED
        }
    }
}
