package com.customrom.adb

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubIssueClientTest {
    @Test
    fun parsesOnlyCustomromIssuesAndExtractsAuthor() {
        val json = """
            [
              {
                "number": 41,
                "title": "[CUSTOMROM JOB] Diagnosticar memória",
                "body": "{\"schema\":\"customrom.adb.job.v1\"}",
                "html_url": "https://github.com/viluadmcontas2-dot/AgentRed/issues/41",
                "user": {"login": "viluadmcontas2-dot"}
              },
              {
                "number": 42,
                "title": "Assunto comum",
                "body": "ignore",
                "html_url": "https://github.com/viluadmcontas2-dot/AgentRed/issues/42",
                "user": {"login": "viluadmcontas2-dot"}
              }
            ]
        """.trimIndent()

        val issues = GitHubIssueClient.parseIssues(json, "[CUSTOMROM JOB]")

        assertEquals(1, issues.size)
        assertEquals(41L, issues.single().number)
        assertEquals("viluadmcontas2-dot", issues.single().authorLogin)
        assertTrue(issues.single().body.contains("customrom.adb.job.v1"))
    }

    @Test
    fun ignoresPullRequestsReturnedByIssuesApi() {
        val json = """
            [
              {
                "number": 50,
                "title": "[CUSTOMROM JOB] Isto é PR",
                "body": "payload",
                "html_url": "https://github.com/x/y/pull/50",
                "user": {"login": "viluadmcontas2-dot"},
                "pull_request": {"url": "https://api.github.com/repos/x/y/pulls/50"}
              }
            ]
        """.trimIndent()

        assertTrue(GitHubIssueClient.parseIssues(json, "[CUSTOMROM JOB]").isEmpty())
    }

    @Test
    fun ignoresJobWithoutBody() {
        val json = """
            [
              {
                "number": 51,
                "title": "[CUSTOMROM JOB] Sem payload",
                "body": null,
                "html_url": "https://github.com/x/y/issues/51",
                "user": {"login": "viluadmcontas2-dot"}
              }
            ]
        """.trimIndent()

        assertTrue(GitHubIssueClient.parseIssues(json, "[CUSTOMROM JOB]").isEmpty())
    }

    @Test
    fun titlePrefixMustBeExactAtStart() {
        val json = """
            [
              {
                "number": 52,
                "title": "prefixo [CUSTOMROM JOB] no meio",
                "body": "{}",
                "html_url": "https://github.com/x/y/issues/52",
                "user": {"login": "viluadmcontas2-dot"}
              }
            ]
        """.trimIndent()

        assertTrue(GitHubIssueClient.parseIssues(json, "[CUSTOMROM JOB]").isEmpty())
    }

    @Test
    fun defaultControlConfigIsPrivateAgentRedNamespace() {
        val config = GitHubControlConfig.defaults()

        assertEquals("viluadmcontas2-dot", config.owner)
        assertEquals("AgentRed", config.repo)
        assertEquals("[CUSTOMROM JOB]", config.titlePrefix)
        assertEquals("taytech-primary", config.target)
        assertEquals("viluadmcontas2-dot", config.allowedAuthor)
    }
    @Test
    fun defaultPollingIsNearRealtimeWithoutAggressiveBusyLoop() {
        assertEquals(5, GitHubControlConfig.defaults().pollSeconds)
    }

    @Test
    fun pollingFetchesNewestOpenIssuesFirstSoFreshJobsAreNotStarved() {
        var requestedUrl = ""
        val client = GitHubIssueClient(
            config = GitHubControlConfig.defaults(),
            tokenProvider = { "token-for-test" },
            connectionFactory = { url ->
                requestedUrl = url.toString()
                FakeConnection(200, "[]")
            }
        )

        client.listOpenJobs()

        assertTrue(requestedUrl.contains("sort=created"))
        assertTrue(requestedUrl.contains("direction=desc"))
    }

    @Test
    fun conditionalPollingReusesCachedBodyOn304AndSendsEtag() {
        val json = """[{"number":61,"title":"[CUSTOMROM JOB] console","body":"{}","html_url":"https://github.com/x/y/issues/61","user":{"login":"viluadmcontas2-dot"}}]"""
        val first = FakeConnection(200, json, mapOf("ETag" to "\"etag-1\""))
        val second = FakeConnection(304, "")
        val queue = ArrayDeque(listOf(first, second))
        val client = GitHubIssueClient(
            config = GitHubControlConfig.defaults(),
            tokenProvider = { "token-for-test" },
            connectionFactory = { queue.removeFirst() }
        )

        assertEquals(1, client.listOpenJobs().size)
        assertEquals(1, client.listOpenJobs().size)
        assertEquals("\"etag-1\"", second.getRequestProperty("If-None-Match"))
    }

    private class FakeConnection(
        private val status: Int,
        private val payload: String,
        private val responseHeaders: Map<String, String> = emptyMap()
    ) : HttpURLConnection(URL("https://api.github.test")) {
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
        override fun getResponseCode(): Int = status
        override fun getInputStream(): InputStream = ByteArrayInputStream(payload.toByteArray())
        override fun getErrorStream(): InputStream? = ByteArrayInputStream(payload.toByteArray())
        override fun getHeaderField(name: String?): String? = responseHeaders[name]
    }

}
